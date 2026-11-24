package com.lin.music.core.playback.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.Track

/**
 * Pure hierarchical browse-tree helper for Android Auto / Wear OS MediaLibraryService.
 */
object LinAutoMediaTree {
    const val ROOT_ID = "lin_root"
    const val DOWNLOADS_ID = "lin_downloads"
    const val PLAYLISTS_ID = "lin_playlists"
    const val RECENT_ID = "lin_recent"
    const val SONGS_ID = "lin_songs"
    const val PLAYLIST_PREFIX = "lin_pl:"
    const val TRACK_PREFIX = "lin_tr:"

    fun rootItem(): MediaItem = folderItem(
        id = ROOT_ID,
        title = "LIN",
        subtitle = "Library",
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    )

    fun topLevelCategories(): List<MediaItem> = listOf(
        folderItem(DOWNLOADS_ID, "Downloads", "Offline ready", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        folderItem(PLAYLISTS_ID, "Playlists", "Your library playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        folderItem(RECENT_ID, "Recent", "Recently played", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        folderItem(SONGS_ID, "Songs", "Saved library tracks", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
    )

    fun playlistItems(playlists: List<PlaylistSummary>): List<MediaItem> =
        playlists.map { pl ->
            folderItem(
                id = "$PLAYLIST_PREFIX${pl.id}",
                title = pl.title,
                subtitle = pl.trackCount?.let { "$it tracks" } ?: pl.subtitle,
                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                artworkUri = pl.thumbnailUrl
            )
        }

    fun trackItems(tracks: List<Track>, contextTag: String): List<MediaItem> =
        tracks.map { track ->
            val meta = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artistDisplay)
                .setAlbumTitle(track.album?.title)
                .setArtworkUri(track.thumbnailUrl?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply {
                    putString("context", contextTag)
                    putString("videoId", track.videoId)
                })
                .build()
            MediaItem.Builder()
                .setMediaId("$TRACK_PREFIX$contextTag:${track.videoId}")
                .setMediaMetadata(meta)
                .build()
        }

    fun folderItem(
        id: String,
        title: String,
        subtitle: String? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        artworkUri: String? = null
    ): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(artworkUri?.let(Uri::parse))
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(meta)
            .build()
    }
}
