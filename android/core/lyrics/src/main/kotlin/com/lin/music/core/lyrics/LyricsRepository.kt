package com.lin.music.core.lyrics

import com.lin.music.core.innertube.InnerTubeApi
import com.lin.music.core.model.LyricsResult
import com.lin.music.core.model.LyricsSearchCandidate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class LrcLibResponse(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null
)

@Singleton
class LyricsRepository @Inject constructor(
    private val innerTubeApi: InnerTubeApi,
    okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(OkHttp) {
        engine { preconfigured = okHttpClient }
        install(ContentNegotiation) { json(json) }
    }

    suspend fun fetchLyrics(
        videoId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long
    ): LyricsResult = withContext(Dispatchers.IO) {
        // 1. Primary: LRCLIB synced lyrics
        val lrcMatch = runCatching {
            val response = httpClient.get("https://lrclib.net/api/get") {
                header("User-Agent", "LIN-YTMusic/1.0 (Android)")
                parameter("track_name", title)
                parameter("artist_name", artist)
                if (!album.isNullOrBlank()) parameter("album_name", album)
                if (durationMs > 0L) parameter("duration", (durationMs / 1000L).toInt())
            }
            if (response.status.isSuccess()) response.body<LrcLibResponse>() else null
        }.getOrNull()

        if (lrcMatch != null && (!lrcMatch.syncedLyrics.isNullOrBlank() || !lrcMatch.plainLyrics.isNullOrBlank())) {
            val synced = lrcMatch.syncedLyrics?.let { LrcParser.parse(it) }.orEmpty()
            return@withContext LyricsResult(
                videoId = videoId,
                syncedLines = synced,
                plainText = lrcMatch.plainLyrics,
                source = "LRCLIB"
            )
        }

        // 2. Fallback: YouTube Music native lyrics tab via /next
        val ytmText = runCatching {
            val next = innerTubeApi.getNext(videoId = videoId)
            val browseId = next.lyricsBrowseId ?: return@runCatching null
            innerTubeApi.getBrowseLyrics(browseId)
        }.getOrNull()

        if (!ytmText.isNullOrBlank()) {
            return@withContext LyricsResult(
                videoId = videoId,
                syncedLines = emptyList(),
                plainText = ytmText,
                source = "YouTube Music"
            )
        }

        LyricsResult(videoId = videoId, source = "Unavailable")
    }

    suspend fun searchLyrics(query: String): List<LyricsSearchCandidate> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext emptyList()
        runCatching {
            val response = httpClient.get("https://lrclib.net/api/search") {
                header("User-Agent", "LIN-YTMusic/1.0 (Android)")
                parameter("q", clean)
            }
            if (!response.status.isSuccess()) return@withContext emptyList()
            response.body<List<LrcLibResponse>>()
                .mapNotNull { entry ->
                    val id = entry.id ?: return@mapNotNull null
                    if (entry.syncedLyrics.isNullOrBlank() && entry.plainLyrics.isNullOrBlank()) {
                        return@mapNotNull null
                    }
                    LyricsSearchCandidate(
                        id = id,
                        trackName = entry.trackName.orEmpty().ifBlank { "Unknown track" },
                        artistName = entry.artistName.orEmpty().ifBlank { "Unknown artist" },
                        albumName = entry.albumName,
                        durationSeconds = entry.duration,
                        syncedLyrics = entry.syncedLyrics,
                        plainLyrics = entry.plainLyrics
                    )
                }
        }.getOrElse { emptyList() }
    }

    fun candidateToResult(
        videoId: String,
        candidate: LyricsSearchCandidate,
        syncOffsetMs: Long = 0L
    ): LyricsResult {
        val synced = candidate.syncedLyrics?.let { LrcParser.parse(it) }.orEmpty()
        return LyricsResult(
            videoId = videoId,
            syncedLines = synced,
            plainText = candidate.plainLyrics,
            source = "LRCLIB (Picked)",
            syncOffsetMs = syncOffsetMs
        )
    }

    suspend fun translateLines(
        lines: List<String>,
        targetLanguage: String = "en"
    ): List<String> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()
        val nonBlankIndices = lines.indices.filter { lines[it].isNotBlank() }
        if (nonBlankIndices.isEmpty()) return@withContext lines

        val translatedByIdx = HashMap<Int, String>(nonBlankIndices.size)
        for (chunkIndices in nonBlankIndices.chunked(18)) {
            val payload = chunkIndices.joinToString("\n") { lines[it].replace('\n', ' ').trim() }
            val batchResult = translateBatchText(payload, targetLanguage)
            val split = batchResult?.split('\n')
            if (split != null && split.size == chunkIndices.size) {
                chunkIndices.forEachIndexed { pos, origIdx ->
                    translatedByIdx[origIdx] = split[pos].trim()
                }
            } else {
                for (origIdx in chunkIndices) {
                    val single = translateBatchText(lines[origIdx], targetLanguage)
                    if (!single.isNullOrBlank()) {
                        translatedByIdx[origIdx] = single.trim()
                    }
                }
            }
        }
        lines.indices.map { idx -> translatedByIdx[idx] ?: "" }
    }

    private suspend fun translateBatchText(text: String, targetLanguage: String): String? = runCatching {
        val response = httpClient.get("https://translate.googleapis.com/translate_a/single") {
            header("User-Agent", "Mozilla/5.0")
            parameter("client", "gtx")
            parameter("sl", "auto")
            parameter("tl", targetLanguage)
            parameter("dt", "t")
            parameter("q", text)
        }
        if (!response.status.isSuccess()) return@runCatching null
        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return@runCatching null
        val sentences = root.firstOrNull()?.jsonArray ?: return@runCatching null
        buildString {
            for (segment in sentences) {
                val part = segment.jsonArray.firstOrNull()?.jsonPrimitive?.content ?: continue
                append(part)
            }
        }.ifBlank { null }
    }.getOrNull()
}
