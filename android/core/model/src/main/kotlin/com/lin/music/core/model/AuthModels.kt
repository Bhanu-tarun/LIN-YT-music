package com.lin.music.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AccountSession(
    val cookies: String,
    val sapisid: String,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val accountName: String? = null,
    val accountHandle: String? = null,
    val avatarUrl: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    val isValid: Boolean
        get() = cookies.isNotBlank() && sapisid.isNotBlank()
}

@Serializable
data class UserPreferences(
    val isDarkTheme: Boolean = true,
    val ambientMode: AmbientMode = AmbientMode.AUDIO_REACTIVE,
    val ambientReactivity: AmbientReactivity = AmbientReactivity.EXPRESSIVE,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val downloadQuality: AudioQuality = AudioQuality.HIGH,
    val downloadWifiOnly: Boolean = false,
    val smartDownloadsEnabled: Boolean = false,
    val smartDownloadsLimit: Int = 50,
    val recentlyPlayedDownloadsEnabled: Boolean = false,
    val recentlyPlayedDownloadsLimit: Int = 20,
    val capitalizeLabels: Boolean = false,
    val normalizeVolume: Boolean = true,
    val skipSilence: Boolean = false,
    val autoMix: Boolean = true,
    val sponsorBlockEnabled: Boolean = false,
    val reportPlaybackHistory: Boolean = true,
    val cacheLimitMb: Int = 1024,
    val includeMusicVideos: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val showTrackThumbnails: Boolean = false,
    val contentRegion: String = ContentRegions.DEFAULT_CODE,
    val songsSortOrder: SongSortOrder = SongSortOrder.TITLE_ASC,
    val albumsSortOrder: AlbumSortOrder = AlbumSortOrder.TITLE_ASC,
    val artistsSortOrder: ArtistSortOrder = ArtistSortOrder.NAME_ASC,
    val offlineMode: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val microFadeOnPause: Boolean = true,
    val shuffleMode: ShuffleMode = ShuffleMode.TRUE_RANDOM,
    val eqEnabled: Boolean = false,
    val activeEqProfileId: String = "flat",
    val customEqProfiles: List<EqualizerProfile> = emptyList(),
    val bluetoothEqAssignments: List<BluetoothEqAssignment> = emptyList(),
    val lyricsShowOriginal: Boolean = true,
    val lyricsShowRomanized: Boolean = false,
    val lyricsShowTranslation: Boolean = false,
    val lyricsTranslationLanguage: String = "en"
)

@Serializable
data class LinBackupPayload(
    val version: Int = 1,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val preferences: UserPreferences,
    val itemTags: List<ItemTagRecord> = emptyList()
)

@Serializable
data class ItemTagRecord(
    val itemId: String,
    val itemType: String,
    val tag: String
)
