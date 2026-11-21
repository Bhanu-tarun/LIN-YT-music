package com.lin.music.core.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lin.music.core.model.AlbumRef
import com.lin.music.core.model.AlbumSummary
import com.lin.music.core.model.ArtistRef
import com.lin.music.core.model.ArtistSummary
import com.lin.music.core.model.LyricsResult
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.StreamInfo
import com.lin.music.core.model.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val entityJson = Json { ignoreUnknownKeys = true }

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["inLibrary"], name = "index_tracks_inLibrary"),
        Index(value = ["isLiked"], name = "index_tracks_isLiked"),
        Index(value = ["inHistory", "historyTimestampMs"], name = "index_tracks_inHistory_historyTimestampMs"),
        Index(value = ["albumId"], name = "index_tracks_albumId"),
        Index(value = ["playCount"], name = "index_tracks_playCount")
    ]
)
data class TrackEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artistsJson: String,
    val albumId: String?,
    val albumTitle: String?,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val inLibrary: Boolean,
    val isLiked: Boolean,
    val inHistory: Boolean,
    val historyTimestampMs: Long,
    val feedbackTokenAdd: String?,
    val feedbackTokenRemove: String?,
    val isExplicit: Boolean,
    val playCount: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun toModel(tags: List<String> = emptyList(), setVideoId: String? = null): Track {
        val artists = runCatching {
            entityJson.decodeFromString<List<ArtistRef>>(artistsJson)
        }.getOrDefault(emptyList())
        return Track(
            videoId = videoId,
            title = title,
            artists = artists,
            album = if (!albumTitle.isNullOrBlank() || !albumId.isNullOrBlank()) {
                AlbumRef(id = albumId, title = albumTitle.orEmpty())
            } else null,
            durationMs = durationMs,
            thumbnailUrl = thumbnailUrl,
            inLibrary = inLibrary,
            isLiked = isLiked,
            feedbackTokenAdd = feedbackTokenAdd,
            feedbackTokenRemove = feedbackTokenRemove,
            setVideoId = setVideoId,
            isExplicit = isExplicit,
            tags = tags
        )
    }
}

fun Track.toEntity(
    inHistory: Boolean = false,
    historyTimestampMs: Long = 0L,
    playCount: Int = 0
): TrackEntity = TrackEntity(
    videoId = videoId,
    title = title,
    artistsJson = entityJson.encodeToString(artists),
    albumId = album?.id,
    albumTitle = album?.title,
    durationMs = durationMs,
    thumbnailUrl = thumbnailUrl,
    inLibrary = inLibrary,
    isLiked = isLiked,
    inHistory = inHistory,
    historyTimestampMs = historyTimestampMs,
    feedbackTokenAdd = feedbackTokenAdd,
    feedbackTokenRemove = feedbackTokenRemove,
    isExplicit = isExplicit,
    playCount = playCount
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val browseId: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val trackCount: Int?,
    val isEditable: Boolean,
    val inLibrary: Boolean = true,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun toSummary(tags: List<String> = emptyList()): PlaylistSummary = PlaylistSummary(
        id = id,
        browseId = browseId,
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        trackCount = trackCount,
        isEditable = isEditable,
        tags = tags
    )
}

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "videoId", "position"],
    indices = [
        Index(value = ["videoId"], name = "index_playlist_tracks_videoId")
    ]
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val videoId: String,
    val setVideoId: String?,
    val position: Int
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val browseId: String,
    val playlistId: String?,
    val title: String,
    val artistsJson: String,
    val year: String?,
    val isEpOrSingle: Boolean,
    val thumbnailUrl: String?,
    val inLibrary: Boolean,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun toSummary(tags: List<String> = emptyList()): AlbumSummary {
        val artists = runCatching {
            entityJson.decodeFromString<List<ArtistRef>>(artistsJson)
        }.getOrDefault(emptyList())
        return AlbumSummary(
            browseId = browseId,
            playlistId = playlistId,
            title = title,
            artists = artists,
            year = year,
            isEpOrSingle = isEpOrSingle,
            thumbnailUrl = thumbnailUrl,
            tags = tags
        )
    }
}

fun AlbumSummary.toEntity(inLibrary: Boolean = true): AlbumEntity = AlbumEntity(
    browseId = browseId,
    playlistId = playlistId,
    title = title,
    artistsJson = entityJson.encodeToString(artists),
    year = year,
    isEpOrSingle = isEpOrSingle,
    thumbnailUrl = thumbnailUrl,
    inLibrary = inLibrary
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val browseId: String,
    val name: String,
    val subscriberCount: String?,
    val thumbnailUrl: String?,
    val isSubscribed: Boolean,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun toSummary(): ArtistSummary = ArtistSummary(
        browseId = browseId,
        name = name,
        subscriberCount = subscriberCount,
        thumbnailUrl = thumbnailUrl
    )
}

@Entity(
    tableName = "item_tags",
    primaryKeys = ["itemId", "itemType", "tag"]
)
data class ItemTagEntity(
    val itemId: String,
    val itemType: String, // "track", "album", "playlist"
    val tag: String
)

@Entity(tableName = "stream_cache")
data class StreamCacheEntity(
    @PrimaryKey val videoId: String,
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val loudnessDb: Float?,
    val expiresAtEpochMs: Long,
    val codecLabel: String,
    val clientSource: String,
    val userAgent: String = ""
) {
    fun toModel(): StreamInfo = StreamInfo(
        videoId = videoId,
        url = url,
        mimeType = mimeType,
        bitrate = bitrate,
        loudnessDb = loudnessDb,
        expiresAtEpochMs = expiresAtEpochMs,
        codecLabel = codecLabel,
        clientSource = clientSource,
        userAgent = userAgent
    )
}

fun StreamInfo.toEntity(): StreamCacheEntity = StreamCacheEntity(
    videoId = videoId,
    url = url,
    mimeType = mimeType,
    bitrate = bitrate,
    loudnessDb = loudnessDb,
    expiresAtEpochMs = expiresAtEpochMs,
    codecLabel = codecLabel,
    clientSource = clientSource,
    userAgent = userAgent
)

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["state"], name = "index_downloads_state")
    ]
)
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val state: Int, // 0=queued, 1=downloading, 2=completed, 3=failed
    val progressPercent: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val sourceFlags: Int = SOURCE_MANUAL,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_MANUAL = 1
        const val SOURCE_SMART = 2
        const val SOURCE_RECENTLY_PLAYED = 4
    }
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "persisted_queue")
data class PersistedQueueEntity(
    @PrimaryKey val position: Int,
    val videoId: String,
    val trackJson: String,
    val isCurrent: Boolean,
    val playbackPositionMs: Long
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val videoId: String,
    val resultJson: String,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun toModel(): LyricsResult? = runCatching {
        entityJson.decodeFromString<LyricsResult>(resultJson)
    }.getOrNull()
}
