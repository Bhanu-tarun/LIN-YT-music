package com.lin.music.core.playback.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import com.lin.music.core.model.Track
import com.lin.music.core.playback.di.DownloadCache
import com.lin.music.core.playback.di.PlaybackModule
import com.lin.music.core.playback.di.PrecacheDataSourceFactory
import com.lin.music.core.playback.di.StreamCache
import com.lin.music.core.playback.queue.PlaybackStateHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playback-first two-phase precacher backed by isolated [@StreamCache]:
 * Phase 1 caches the opening 512 KB of Track +1 and Track +2 first so skips start with
 * zero stutter (especially over VPN), then Phase 2 finishes full-file offline caching.
 */
@OptIn(UnstableApi::class)
@Singleton
class TrackPrecacher @Inject constructor(
    @StreamCache private val streamCache: Cache,
    @DownloadCache private val downloadCache: Cache,
    @PrecacheDataSourceFactory private val precacheDataSourceFactory: CacheDataSource.Factory,
    private val stateHub: PlaybackStateHub
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var previewJob: Job? = null

    @Volatile
    private var activeWriter: CacheWriter? = null

    @Volatile
    private var previewWriter: CacheWriter? = null

    @Volatile
    private var currentlyWritingVideoId: String? = null

    @Volatile
    private var currentlyPreviewingVideoId: String? = null

    @Volatile
    private var currentActiveVideoId: String? = null

    @Volatile
    private var playbackSettled: Boolean = false

    @Volatile
    private var lastRequestedIds: List<String> = emptyList()

    @Synchronized
    fun onPlaybackLoadingOrBuffering(activeVideoId: String?) {
        playbackSettled = false
        currentActiveVideoId = activeVideoId
        if (previewJob?.isActive == true || previewWriter != null) {
            cancelPreviewLocked()
        }
        val isTouchingActiveOrRunning =
            activeJob?.isActive == true ||
                activeWriter != null ||
                (activeVideoId != null && currentlyWritingVideoId == activeVideoId)
        if (isTouchingActiveOrRunning) {
            cancelActiveLocked()
            lastRequestedIds = emptyList()
        }
    }

    @Synchronized
    fun warmPreviewHead(videoId: String, isPlaybackBuffering: () -> Boolean) {
        if (videoId.isBlank() || videoId == currentActiveVideoId || isPlaybackBuffering()) return
        if (isRangeOrFullCached(videoId, PREVIEW_HEAD_BYTES)) return
        if (previewJob?.isActive == true && currentlyPreviewingVideoId == videoId) return

        cancelPreviewLocked()
        currentlyPreviewingVideoId = videoId
        previewJob = scope.launch {
            if (!isActive || isPlaybackBuffering() || videoId == currentActiveVideoId) return@launch
            cacheRange(
                videoId = videoId,
                lengthBytes = PREVIEW_HEAD_BYTES,
                isPreview = true,
                shouldContinue = { !isPlaybackBuffering() && videoId != currentActiveVideoId }
            )
            if (currentlyPreviewingVideoId == videoId) {
                currentlyPreviewingVideoId = null
            }
        }
    }

    @Synchronized
    fun precacheNextIfHealthy(
        activeVideoId: String?,
        upcoming: List<Track>,
        isPlaybackHealthy: () -> Boolean
    ) {
        currentActiveVideoId = activeVideoId
        val targets = selectTargets(upcoming, activeVideoId)
        if (targets.isEmpty()) {
            cancelActiveLocked()
            lastRequestedIds = emptyList()
            return
        }

        playbackSettled = true
        val ids = targets.map { it.videoId }
        val allFullyCached = ids.all { isRangeOrFullCached(it, C.LENGTH_UNSET.toLong()) }
        if (ids == lastRequestedIds && (activeJob?.isActive == true || allFullyCached)) {
            return
        }

        lastRequestedIds = ids
        cancelActiveLocked()

        val steps = buildPhasePlan(targets)
        activeJob = scope.launch {
            delay(SETTLE_DELAY_MS)
            for (step in steps) {
                val vid = step.track.videoId
                if (!isActive || !playbackSettled || !isPlaybackHealthy() || vid == currentActiveVideoId) {
                    resetRequestedIfMatch(ids)
                    break
                }
                if (isRangeOrFullCached(vid, step.lengthBytes)) continue
                val ok = cacheRange(
                    videoId = vid,
                    lengthBytes = step.lengthBytes,
                    isPreview = false,
                    shouldContinue = { playbackSettled && vid != currentActiveVideoId && isPlaybackHealthy() }
                )
                if (!ok && (!isActive || !playbackSettled || !isPlaybackHealthy())) {
                    resetRequestedIfMatch(ids)
                    break
                }
            }
        }
    }

    private suspend fun cacheRange(
        videoId: String,
        lengthBytes: Long,
        isPreview: Boolean,
        shouldContinue: () -> Boolean
    ): Boolean {
        val builder = DataSpec.Builder()
            .setUri(Uri.parse("${PlaybackModule.STREAM_URI_PREFIX}$videoId"))
            .setKey(videoId)
            .setPosition(0L)
        if (lengthBytes > 0L) {
            builder.setLength(lengthBytes)
        }
        val dataSpec = builder.build()
        val buffer = ByteArray(PRECACHE_BUFFER_BYTES)

        for (attempt in 0..MAX_PRIORITY_RETRIES) {
            if (!shouldContinue()) return false
            var writerRef: CacheWriter? = null
            val writer = CacheWriter(
                precacheDataSourceFactory.createDataSource(),
                dataSpec,
                buffer
            ) { _, _, _ ->
                if (!shouldContinue()) {
                    writerRef?.cancel()
                }
            }
            writerRef = writer
            if (isPreview) {
                previewWriter = writer
                currentlyPreviewingVideoId = videoId
            } else {
                activeWriter = writer
                currentlyWritingVideoId = videoId
            }
            try {
                writer.cache()
                return true
            } catch (c: CancellationException) {
                throw c
            } catch (_: PriorityTaskManager.PriorityTooLowException) {
                if (attempt == MAX_PRIORITY_RETRIES || !shouldContinue()) {
                    return false
                }
                delay(PRIORITY_BACKOFF_MS)
            } catch (_: Throwable) {
                return false
            } finally {
                if (isPreview) {
                    if (previewWriter === writer) previewWriter = null
                } else {
                    if (activeWriter === writer) {
                        activeWriter = null
                        currentlyWritingVideoId = null
                    }
                }
            }
        }
        return false
    }

    @Synchronized
    private fun cancelActiveLocked() {
        activeWriter?.cancel()
        activeWriter = null
        currentlyWritingVideoId = null
        activeJob?.cancel()
        activeJob = null
    }

    @Synchronized
    private fun cancelPreviewLocked() {
        previewWriter?.cancel()
        previewWriter = null
        currentlyPreviewingVideoId = null
        previewJob?.cancel()
        previewJob = null
    }

    @Synchronized
    private fun resetRequestedIfMatch(ids: List<String>) {
        if (lastRequestedIds == ids) {
            lastRequestedIds = emptyList()
        }
    }

    private fun isRangeOrFullCached(videoId: String, desiredBytes: Long): Boolean {
        val streamKey = PlaybackModule.streamCacheKey(videoId, stateHub.currentQuality)
        return isCachedInStore(downloadCache, videoId, desiredBytes) ||
            isCachedInStore(streamCache, streamKey, desiredBytes)
    }

    suspend fun evictTrackStreamCache(videoId: String) = withContext(Dispatchers.IO) {
        synchronized(this@TrackPrecacher) {
            if (currentlyWritingVideoId == videoId || videoId in lastRequestedIds) {
                cancelActiveLocked()
                lastRequestedIds = emptyList()
            }
            if (currentlyPreviewingVideoId == videoId) {
                cancelPreviewLocked()
            }
        }
        streamCache.keys.toList().forEach { key ->
            if (PlaybackModule.isStreamCacheKeyForVideo(key, videoId)) {
                runCatching { streamCache.removeResource(key) }
            }
        }
    }

    suspend fun pruneLegacyUnkeyedStreamCache() = withContext(Dispatchers.IO) {
        streamCache.keys.toList().forEach { key ->
            if (':' !in key) {
                runCatching { streamCache.removeResource(key) }
            }
        }
    }

    private fun isCachedInStore(store: Cache, videoId: String, desiredBytes: Long): Boolean {
        val contentLength = ContentMetadata.getContentLength(store.getContentMetadata(videoId))
        val checkLen = if (contentLength != C.LENGTH_UNSET.toLong() && contentLength > 0L) {
            if (desiredBytes == C.LENGTH_UNSET.toLong()) contentLength else minOf(contentLength, desiredBytes)
        } else {
            if (desiredBytes == C.LENGTH_UNSET.toLong()) return false else desiredBytes
        }
        return checkLen > 0L && store.isCached(videoId, 0L, checkLen)
    }

    suspend fun clearNonDownloadedStreamCache(): Long = withContext(Dispatchers.IO) {
        synchronized(this@TrackPrecacher) {
            cancelActiveLocked()
            cancelPreviewLocked()
            lastRequestedIds = emptyList()
        }
        val before = streamCache.cacheSpace
        streamCache.keys.toList().forEach { key ->
            runCatching { streamCache.removeResource(key) }
        }
        (before - streamCache.cacheSpace).coerceAtLeast(0L)
    }

    fun cacheSizeBytes(): Long = streamCache.cacheSpace

    fun downloadSizeBytes(): Long = downloadCache.cacheSpace

    data class PrecacheStep(
        val track: Track,
        val lengthBytes: Long
    )

    companion object {
        const val HEAD_PRECACHE_BYTES = 512L * 1024L
        const val PREVIEW_HEAD_BYTES = 256L * 1024L
        private const val SETTLE_DELAY_MS = 50L
        private const val PRIORITY_BACKOFF_MS = 1_500L
        private const val MAX_PRIORITY_RETRIES = 2
        private const val PRECACHE_BUFFER_BYTES = 16 * 1024

        fun selectTargets(upcoming: List<Track>, activeVideoId: String?): List<Track> =
            upcoming
                .asSequence()
                .filter { it.videoId.isNotBlank() && it.videoId != activeVideoId }
                .distinctBy { it.videoId }
                .take(2)
                .toList()

        fun buildPhasePlan(targets: List<Track>): List<PrecacheStep> {
            if (targets.isEmpty()) return emptyList()
            val headPhase = targets.map { PrecacheStep(it, HEAD_PRECACHE_BYTES) }
            val fullPhase = targets.map { PrecacheStep(it, C.LENGTH_UNSET.toLong()) }
            return headPhase + fullPhase
        }
    }
}
