package com.lin.music.core.innertube.stream

import com.lin.music.core.innertube.InnerTubeApi
import com.lin.music.core.innertube.client.YouTubeClient
import com.lin.music.core.model.AudioQuality
import com.lin.music.core.model.SkipSegment
import com.lin.music.core.model.StreamInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo as NpStreamInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamResolver @Inject constructor(
    private val innerTubeApi: InnerTubeApi,
    private val okHttpClient: OkHttpClient
) {
    private val initMutex = Mutex()
    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<Pair<String, AudioQuality>, CompletableDeferred<StreamInfo>>()
    @Volatile
    private var newPipeInitialized = false

    private val json = Json { ignoreUnknownKeys = true }

    private val probeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun ensureNewPipeInitialized() {
        if (newPipeInitialized) return
        initMutex.withLock {
            if (!newPipeInitialized) {
                NewPipe.init(NewPipeDownloaderImpl(okHttpClient))
                newPipeInitialized = true
            }
        }
    }

    suspend fun warmUp() = withContext(Dispatchers.IO) {
        runCatching { innerTubeApi.fetchVisitorData(YouTubeClient.VISIONOS) }
    }

    suspend fun resolveStream(
        videoId: String,
        quality: AudioQuality = AudioQuality.HIGH
    ): StreamInfo {
        val key = videoId to quality
        while (true) {
            inFlight[key]?.let { return it.await() }
            val created = CompletableDeferred<StreamInfo>()
            if (inFlight.putIfAbsent(key, created) == null) {
                resolverScope.launch {
                    try {
                        created.complete(resolveStreamUncached(videoId, quality))
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

    private suspend fun resolveStreamUncached(
        videoId: String,
        quality: AudioQuality = AudioQuality.HIGH
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

        // 1. Primary: VISIONOS (101) with fresh visitorData — fast-path skips extra blocking probe
        val visionOsAttempt = runCatching {
            tryInnerTubeClient(YouTubeClient.VISIONOS, verifyReachable = false)
        }
        visionOsAttempt.getOrNull()?.let { return@withContext it }
        visionOsAttempt.exceptionOrNull()?.let { failureNotes += "VISIONOS: ${it.message}" }

        // 2. Secondary: NewPipeExtractor v0.27.0 (fast-path without duplicate 16KB pre-probe)
        val npAttempt = runCatching {
            ensureNewPipeInitialized()
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
        npAttempt.getOrNull()?.let { return@withContext it }
        npAttempt.exceptionOrNull()?.let { failureNotes += "NEWPIPE: ${it.message}" }

        // 3. Last-resort fallbacks strictly gated by open-range verification
        val fallbackClients = listOf(
            YouTubeClient.ANDROID_VR,
            YouTubeClient.WEB_REMIX,
            YouTubeClient.IOS
        )
        for (client in fallbackClients) {
            val attempt = runCatching { tryInnerTubeClient(client, verifyReachable = true) }
            attempt.getOrNull()?.let { return@withContext it }
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
    }
}
