package com.lin.music.core.playback.queue

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.toEntity
import com.lin.music.core.data.repository.MusicRepository
import com.lin.music.core.innertube.parser.InnerTubeParsers
import com.lin.music.core.innertube.stream.StreamResolver
import com.lin.music.core.model.AudioEnergy
import com.lin.music.core.model.MixChip
import com.lin.music.core.model.RepeatMode
import com.lin.music.core.model.SleepTimerState
import com.lin.music.core.model.StreamInfo
import com.lin.music.core.model.Track
import com.lin.music.core.playback.cache.TrackPrecacher
import com.lin.music.core.playback.service.LinPlaybackService
import com.lin.music.core.playback.service.LinPlaybackService.Companion.toMediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateHub: PlaybackStateHub,
    private val musicRepository: MusicRepository,
    private val streamResolver: StreamResolver,
    private val cacheDao: CacheDao,
    private val trackPrecacher: TrackPrecacher
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val queueSnapshot: StateFlow<QueueSnapshot> = stateHub.queueSnapshot
    val isPlaying: StateFlow<Boolean> = stateHub.isPlaying
    val isBuffering: StateFlow<Boolean> = stateHub.isBuffering
    val positionMs: StateFlow<Long> = stateHub.positionMs
    val durationMs: StateFlow<Long> = stateHub.durationMs
    val audioEnergy: StateFlow<AudioEnergy> = stateHub.reactiveAudioProcessor.energyFlow
    val activeStreamInfo: StateFlow<StreamInfo?> = stateHub.activeStreamInfo
    val mixChips: StateFlow<List<MixChip>> = stateHub.mixChips
    val sleepTimerState: StateFlow<SleepTimerState> = stateHub.sleepTimerState
    val audioSessionId: StateFlow<Int> = stateHub.audioSessionId
    val isUiForeground: StateFlow<Boolean> = stateHub.isUiForeground
    val errors = stateHub.errors

    fun setUiForeground(foreground: Boolean) {
        stateHub.setUiForeground(foreground)
    }

    fun setLyricsActive(active: Boolean) {
        stateHub.setLyricsActive(active)
    }

    @Volatile
    private var controller: MediaController? = null

    init {
        scope.launch { ensureController() }
    }

    private suspend fun ensureController(): MediaController {
        controller?.let { if (it.isConnected) return it }
        val token = SessionToken(context, ComponentName(context, LinPlaybackService::class.java))
        val created = MediaController.Builder(context, token).buildAsync().await()
        controller = created
        return created
    }

    fun playContext(tracks: List<Track>, startIndex: Int, sourceLabel: String) {
        if (tracks.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, tracks.lastIndex)
        stateHub.autoMixContinuation = null
        stateHub.autoMixPlaylistId = null
        stateHub.updateMixChips(emptyList())
        val snap = stateHub.queueEngine.replaceWithContext(tracks, safeIdx, sourceLabel)
        stateHub.publishQueue(snap)
        trackPrecacher.onPlaybackLoadingOrBuffering(tracks[safeIdx].videoId)

        scope.launch {
            val ctrl = ensureController()
            ctrl.setMediaItems(tracks.map { it.toMediaItem() }, safeIdx, 0L)
            ctrl.prepare()
            ctrl.play()
        }
    }

    fun prefetchTrack(track: Track) {
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = cacheDao.getStream(track.videoId)?.toModel()
            if (cached == null ||
                cached.userAgent.isBlank() ||
                cached.clientSource == "IOS" ||
                cached.clientSource == "ANDROID_VR" ||
                cached.expiresAtEpochMs <= now + 60_000L
            ) {
                runCatching {
                    val fresh = streamResolver.resolveStream(track.videoId, stateHub.currentQuality)
                    cacheDao.putStream(fresh.toEntity())
                }
            }
            trackPrecacher.warmPreviewHead(track.videoId) { stateHub.isBuffering.value }
        }
    }

    fun shuffleContext(tracks: List<Track>, sourceLabel: String) {
        if (tracks.isEmpty()) return
        playContext(tracks.shuffled(), 0, "$sourceLabel · shuffle")
    }

    /**
     * Non-negotiable Right-Swipe & menu action: inserts track immediately after the active track.
     */
    fun playNext(track: Track): QueueUndoToken {
        val wasEmpty = stateHub.queueEngine.snapshot.items.isEmpty()
        val (snap, undo) = stateHub.queueEngine.playNext(track)
        stateHub.publishQueue(snap)
        if (wasEmpty) {
            trackPrecacher.onPlaybackLoadingOrBuffering(track.videoId)
        }

        scope.launch {
            val ctrl = ensureController()
            if (wasEmpty || ctrl.mediaItemCount == 0) {
                ctrl.setMediaItem(track.toMediaItem())
                ctrl.prepare()
                ctrl.play()
            } else {
                val insertAt = (ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount)
                ctrl.addMediaItem(insertAt, track.toMediaItem())
            }
        }
        return undo
    }

    fun playNextAll(tracks: List<Track>, label: String): QueueUndoToken {
        val wasEmpty = stateHub.queueEngine.snapshot.items.isEmpty()
        val (snap, undo) = stateHub.queueEngine.playNextAll(tracks, label)
        stateHub.publishQueue(snap)

        if (tracks.isNotEmpty()) {
            scope.launch {
                val ctrl = ensureController()
                if (wasEmpty || ctrl.mediaItemCount == 0) {
                    ctrl.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0L)
                    ctrl.prepare()
                    ctrl.play()
                } else {
                    val insertAt = (ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount)
                    ctrl.addMediaItems(insertAt, tracks.map { it.toMediaItem() })
                }
            }
        }
        return undo
    }

    /**
     * Non-negotiable Left-Swipe & menu action: appends track to end of queue.
     */
    fun addToQueue(track: Track): QueueUndoToken {
        val wasEmpty = stateHub.queueEngine.snapshot.items.isEmpty()
        val (snap, undo) = stateHub.queueEngine.addToQueue(track)
        stateHub.publishQueue(snap)
        if (wasEmpty) {
            trackPrecacher.onPlaybackLoadingOrBuffering(track.videoId)
        }

        scope.launch {
            val ctrl = ensureController()
            if (wasEmpty || ctrl.mediaItemCount == 0) {
                ctrl.setMediaItem(track.toMediaItem())
                ctrl.prepare()
                ctrl.play()
            } else {
                ctrl.addMediaItem(track.toMediaItem())
            }
        }
        return undo
    }

    fun addAllToQueue(tracks: List<Track>, label: String): QueueUndoToken {
        val wasEmpty = stateHub.queueEngine.snapshot.items.isEmpty()
        val (snap, undo) = stateHub.queueEngine.addAllToQueue(tracks, label)
        stateHub.publishQueue(snap)

        if (tracks.isNotEmpty()) {
            scope.launch {
                val ctrl = ensureController()
                if (wasEmpty || ctrl.mediaItemCount == 0) {
                    ctrl.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0L)
                    ctrl.prepare()
                    ctrl.play()
                } else {
                    ctrl.addMediaItems(tracks.map { it.toMediaItem() })
                }
            }
        }
        return undo
    }

    fun undoQueueChange(token: QueueUndoToken) {
        val snap = stateHub.queueEngine.restore(token)
        stateHub.publishQueue(snap)

        scope.launch {
            val ctrl = ensureController()
            val curPos = ctrl.currentPosition.coerceAtLeast(0L)
            if (snap.items.isEmpty()) {
                ctrl.clearMediaItems()
            } else {
                val idx = snap.currentIndex.coerceIn(0, snap.items.lastIndex)
                ctrl.setMediaItems(snap.items.map { it.toMediaItem() }, idx, curPos)
                ctrl.prepare()
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val snap = stateHub.queueEngine.move(fromIndex, toIndex)
        stateHub.publishQueue(snap)
        scope.launch {
            val ctrl = ensureController()
            if (fromIndex in 0 until ctrl.mediaItemCount && toIndex in 0 until ctrl.mediaItemCount) {
                ctrl.moveMediaItem(fromIndex, toIndex)
            }
        }
    }

    fun removeQueueItem(index: Int): QueueUndoToken? {
        val (snap, undo) = stateHub.queueEngine.removeAt(index)
        stateHub.publishQueue(snap)
        scope.launch {
            val ctrl = ensureController()
            if (index in 0 until ctrl.mediaItemCount) {
                ctrl.removeMediaItem(index)
            }
        }
        return undo
    }

    fun clearUpcomingQueue(): QueueUndoToken {
        val (snap, undo) = stateHub.queueEngine.clearUpcoming()
        stateHub.publishQueue(snap)
        scope.launch {
            val ctrl = ensureController()
            val cur = ctrl.currentMediaItemIndex
            if (cur in 0 until ctrl.mediaItemCount - 1) {
                ctrl.removeMediaItems(cur + 1, ctrl.mediaItemCount)
            }
        }
        return undo
    }

    fun jumpToQueueIndex(index: Int) {
        val snap = stateHub.queueEngine.setCurrentIndex(index)
        stateHub.publishQueue(snap)
        trackPrecacher.onPlaybackLoadingOrBuffering(snap.currentTrack?.videoId)
        scope.launch {
            val ctrl = ensureController()
            if (index in 0 until ctrl.mediaItemCount) {
                if (ctrl.playbackState == Player.STATE_IDLE) {
                    ctrl.prepare()
                }
                ctrl.seekToDefaultPosition(index)
                ctrl.play()
            }
        }
    }

    fun startMix(
        seedVideoId: String? = null,
        playlistId: String? = null,
        params: String? = null,
        sourceLabel: String = "mix"
    ) {
        scope.launch {
            runCatching {
                val nextPage = musicRepository.getNext(
                    videoId = seedVideoId,
                    playlistId = playlistId ?: seedVideoId?.let { "RDAMVM$it" },
                    params = params
                )
                if (nextPage.tracks.isEmpty()) {
                    stateHub.emitError("Could not start mix")
                    return@launch
                }
                val mixTracks = InnerTubeParsers.deduplicateMixTracks(
                    nextPage.tracks,
                    allowSeedVideoId = seedVideoId
                ).ifEmpty { nextPage.tracks }
                stateHub.autoMixPlaylistId = nextPage.playlistId ?: playlistId
                stateHub.autoMixContinuation = nextPage.continuation
                stateHub.updateMixChips(nextPage.mixChips)
                val snap = stateHub.queueEngine.replaceWithContext(mixTracks, 0, sourceLabel)
                stateHub.publishQueue(snap)
                trackPrecacher.onPlaybackLoadingOrBuffering(mixTracks.firstOrNull()?.videoId)
                val ctrl = ensureController()
                ctrl.setMediaItems(mixTracks.map { it.toMediaItem() }, 0, 0L)
                ctrl.prepare()
                ctrl.play()
            }.onFailure {
                stateHub.emitError(it.message ?: "Failed to start mix")
            }
        }
    }

    fun selectMixChip(chip: MixChip) {
        val currentId = stateHub.queueEngine.snapshot.currentTrack?.videoId
        startMix(
            seedVideoId = currentId,
            playlistId = chip.playlistId,
            params = chip.params,
            sourceLabel = "mix · ${chip.title.lowercase()}"
        )
    }

    fun togglePlayPause() {
        scope.launch {
            val ctrl = ensureController()
            if (ctrl.isPlaying) {
                ctrl.pause()
            } else {
                if (ctrl.playbackState == Player.STATE_IDLE && ctrl.mediaItemCount > 0) {
                    ctrl.prepare()
                }
                ctrl.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        stateHub.updatePosition(positionMs, stateHub.durationMs.value)
        scope.launch { ensureController().seekTo(positionMs.coerceAtLeast(0L)) }
    }

    fun skipNext() {
        val curSnap = stateHub.queueEngine.snapshot
        trackPrecacher.onPlaybackLoadingOrBuffering(curSnap.items.getOrNull(curSnap.currentIndex + 1)?.videoId)
        scope.launch {
            val ctrl = ensureController()
            if (ctrl.playbackState == Player.STATE_IDLE && ctrl.mediaItemCount > 0) {
                ctrl.prepare()
            }
            if (ctrl.hasNextMediaItem()) ctrl.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val curSnap = stateHub.queueEngine.snapshot
        trackPrecacher.onPlaybackLoadingOrBuffering(curSnap.currentTrack?.videoId)
        scope.launch {
            val ctrl = ensureController()
            if (ctrl.playbackState == Player.STATE_IDLE && ctrl.mediaItemCount > 0) {
                ctrl.prepare()
            }
            if (ctrl.currentPosition > 3500L || !ctrl.hasPreviousMediaItem()) {
                ctrl.seekTo(0L)
            } else {
                ctrl.seekToPreviousMediaItem()
            }
        }
    }

    fun toggleShuffle() {
        val next = !stateHub.queueEngine.snapshot.shuffleEnabled
        stateHub.publishQueue(stateHub.queueEngine.setShuffleEnabled(next))
        scope.launch { ensureController().shuffleModeEnabled = next }
    }

    fun cycleRepeatMode() {
        val next = when (stateHub.queueEngine.snapshot.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        stateHub.publishQueue(stateHub.queueEngine.setRepeatMode(next))
        scope.launch {
            ensureController().repeatMode = when (next) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
        }
    }

    fun updateTrackMetadata(track: Track) {
        stateHub.updateTrackMetadata(track)
    }

    // --- Sleep Timer Controls ---

    fun startSleepCountdown(durationMs: Long, fadeOut: Boolean = true) {
        stateHub.serviceTimerBridge?.startCountdown(durationMs, fadeOut)
    }

    fun startSleepEndOfTrack(fadeOut: Boolean = false) {
        stateHub.serviceTimerBridge?.startEndOfTrack(fadeOut)
    }

    fun extendSleepTimer(extraMs: Long = 5 * 60_000L) {
        stateHub.serviceTimerBridge?.extend(extraMs)
    }

    fun cancelSleepTimer() {
        stateHub.serviceTimerBridge?.cancel()
    }
}
