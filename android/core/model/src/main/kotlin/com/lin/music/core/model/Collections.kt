package com.lin.music.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistSummary(
    val id: String,
    val browseId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val trackCount: Int? = null,
    val isEditable: Boolean = false,
    val tags: List<String> = emptyList()
)

@Serializable
data class PlaylistDetail(
    val id: String,
    val browseId: String,
    val title: String,
    val description: String? = null,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val continuation: String? = null,
    val isEditable: Boolean = false,
    val inLibrary: Boolean = false,
    val privacyStatus: String = "PRIVATE",
    val radioEndpointPlaylistId: String? = null,
    val shuffleEndpointPlaylistId: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class AlbumSummary(
    val browseId: String,
    val playlistId: String? = null,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val year: String? = null,
    val isEpOrSingle: Boolean = false,
    val thumbnailUrl: String? = null,
    val tags: List<String> = emptyList()
) {
    val artistDisplay: String
        get() = artists.joinToString(", ") { it.name }
}

@Serializable
data class AlbumDetail(
    val browseId: String,
    val playlistId: String? = null,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val year: String? = null,
    val description: String? = null,
    val isEpOrSingle: Boolean = false,
    val thumbnailUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val inLibrary: Boolean = false,
    val tags: List<String> = emptyList()
) {
    val artistDisplay: String
        get() = artists.joinToString(", ") { it.name }
}

@Serializable
data class ArtistSummary(
    val browseId: String,
    val name: String,
    val subscriberCount: String? = null,
    val thumbnailUrl: String? = null
)

@Serializable
data class ArtistDetail(
    val browseId: String,
    val name: String,
    val description: String? = null,
    val subscriberCount: String? = null,
    val thumbnailUrl: String? = null,
    val isSubscribed: Boolean = false,
    val shufflePlaylistId: String? = null,
    val radioPlaylistId: String? = null,
    val topSongsBrowseId: String? = null,
    val topSongs: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val singlesAndEps: List<AlbumSummary> = emptyList(),
    val relatedArtists: List<ArtistSummary> = emptyList()
)

@Serializable
sealed interface HomeItem {
    @Serializable
    data class SongItem(val track: Track) : HomeItem

    @Serializable
    data class AlbumItem(val album: AlbumSummary) : HomeItem

    @Serializable
    data class PlaylistItem(val playlist: PlaylistSummary) : HomeItem

    @Serializable
    data class ArtistItem(val artist: ArtistSummary) : HomeItem
}

@Serializable
data class HomeSection(
    val title: String,
    val items: List<HomeItem>,
    val isQuickPicks: Boolean = false
)

@Serializable
data class HomePage(
    val sections: List<HomeSection>,
    val continuation: String? = null
)

@Serializable
enum class SearchFilter(val label: String, val innertubeParams: String?) {
    ALL("all", null),
    SONGS("songs", "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D"),
    ALBUMS("albums", "EgWKAQIYAWoKEAoQAxAEEAkQBQ%3D%3D"),
    EPS("eps", "EgWKAQIYAWoKEAkQAxAEEAUQCg%3D%3D"),
    ARTISTS("artists", "EgWKAQIgAWoKEAoQAxAEEAkQBQ%3D%3D"),
    PLAYLISTS("playlists", "EgWKAQIoAWoKEAoQAxAEEAkQBQ%3D%3D"),
    LIBRARY("library", null)
}

@Serializable
data class SearchResultPage(
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val continuation: String? = null
)

@Serializable
data class MixChip(
    val title: String,
    val playlistId: String,
    val params: String? = null,
    val isSelected: Boolean = false
)

@Serializable
data class NextPage(
    val tracks: List<Track> = emptyList(),
    val currentTrackMetadata: Track? = null,
    val playlistId: String? = null,
    val continuation: String? = null,
    val lyricsBrowseId: String? = null,
    val relatedBrowseId: String? = null,
    val mixChips: List<MixChip> = emptyList()
)
