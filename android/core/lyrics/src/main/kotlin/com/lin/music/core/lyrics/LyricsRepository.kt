package com.lin.music.core.lyrics

import com.lin.music.core.innertube.InnerTubeApi
import com.lin.music.core.model.LyricsResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class LrcLibResponse(
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
}
