package com.lin.music.core.innertube

import com.lin.music.core.innertube.auth.SapisidAuthSigner
import com.lin.music.core.innertube.client.YouTubeClient
import com.lin.music.core.innertube.parser.InnerTubeParsers
import com.lin.music.core.model.AccountSession
import com.lin.music.core.model.AlbumDetail
import com.lin.music.core.model.AlbumSummary
import com.lin.music.core.model.ArtistDetail
import com.lin.music.core.model.ArtistSummary
import com.lin.music.core.model.HomePage
import com.lin.music.core.model.NextPage
import com.lin.music.core.model.PlaylistDetail
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.SearchFilter
import com.lin.music.core.model.SearchResultPage
import com.lin.music.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface SessionProvider {
    suspend fun currentSession(): AccountSession?
}

@Singleton
class InnerTubeApi @Inject constructor(
    private val sessionProvider: SessionProvider,
    private val okHttpClient: OkHttpClient
) {
    internal var baseUrl: String? = null
    private val visitorDataCache = ConcurrentHashMap<String, Pair<String, Long>>()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(OkHttp) {
        engine { preconfigured = okHttpClient }
        install(ContentNegotiation) { json(json) }
    }

    suspend fun fetchVisitorData(client: YouTubeClient, forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            visitorDataCache[client.name]?.let { (vd, cachedAt) ->
                if (now - cachedAt < 30L * 60_000L && vd.isNotBlank()) return vd
            }
        }
        return runCatching {
            val host = baseUrl ?: client.apiHost
            val payload = buildJsonObject {
                put("context", client.buildContextJson())
            }
            val response = httpClient.post("${host}visitor_id") {
                parameter("prettyPrint", "false")
                header("User-Agent", client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (!response.status.isSuccess()) return@runCatching null
            val root: JsonObject = response.body()
            val vd = (root["responseContext"] as? JsonObject)
                ?.get("visitorData")
                ?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?.takeIf { it.isNotBlank() }
            if (vd != null) {
                visitorDataCache[client.name] = vd to now
            }
            vd
        }.getOrNull()
    }

    private suspend fun postInnerTube(
        endpoint: String,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
        bodyBuilder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
        queryParams: Map<String, String> = emptyMap(),
        visitorDataOverride: String? = null
    ): JsonObject {
        val session = if (client.supportsAuthCookies) sessionProvider.currentSession() else null
        val authHeaders = if (client.supportsAuthCookies) {
            SapisidAuthSigner.buildSessionHeaders(session)
        } else {
            emptyMap()
        }
        val effectiveVisitorData = visitorDataOverride ?: session?.visitorData

        val payload = buildJsonObject {
            put("context", client.buildContextJson(visitorData = effectiveVisitorData))
            bodyBuilder()
        }

        val host = baseUrl ?: client.apiHost
        val response = httpClient.post("$host$endpoint") {
            parameter("prettyPrint", "false")
            queryParams.forEach { (k, v) -> parameter(k, v) }
            header("User-Agent", client.userAgent)
            header("X-YouTube-Client-Name", client.clientId)
            header("X-YouTube-Client-Version", client.clientVersion)
            if (!client.supportsAuthCookies && !effectiveVisitorData.isNullOrBlank()) {
                header("X-Goog-Visitor-Id", effectiveVisitorData)
            }
            authHeaders.forEach { (k, v) -> header(k, v) }
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            val errSnippet = runCatching { response.bodyAsText().take(240) }.getOrDefault("")
            throw IllegalStateException("InnerTube $endpoint (${client.name}) HTTP ${response.status.value}: $errSnippet")
        }
        return response.body()
    }

    suspend fun getAccountInfo(): InnerTubeParsers.ParsedAccountInfo {
        val root = postInnerTube("account/account_menu", bodyBuilder = {})
        return InnerTubeParsers.parseAccountInfo(root)
    }

    suspend fun getHome(continuation: String? = null): HomePage {
        val params = if (continuation != null) mapOf("continuation" to continuation, "type" to "next") else emptyMap()
        val root = postInnerTube(
            endpoint = "browse",
            queryParams = params,
            bodyBuilder = {
                if (continuation == null) {
                    put("browseId", "FEmusic_home")
                }
            }
        )
        return InnerTubeParsers.parseHomeResponse(root)
    }

    suspend fun getLibrarySongs(continuation: String? = null): Pair<List<Track>, String?> {
        val params = if (continuation != null) mapOf("continuation" to continuation, "type" to "next") else emptyMap()
        val root = postInnerTube(
            endpoint = "browse",
            queryParams = params,
            bodyBuilder = {
                if (continuation == null) put("browseId", "FEmusic_liked_videos")
            }
        )
        return InnerTubeParsers.parseTracksFromBrowseShelf(root)
    }

    suspend fun getLikedSongs(continuation: String? = null): PlaylistDetail {
        val params = if (continuation != null) mapOf("continuation" to continuation, "type" to "next") else emptyMap()
        val root = postInnerTube(
            endpoint = "browse",
            queryParams = params,
            bodyBuilder = {
                if (continuation == null) put("browseId", "VLLM")
            }
        )
        return InnerTubeParsers.parsePlaylistResponse(root, "VLLM")
    }

    suspend fun getHistory(continuation: String? = null): Pair<List<Track>, String?> {
        val params = if (continuation != null) mapOf("continuation" to continuation, "type" to "next") else emptyMap()
        val root = postInnerTube(
            endpoint = "browse",
            queryParams = params,
            bodyBuilder = {
                if (continuation == null) put("browseId", "FEmusic_history")
            }
        )
        return InnerTubeParsers.parseTracksFromBrowseShelf(root)
    }

    suspend fun getLibraryPlaylists(): List<PlaylistSummary> {
        val root = postInnerTube("browse", bodyBuilder = { put("browseId", "FEmusic_liked_playlists") })
        return InnerTubeParsers.parseLibraryPlaylists(root)
    }

    suspend fun getLibraryAlbums(): List<AlbumSummary> {
        val root = postInnerTube("browse", bodyBuilder = { put("browseId", "FEmusic_liked_albums") })
        return InnerTubeParsers.parseLibraryAlbums(root)
    }

    suspend fun getLibraryArtists(): List<ArtistSummary> {
        val root = postInnerTube("browse", bodyBuilder = { put("browseId", "FEmusic_library_corpus_track_artists") })
        val trackArtists = InnerTubeParsers.parseLibraryArtists(root)
        if (trackArtists.isNotEmpty()) return trackArtists
        val fallbackRoot = postInnerTube("browse", bodyBuilder = { put("browseId", "FEmusic_library_corpus_artists") })
        return InnerTubeParsers.parseLibraryArtists(fallbackRoot)
    }

    suspend fun getAlbum(browseId: String): AlbumDetail {
        val root = postInnerTube("browse", bodyBuilder = { put("browseId", browseId) })
        return InnerTubeParsers.parseAlbumResponse(root, browseId)
    }

    suspend fun getArtist(browseId: String): ArtistDetail {
        val root = postInnerTube("browse", bodyBuilder = { put("browseId", browseId) })
        return InnerTubeParsers.parseArtistResponse(root, browseId)
    }

    suspend fun getPlaylist(browseId: String, continuation: String? = null): PlaylistDetail {
        val normalizedId = if (browseId.startsWith("PL") || browseId.startsWith("RD") || browseId.startsWith("OL")) {
            "VL$browseId"
        } else {
            browseId
        }
        val params = if (continuation != null) mapOf("continuation" to continuation, "type" to "next") else emptyMap()
        val root = postInnerTube(
            endpoint = "browse",
            queryParams = params,
            bodyBuilder = {
                if (continuation == null) put("browseId", normalizedId)
            }
        )
        return InnerTubeParsers.parsePlaylistResponse(root, normalizedId)
    }

    suspend fun search(query: String, filter: SearchFilter, continuation: String? = null): SearchResultPage {
        val params = if (continuation != null) mapOf("continuation" to continuation, "type" to "next") else emptyMap()
        val root = postInnerTube(
            endpoint = "search",
            queryParams = params,
            bodyBuilder = {
                if (continuation == null) {
                    put("query", query)
                    filter.innertubeParams?.let { put("params", it) }
                }
            }
        )
        return InnerTubeParsers.parseSearchResponse(root)
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val root = postInnerTube("music/get_search_suggestions", bodyBuilder = { put("input", query) })
        return InnerTubeParsers.parseSearchSuggestions(root)
    }

    suspend fun getNext(
        videoId: String? = null,
        playlistId: String? = null,
        params: String? = null,
        continuation: String? = null
    ): NextPage {
        val root = postInnerTube("next", bodyBuilder = {
            videoId?.let { put("videoId", it) }
            playlistId?.let { put("playlistId", it) }
            params?.let { put("params", it) }
            continuation?.let { put("continuation", it) }
            put("isAudioOnly", true)
            put("enablePersistentPlaylistPanel", true)
        })
        return InnerTubeParsers.parseNextResponse(root)
    }

    suspend fun getBrowseLyrics(browseId: String): String? {
        val root = postInnerTube("browse", bodyBuilder = { put("browseId", browseId) })
        return InnerTubeParsers.parseBrowseLyrics(root)
    }

    suspend fun getRelated(browseId: String): HomePage {
        val root = postInnerTube("browse", bodyBuilder = { put("browseId", browseId) })
        return InnerTubeParsers.parseHomeResponse(root)
    }

    suspend fun getPlayerRaw(
        videoId: String,
        client: YouTubeClient,
        signatureTimestamp: Int? = null,
        visitorDataOverride: String? = null
    ): JsonObject {
        return postInnerTube(
            endpoint = "player",
            client = client,
            visitorDataOverride = visitorDataOverride,
            bodyBuilder = {
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                if (signatureTimestamp != null) {
                    putJsonObject("playbackContext") {
                        putJsonObject("contentPlaybackContext") {
                            put("signatureTimestamp", signatureTimestamp)
                        }
                    }
                }
            }
        )
    }

    // --- Authenticated Mutations ---

    suspend fun likeSong(videoId: String, like: Boolean) {
        val endpoint = if (like) "like/like" else "like/removelike"
        postInnerTube(endpoint, bodyBuilder = {
            putJsonObject("target") { put("videoId", videoId) }
        })
    }

    suspend fun sendFeedback(feedbackTokens: List<String>) {
        if (feedbackTokens.isEmpty()) return
        postInnerTube("feedback", bodyBuilder = {
            put("isFeedbackTokenUnencrypted", false)
            put("shouldMerge", false)
            putJsonArray("feedbackTokens") {
                feedbackTokens.forEach { add(it) }
            }
        })
    }

    suspend fun createPlaylist(title: String, description: String = "", privacyStatus: String = "PRIVATE"): String? {
        val root = postInnerTube("playlist/create", bodyBuilder = {
            put("title", title)
            if (description.isNotBlank()) put("description", description)
            put("privacyStatus", privacyStatus)
        })
        return (root["playlistId"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    }

    suspend fun deletePlaylist(playlistId: String) {
        postInnerTube("playlist/delete", bodyBuilder = {
            put("playlistId", playlistId.removePrefix("VL"))
        })
    }

    suspend fun addTrackToPlaylist(playlistId: String, videoId: String) {
        editPlaylist(
            playlistId = playlistId,
            actions = buildJsonArray {
                add(buildJsonObject {
                    put("action", "ACTION_ADD_VIDEO")
                    put("addedVideoId", videoId)
                })
            }
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, videoId: String, setVideoId: String) {
        editPlaylist(
            playlistId = playlistId,
            actions = buildJsonArray {
                add(buildJsonObject {
                    put("action", "ACTION_REMOVE_VIDEO")
                    put("removedVideoId", videoId)
                    put("setVideoId", setVideoId)
                })
            }
        )
    }

    suspend fun moveTrackInPlaylist(playlistId: String, setVideoId: String, movedBeforeSetVideoId: String?) {
        editPlaylist(
            playlistId = playlistId,
            actions = buildJsonArray {
                add(buildJsonObject {
                    put("action", "ACTION_MOVE_VIDEO_BEFORE")
                    put("setVideoId", setVideoId)
                    if (movedBeforeSetVideoId != null) {
                        put("movedSetVideoIdSuccessor", movedBeforeSetVideoId)
                    }
                })
            }
        )
    }

    suspend fun updatePlaylistMetadata(playlistId: String, title: String, description: String) {
        editPlaylist(
            playlistId = playlistId,
            actions = buildJsonArray {
                add(buildJsonObject {
                    put("action", "ACTION_SET_PLAYLIST_NAME")
                    put("playlistName", title)
                })
                add(buildJsonObject {
                    put("action", "ACTION_SET_PLAYLIST_DESCRIPTION")
                    put("playlistDescription", description)
                })
            }
        )
    }

    private suspend fun editPlaylist(playlistId: String, actions: JsonArray) {
        postInnerTube("browse/edit_playlist", bodyBuilder = {
            put("playlistId", playlistId.removePrefix("VL"))
            put("actions", actions)
        })
    }

    suspend fun subscribeArtist(channelId: String, subscribe: Boolean) {
        val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
        postInnerTube(endpoint, bodyBuilder = {
            putJsonArray("channelIds") { add(channelId) }
        })
    }

    suspend fun saveAlbumToLibrary(playlistId: String, save: Boolean) {
        val endpoint = if (save) "like/like" else "like/removelike"
        postInnerTube(endpoint, bodyBuilder = {
            putJsonObject("target") { put("playlistId", playlistId.removePrefix("VL")) }
        })
    }
}
