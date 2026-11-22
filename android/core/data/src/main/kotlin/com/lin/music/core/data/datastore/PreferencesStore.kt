package com.lin.music.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin.music.core.innertube.RegionProvider
import com.lin.music.core.model.AmbientMode
import com.lin.music.core.model.AmbientReactivity
import com.lin.music.core.model.AudioQuality
import com.lin.music.core.model.ContentRegions
import com.lin.music.core.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lin_user_prefs")

@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) : RegionProvider {
    @Volatile
    private var cachedRegionCode: String? = null

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val AMBIENT_MODE = stringPreferencesKey("ambient_mode")
        val AMBIENT_REACTIVITY = stringPreferencesKey("ambient_reactivity")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val SMART_DOWNLOADS_ENABLED = booleanPreferencesKey("smart_downloads_enabled")
        val SMART_DOWNLOADS_LIMIT = intPreferencesKey("smart_downloads_limit")
        val RECENTLY_PLAYED_DOWNLOADS_ENABLED = booleanPreferencesKey("recently_played_downloads_enabled")
        val RECENTLY_PLAYED_DOWNLOADS_LIMIT = intPreferencesKey("recently_played_downloads_limit")
        val CAPITALIZE_LABELS = booleanPreferencesKey("capitalize_labels")
        val NORMALIZE_VOLUME = booleanPreferencesKey("normalize_volume")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val AUTO_MIX = booleanPreferencesKey("auto_mix")
        val SPONSOR_BLOCK = booleanPreferencesKey("sponsor_block")
        val REPORT_HISTORY = booleanPreferencesKey("report_history")
        val CACHE_LIMIT_MB = intPreferencesKey("cache_limit_mb")
        val INCLUDE_MVS = booleanPreferencesKey("include_mvs")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
        val SHOW_THUMBNAILS = booleanPreferencesKey("show_thumbnails")
        val SPEED_MIGRATED_V2 = booleanPreferencesKey("speed_migrated_v2")
        val LAST_SMART_DOWNLOAD_SYNC_MS = longPreferencesKey("last_smart_download_sync_ms")
        val LAST_RECENT_DOWNLOAD_SYNC_MS = longPreferencesKey("last_recent_download_sync_ms")
        val CONTENT_REGION = stringPreferencesKey("content_region")
    }

    private fun fromPreferences(prefs: Preferences): UserPreferences {
        val region = ContentRegions.normalize(prefs[Keys.CONTENT_REGION])
        cachedRegionCode = region
        return UserPreferences(
            isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
            ambientMode = prefs[Keys.AMBIENT_MODE]?.let { runCatching { AmbientMode.valueOf(it) }.getOrNull() }
                ?: AmbientMode.AUDIO_REACTIVE,
            ambientReactivity = prefs[Keys.AMBIENT_REACTIVITY]?.let { runCatching { AmbientReactivity.valueOf(it) }.getOrNull() }
                ?: AmbientReactivity.EXPRESSIVE,
            audioQuality = prefs[Keys.AUDIO_QUALITY]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
                ?: AudioQuality.HIGH,
            downloadQuality = prefs[Keys.DOWNLOAD_QUALITY]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
                ?: AudioQuality.HIGH,
            downloadWifiOnly = prefs[Keys.DOWNLOAD_WIFI_ONLY] ?: false,
            smartDownloadsEnabled = prefs[Keys.SMART_DOWNLOADS_ENABLED] ?: false,
            smartDownloadsLimit = prefs[Keys.SMART_DOWNLOADS_LIMIT] ?: 50,
            recentlyPlayedDownloadsEnabled = prefs[Keys.RECENTLY_PLAYED_DOWNLOADS_ENABLED] ?: false,
            recentlyPlayedDownloadsLimit = prefs[Keys.RECENTLY_PLAYED_DOWNLOADS_LIMIT] ?: 20,
            capitalizeLabels = prefs[Keys.CAPITALIZE_LABELS] ?: false,
            normalizeVolume = prefs[Keys.NORMALIZE_VOLUME] ?: true,
            skipSilence = prefs[Keys.SKIP_SILENCE] ?: false,
            autoMix = prefs[Keys.AUTO_MIX] ?: true,
            sponsorBlockEnabled = prefs[Keys.SPONSOR_BLOCK] ?: false,
            reportPlaybackHistory = prefs[Keys.REPORT_HISTORY] ?: true,
            cacheLimitMb = prefs[Keys.CACHE_LIMIT_MB] ?: 1024,
            includeMusicVideos = prefs[Keys.INCLUDE_MVS] ?: false,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            playbackPitch = prefs[Keys.PLAYBACK_PITCH] ?: 1.0f,
            showTrackThumbnails = prefs[Keys.SHOW_THUMBNAILS] ?: false,
            contentRegion = region
        )
    }

    val preferencesFlow: Flow<UserPreferences> = context.userPrefsDataStore.data.map(::fromPreferences)

    suspend fun current(): UserPreferences = preferencesFlow.first()

    override suspend fun currentRegionCode(): String = cachedRegionCode ?: current().contentRegion

    suspend fun ensureNormalSpeedDefaultOnce() {
        context.userPrefsDataStore.edit { prefs ->
            if (prefs[Keys.SPEED_MIGRATED_V2] != true) {
                val saved = prefs[Keys.PLAYBACK_SPEED]
                if (saved != null && kotlin.math.abs(saved - 0.8f) < 0.01f) {
                    prefs[Keys.PLAYBACK_SPEED] = 1.0f
                }
                prefs[Keys.SPEED_MIGRATED_V2] = true
            }
        }
    }

    suspend fun toggleTheme() {
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs[Keys.IS_DARK_THEME] ?: true
            prefs[Keys.IS_DARK_THEME] = !current
        }
    }

    suspend fun getLastSmartDownloadSyncMs(): Long =
        context.userPrefsDataStore.data.map { it[Keys.LAST_SMART_DOWNLOAD_SYNC_MS] ?: 0L }.first()

    suspend fun setLastSmartDownloadSyncMs(epochMs: Long) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.LAST_SMART_DOWNLOAD_SYNC_MS] = epochMs
        }
    }

    suspend fun getLastRecentDownloadSyncMs(): Long =
        context.userPrefsDataStore.data.map { it[Keys.LAST_RECENT_DOWNLOAD_SYNC_MS] ?: 0L }.first()

    suspend fun setLastRecentDownloadSyncMs(epochMs: Long) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.LAST_RECENT_DOWNLOAD_SYNC_MS] = epochMs
        }
    }

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        context.userPrefsDataStore.edit { prefs ->
            val current = fromPreferences(prefs)
            val updated = transform(current)
            prefs[Keys.IS_DARK_THEME] = updated.isDarkTheme
            prefs[Keys.AMBIENT_MODE] = updated.ambientMode.name
            prefs[Keys.AMBIENT_REACTIVITY] = updated.ambientReactivity.name
            prefs[Keys.AUDIO_QUALITY] = updated.audioQuality.name
            prefs[Keys.DOWNLOAD_QUALITY] = updated.downloadQuality.name
            prefs[Keys.DOWNLOAD_WIFI_ONLY] = updated.downloadWifiOnly
            prefs[Keys.SMART_DOWNLOADS_ENABLED] = updated.smartDownloadsEnabled
            prefs[Keys.SMART_DOWNLOADS_LIMIT] = updated.smartDownloadsLimit
            prefs[Keys.RECENTLY_PLAYED_DOWNLOADS_ENABLED] = updated.recentlyPlayedDownloadsEnabled
            prefs[Keys.RECENTLY_PLAYED_DOWNLOADS_LIMIT] = updated.recentlyPlayedDownloadsLimit
            prefs[Keys.CAPITALIZE_LABELS] = updated.capitalizeLabels
            prefs[Keys.NORMALIZE_VOLUME] = updated.normalizeVolume
            prefs[Keys.SKIP_SILENCE] = updated.skipSilence
            prefs[Keys.AUTO_MIX] = updated.autoMix
            prefs[Keys.SPONSOR_BLOCK] = updated.sponsorBlockEnabled
            prefs[Keys.REPORT_HISTORY] = updated.reportPlaybackHistory
            prefs[Keys.CACHE_LIMIT_MB] = updated.cacheLimitMb
            prefs[Keys.INCLUDE_MVS] = updated.includeMusicVideos
            prefs[Keys.PLAYBACK_SPEED] = updated.playbackSpeed
            prefs[Keys.PLAYBACK_PITCH] = updated.playbackPitch
            prefs[Keys.SHOW_THUMBNAILS] = updated.showTrackThumbnails
            val normalizedRegion = ContentRegions.normalize(updated.contentRegion)
            prefs[Keys.CONTENT_REGION] = normalizedRegion
            cachedRegionCode = normalizedRegion
        }
    }
}
