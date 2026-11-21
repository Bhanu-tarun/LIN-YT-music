package com.lin.music.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtistRef(
    val id: String? = null,
    val name: String
)

@Serializable
data class AlbumRef(
    val id: String? = null,
    val title: String
)

@Serializable
data class Track(
    val videoId: String,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val album: AlbumRef? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val inLibrary: Boolean = false,
    val isLiked: Boolean = false,
    val feedbackTokenAdd: String? = null,
    val feedbackTokenRemove: String? = null,
    val setVideoId: String? = null,
    val isExplicit: Boolean = false,
    val tags: List<String> = emptyList()
) {
    val artistDisplay: String
        get() = artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }

    val formattedDuration: String
        get() {
            if (durationMs <= 0L) return ""
            val totalSec = durationMs / 1000L
            val mins = totalSec / 60L
            val secs = totalSec % 60L
            return "$mins:${secs.toString().padStart(2, '0')}"
        }
}
