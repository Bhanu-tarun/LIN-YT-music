package com.lin.music.core.innertube.stream

import com.lin.music.core.innertube.InnerTubeApi
import com.lin.music.core.innertube.client.YouTubeClient
import com.lin.music.core.model.AudioQuality
import com.lin.music.core.model.ContentRegions
import com.lin.music.core.model.SkipSegment
import com.lin.music.core.model.StreamInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo as NpStreamInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

internal class VisionOsCooldownTracker(
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 10L * 60_000L
) {
    private var trackedRegion: String? = null
    private var consecutiveFailures = 0
    private var skipUntilMs = 0L

    @Synchronized
    fun shouldSkipVisionOs(regionCode: String, nowMs: Long): Boolean {
        if (trackedRegion != regionCode) {
            resetLocked(regionCode)
            return false
        }
        return nowMs < skipUntilMs
    }

    @Synchronized
    fun recordVisionOsSuccess(regionCode: String) {
        resetLocked(regionCode)
    }

    @Synchronized
    fun recordVisionOsFailureWithFallbackSuccess(regionCode: String, nowMs: Long) {
        if (trackedRegion != regionCode) {
            resetLocked(regionCode)
        }
        consecutiveFailures++
        if (consecutiveFailures >= failureThreshold) {
            skipUntilMs = nowMs + cooldownMs
        }
    }

    @Synchronized
    fun reset() {
        resetLocked(null)
    }

    private fun resetLocked(nextRegion: String?) {
        trackedRegion = nextRegion
        consecutiveFailures = 0
        skipUntilMs = 0L
    }
}

@Singleton
class StreamResolver @Inject constructor(
    private val innerTubeApi: InnerTubeApi,
    private val okHttpClient: OkHttpClient
) {
    private data class StreamCacheKey(
        val videoId: String,
        val quality: AudioQuality,
        val regionCode: String
    )

    private val initMutex = Mutex()
    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<StreamCacheKey, CompletableDeferred<StreamInfo>>()
    private val resolvedCache = ConcurrentHashMap<StreamCacheKey, StreamInfo>()
    private val prefetchSemaphore = Semaphore(MAX_PREFETCH_CONCURRENCY)
    private val visionOsCooldown = VisionOsCooldownTracker()
    private val lastVisitorForceRefreshMs = AtomicLong(0L)
    private val cacheGeneration = AtomicLong(0L)
    @Volatile
    private var newPipeInitialized = false
    @Volatile
    private var newPipeRegionCode: String? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val probeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun ensureNewPipeInitialized(regionCode: String) {
        val normalized = ContentRegions.normalize(regionCode)
        if (newPipeInitialized && newPipeRegionCode == normalized) return
        initMutex.withLock {
            val localization = Localization("en", normalized)
            val country = ContentCountry(normalized)
            if (!newPipeInitialized) {
                NewPipe.init(NewPipeDownloaderImpl(okHttpClient), localization, country)
                newPipeInitialized = true
                newPipeRegionCode = normalized
            } else if (newPipeRegionCode != normalized) {
                NewPipe.setupLocalization(localization, country)
                newPipeRegionCode = normalized
            }
        }
    }

    suspend fun warmUp() = withContext(Dispatchers.IO) {
        runCatching { innerTubeApi.fetchVisitorData(YouTubeClient.VISIONOS) }
    }

    fun invalidateStream(videoId: String, clearVisitorData: Boolean = false) {
        cacheGeneration.incrementAndGet()
        if (videoId.isNotBlank()) {
            resolvedCache.keys.forEach { key ->
                if (key.videoId == videoId) {
                    resolvedCache.remove(key)
                }
            }
        }
        if (clearVisitorData) {
            visionOsCooldown.reset()
            innerTubeApi.clearVisitorDataCache()
        }
    }

    fun invalidateAllStreams() {
        cacheGeneration.incrementAndGet()
        resolvedCache.clear()
        visionOsCooldown.reset()
        innerTubeApi.clearVisitorDataCache()
    }

    fun prefetchForDownload(videoIds: List<String>, quality: AudioQuality) {
        val targets = videoIds.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (targets.isEmpty()) return
        resolverScope.launch {
            val region = runCatching { innerTubeApi.currentRegionCode() }
                .getOrDefault(ContentRegions.DEFAULT_CODE)
            val now = System.currentTimeMillis()
            targets.forEach { videoId ->
                val key = StreamCacheKey(videoId, quality, region)
                val cached = resolvedCache[key]
                val validCached = cached != null && cached.expiresAtEpochMs > now + CACHE_EXPIRY_MARGIN_MS
                if (!validCached && !inFlight.containsKey(key)) {
                    launch {
                        prefetchSemaphore.withPermit {
                            runCatching { resolveStream(videoId, quality) }
                        }
                    }
                }
            }
        }
    }

    suspend fun resolveStream(
        videoId: String,
        quality: AudioQuality = AudioQuality.HIGH
    ): StreamInfo {
        val region = innerTubeApi.currentRegionCode()
        val key = StreamCacheKey(videoId, quality, region)
        val now = System.currentTimeMillis()
        resolvedCache[key]?.let { cached ->
            if (cached.expiresAtEpochMs > now + CACHE_EXPIRY_MARGIN_MS) {
                return cached
            }
            resolvedCache.remove(key, cached)
        }

        while (true) {
            inFlight[key]?.let { return it.await() }
            val created = CompletableDeferred<StreamInfo>()
            if (inFlight.putIfAbsent(key, created) == null) {
                val startGen = cacheGeneration.get()
                resolverScope.launch {
                    try {
                        val resolved = resolveStreamUncached(videoId, quality, region)
                        if (cacheGeneration.get() == startGen) {
                            resolvedCache[key] = resolved
                            trimResolvedCacheIfNeeded()
                        }
                        created.complete(resolved)
                    } catch (t: Throwable) {
                        created.completeExceptionally(t)
                    } finally {
                        inFlight.remove(key, created)
                    }
                }
                return created.await()
            }
        }
    }

    private fun trimResolvedCacheIfNeeded() {
        if (resolvedCache.size <= MAX_RESOLVED_CACHE_SIZE) return
        val cutoff = System.currentTimeMillis() + CACHE_EXPIRY_MARGIN_MS
        resolvedCache.entries.removeIf { it.value.expiresAtEpochMs <= cutoff }
        if (resolvedCache.size <= MAX_RESOLVED_CACHE_SIZE) return
        val overflow = resolvedCache.size - MAX_RESOLVED_CACHE_SIZE
        resolvedCache.entries
            .sortedBy { it.value.expiresAtEpochMs }
            .take(overflow)
            .forEach { resolvedCache.remove(it.key, it.value) }
    }

    private suspend fun resolveStreamUncached(
        videoId: String,
        quality: AudioQuality = AudioQuality.HIGH,
        regionCode: String
    ): StreamInfo = withContext(Dispatchers.IO) {
        var loudnessDb: Float? = null
        val failureNotes = mutableListOf<String>()

        suspend fun tryInnerTubeClient(
            client: YouTubeClient,
            verifyReachable: Boolean = true,
            forceRefreshVisitor: Boolean = false
        ): StreamInfo {
            val visitorData = if (!client.supportsAuthCookies) {
                innerTubeApi.fetchVisitorData(client, forceRefresh = forceRefreshVisitor)
            } else null

            var root = innerTubeApi.getPlayerRaw(
                videoId = videoId,
                client = client,
                visitorDataOverride = visitorData
            )
            var playability = extractPlayabilityStatus(root)
            if (!client.supportsAuthCookies && !forceRefreshVisitor &&
                (playability == "LOGIN_REQUIRED" || playability == "UNPLAYABLE")
            ) {
                val now = System.currentTimeMillis()
                val prevRefresh = lastVisitorForceRefreshMs.get()
                val canForceRefresh = (now - prevRefresh) >= VISITOR_FORCE_REFRESH_COOLDOWN_MS &&
                    lastVisitorForceRefreshMs.compareAndSet(prevRefresh, now)
                if (canForceRefresh) {
                    val refreshedVd = innerTubeApi.fetchVisitorData(client, forceRefresh = true)
                    if (!refreshedVd.isNullOrBlank() && refreshedVd != visitorData) {
                        root = innerTubeApi.getPlayerRaw(
                            videoId = videoId,
                            client = client,
                            visitorDataOverride = refreshedVd
                        )
                        playability = extractPlayabilityStatus(root)
                    }
                }
            }

            if (loudnessDb == null) {
                loudnessDb = extractLoudnessDb(root)
            }
            val stream = extractBestDirectFormat(videoId, root, quality, client, loudnessDb)
                ?: error("no direct audio format (playability=${extractPlayability(root)})")
            if (verifyReachable && !verifyStreamReachable(stream.url, stream.userAgent)) {
                error("open-range probe failed for ${client.name}")
            }
            return stream
        }

        // 1. Primary: VISIONOS (101) — skip temporarily if repeatedly rejected in this region.
        var visionOsFailed = false
        if (!visionOsCooldown.shouldSkipVisionOs(regionCode, System.currentTimeMillis())) {
            val visionOsAttempt = runCatching {
                tryInnerTubeClient(YouTubeClient.VISIONOS, verifyReachable = false)
            }
            visionOsAttempt.getOrNull()?.let { stream ->
                visionOsCooldown.recordVisionOsSuccess(regionCode)
                return@withContext stream
            }
            visionOsFailed = true
            visionOsAttempt.exceptionOrNull()?.let { failureNotes += "VISIONOS: ${it.message}" }
        } else {
            failureNotes += "VISIONOS: cooldown"
        }

        // 2. Secondary: NewPipeExtractor v0.27.0 (fast-path without duplicate 16KB pre-probe)
        val npAttempt = runCatching {
            ensureNewPipeInitialized(regionCode)
            val npInfo = NpStreamInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$videoId"
            )
            val progressive = npInfo.audioStreams.filter {
                it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrBlank()
            }
            val pool = progressive.ifEmpty {
                npInfo.audioStreams.filter { !it.content.isNullOrBlank() }
            }
            val bestAudio = selectBestNewPipeAudio(pool, quality)
                ?: error("no NewPipe audio streams")

            val streamUrl = bestAudio.content
            val mime = bestAudio.format?.mimeType ?: "audio/mp4"
            val kbps = bestAudio.averageBitrate.coerceAtLeast(32)
            val codecLabel = when {
                mime.contains("opus", true) || mime.contains("webm", true) -> "OPUS ${kbps}k"
                else -> "AAC ${kbps}k"
            }

            StreamInfo(
                videoId = videoId,
                url = streamUrl,
                mimeType = mime,
                bitrate = kbps * 1000,
                loudnessDb = loudnessDb,
                expiresAtEpochMs = System.currentTimeMillis() + 5 * 3600_000L,
                codecLabel = codecLabel,
                clientSource = "NEWPIPE",
                userAgent = NEWPIPE_USER_AGENT
            )
        }
        npAttempt.getOrNull()?.let { stream ->
            if (visionOsFailed) {
                visionOsCooldown.recordVisionOsFailureWithFallbackSuccess(regionCode, System.currentTimeMillis())
            }
            return@withContext stream
        }
        npAttempt.exceptionOrNull()?.let { failureNotes += "NEWPIPE: ${it.message}" }

        // 3. Last-resort fallbacks strictly gated by open-range verification
        val fallbackClients = listOf(
            YouTubeClient.ANDROID_VR,
            YouTubeClient.WEB_REMIX,
            YouTubeClient.IOS
        )
        for (client in fallbackClients) {
            val attempt = runCatching { tryInnerTubeClient(client, verifyReachable = true) }
            attempt.getOrNull()?.let { stream ->
                if (visionOsFailed) {
                    visionOsCooldown.recordVisionOsFailureWithFallbackSuccess(regionCode, System.currentTimeMillis())
                }
                return@withContext stream
            }
            attempt.exceptionOrNull()?.let { failureNotes += "${client.name}: ${it.message}" }
        }

        throw IllegalStateException("Stream resolution failed for $videoId (${failureNotes.joinToString("; ")})")
    }

    private fun verifyStreamReachable(url: String, userAgent: String): Boolean {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-")
                .apply {
                    if (userAgent.isNotBlank()) header("User-Agent", userAgent)
                }
                .get()
                .build()
            probeClient.newCall(req).execute().use { resp ->
                if (resp.code != 200 && resp.code != 206) return@use false
                val stream = resp.body?.byteStream() ?: return@use false
                val buf = ByteArray(4096)
                var total = 0
                while (total < 16_384) {
                    val read = stream.read(buf, 0, minOf(buf.size, 16_384 - total))
                    if (read <= 0) break
                    total += read
                }
                total > 0
            }
        }.getOrDefault(false)
    }

    private fun extractPlayabilityStatus(root: JsonObject): String {
        val ps = root["playabilityStatus"] as? JsonObject ?: return "MISSING"
        return (ps["status"] as? JsonPrimitive)?.contentOrNull ?: "UNKNOWN"
    }

    private fun extractPlayability(root: JsonObject): String {
        val ps = root["playabilityStatus"] as? JsonObject ?: return "MISSING"
        val status = (ps["status"] as? JsonPrimitive)?.contentOrNull ?: "UNKNOWN"
        val reason = (ps["reason"] as? JsonPrimitive)?.contentOrNull
        return if (reason != null) "$status($reason)" else status
    }

    private fun extractLoudnessDb(root: JsonObject): Float? {
        return (root["playerConfig"] as? JsonObject)
            ?.get("audioConfig")?.let { it as? JsonObject }
            ?.get("loudnessDb")?.let { (it as? JsonPrimitive)?.floatOrNull }
    }

    private fun extractBestDirectFormat(
        videoId: String,
        root: JsonObject,
        quality: AudioQuality,
        client: YouTubeClient,
        loudnessDb: Float?
    ): StreamInfo? {
        val playability = (root["playabilityStatus"] as? JsonObject)
            ?.get("status")?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (playability != "OK") return null

        val streamingData = root["streamingData"] as? JsonObject ?: return null
        val adaptive = (streamingData["adaptiveFormats"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { fmt ->
                val mime = (fmt["mimeType"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val url = (fmt["url"] as? JsonPrimitive)?.contentOrNull
                mime.startsWith("audio/") && !url.isNullOrBlank()
            }

        if (adaptive.isEmpty()) return null

        val chosen = when (quality) {
            AudioQuality.LOW -> adaptive.minByOrNull { (it["bitrate"] as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE }
            AudioQuality.NORMAL -> adaptive.sortedBy { (it["bitrate"] as? JsonPrimitive)?.intOrNull ?: 0 }
                .let { it[it.size / 2] }
            AudioQuality.HIGH, AudioQuality.AUTO -> adaptive.maxByOrNull {
                val itag = (it["itag"] as? JsonPrimitive)?.intOrNull ?: 0
                val br = (it["bitrate"] as? JsonPrimitive)?.intOrNull ?: 0
                val bonus = when (itag) {
                    141 -> 500_000
                    251 -> 100_000
                    140 -> 50_000
                    else -> 0
                }
                br + bonus
            }
        } ?: return null

        val url = (chosen["url"] as JsonPrimitive).content
        val mime = (chosen["mimeType"] as? JsonPrimitive)?.contentOrNull ?: "audio/mp4"
        val bitrate = (chosen["bitrate"] as? JsonPrimitive)?.intOrNull ?: 0
        val kbps = (bitrate / 1000).coerceAtLeast(32)
        val codecLabel = when {
            mime.contains("opus", true) || mime.contains("webm", true) -> "OPUS ${kbps}k"
            else -> "AAC ${kbps}k"
        }
        val expiresSec = (streamingData["expiresInSeconds"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 21600L

        return StreamInfo(
            videoId = videoId,
            url = url,
            mimeType = mime,
            bitrate = bitrate,
            loudnessDb = loudnessDb,
            expiresAtEpochMs = System.currentTimeMillis() + (expiresSec - 300L).coerceAtLeast(60L) * 1000L,
            codecLabel = codecLabel,
            clientSource = client.name,
            userAgent = client.userAgent
        )
    }

    private fun selectBestNewPipeAudio(streams: List<AudioStream>, quality: AudioQuality): AudioStream? {
        if (streams.isEmpty()) return null
        return when (quality) {
            AudioQuality.LOW -> streams.minByOrNull { it.averageBitrate }
            AudioQuality.NORMAL -> streams.sortedBy { it.averageBitrate }.let { it[it.size / 2] }
            AudioQuality.HIGH, AudioQuality.AUTO -> streams.maxByOrNull { it.averageBitrate }
        }
    }

    suspend fun fetchSponsorBlockSegments(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&category=music_offtopic")
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val body = resp.body?.string() ?: return@runCatching emptyList()
                val array = json.parseToJsonElement(body).jsonArray
                array.mapNotNull { elem ->
                    val obj = elem.jsonObject
                    val cat = obj["category"]?.jsonPrimitive?.contentOrNull ?: "music_offtopic"
                    val seg = obj["segment"]?.jsonArray ?: return@mapNotNull null
                    val startMs = ((seg.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null) * 1000f).toLong()
                    val endMs = ((seg.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null) * 1000f).toLong()
                    SkipSegment(startMs = startMs, endMs = endMs, category = cat)
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val NEWPIPE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"
        private const val CACHE_EXPIRY_MARGIN_MS = 60_000L
        private const val MAX_RESOLVED_CACHE_SIZE = 256
        private const val MAX_PREFETCH_CONCURRENCY = 4
        private const val VISITOR_FORCE_REFRESH_COOLDOWN_MS = 30_000L
    }
}
