package com.lin.music.feature.settings

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lin.music.core.designsystem.components.LinInvertedButton
import com.lin.music.core.designsystem.components.LinOutlinedButton
import com.lin.music.core.designsystem.components.LinSquareToggle
import com.lin.music.core.designsystem.components.LinTagPill
import com.lin.music.core.designsystem.icons.LinIcons
import com.lin.music.core.designsystem.sheets.LinUnderlineTextField
import com.lin.music.core.designsystem.theme.APP_BRAND_NAME
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.formatStorageBytes
import com.lin.music.core.designsystem.theme.linBrandText
import com.lin.music.core.designsystem.theme.linLabel
import com.lin.music.core.model.AccountSession
import com.lin.music.core.model.AmbientMode
import com.lin.music.core.model.AmbientReactivity
import com.lin.music.core.model.AudioQuality
import com.lin.music.core.model.ContentRegionOption
import com.lin.music.core.model.ContentRegions
import com.lin.music.core.model.UserPreferences
import com.lin.music.util.ShareHelper
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    session: AccountSession?,
    preferences: UserPreferences,
    accentColor: Color,
    audioSessionId: Int,
    downloadedCount: Int,
    downloadedSizeBytes: Long,
    cacheSizeBytes: Long,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onLogout: () -> Unit,
    onSetContentRegion: (String) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetCapitalizeLabels: (Boolean) -> Unit,
    onSetAmbientMode: (AmbientMode) -> Unit,
    onSetAmbientReactivity: (AmbientReactivity) -> Unit,
    onSetAudioQuality: (AudioQuality) -> Unit,
    onSetDownloadQuality: (AudioQuality) -> Unit,
    onSetDownloadWifiOnly: (Boolean) -> Unit,
    onSetSmartDownloadsEnabled: (Boolean) -> Unit,
    onSetSmartDownloadsLimit: (Int) -> Unit,
    onSetRecentlyPlayedDownloadsEnabled: (Boolean) -> Unit,
    onSetRecentlyPlayedDownloadsLimit: (Int) -> Unit,
    onSyncSmartDownloadsNow: () -> Unit,
    onSyncRecentlyPlayedDownloadsNow: () -> Unit,
    onClearStreamCache: suspend () -> Long,
    onDeleteAllDownloads: () -> Unit,
    onSetNormalizeVolume: (Boolean) -> Unit,
    onSetSkipSilence: (Boolean) -> Unit,
    onSetAutoMix: (Boolean) -> Unit,
    onSetSponsorBlock: (Boolean) -> Unit,
    onSetReportHistory: (Boolean) -> Unit,
    onSetShowThumbnails: (Boolean) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onExportBackup: suspend () -> String,
    onImportBackup: suspend (String) -> Result<Unit>,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importJsonInput by remember { mutableStateOf("") }
    var showImportBox by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var regionSearch by remember { mutableStateOf("") }
    var displayedCacheBytes by remember(cacheSizeBytes) { mutableLongStateOf(cacheSizeBytes) }
    var displayedDownloadBytes by remember(downloadedSizeBytes) { mutableLongStateOf(downloadedSizeBytes) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(LinIcons.ArrowLeft, "Back", tint = linColors.textSecondary, modifier = Modifier.size(24.dp))
                }
                Text(linLabel("settings"), style = LinTextStyles.drilldownTitle, color = linColors.textPrimary)
            }
        }

        // Account
        item {
            Column(
                modifier = Modifier.fillMaxWidth().border(1.dp, linColors.glassBorder).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(linLabel("youtube music account"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                Text(
                    text = session?.accountName?.let { "$it (${session.accountHandle.orEmpty()})" }
                        ?: linLabel("signed in via session cookies"),
                    style = LinTextStyles.subtitle,
                    color = linColors.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinInvertedButton("sync library", onClick = onSyncNow)
                    LinOutlinedButton("sign out", onClick = onLogout)
                }
            }
        }

        // Content Location
        item {
            val activeCode = remember(preferences.contentRegion) {
                ContentRegions.normalize(preferences.contentRegion)
            }
            val activeCountryName = remember(activeCode) {
                ContentRegions.allCountries()
                    .firstOrNull { it.code == activeCode }
                    ?.name
                    ?: activeCode
            }
            val chevronRotation by animateFloatAsState(
                targetValue = if (showCountryPicker) 180f else 0f,
                label = "regionMenuChevron"
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(linLabel("content location"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                Text(
                    text = linLabel("changes youtube music catalog, charts & stream region (strict ip geo-blocks may still need a vpn)"),
                    style = LinTextStyles.subtitle,
                    color = linColors.textSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(linColors.queueItemBg)
                        .border(
                            1.dp,
                            if (showCountryPicker) accentColor else linColors.textPrimary.copy(alpha = 0.28f)
                        )
                        .clickable { showCountryPicker = !showCountryPicker }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = linLabel("active region"),
                            style = LinTextStyles.tagPill,
                            color = linColors.textSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = linLabel(activeCountryName.lowercase()),
                                style = LinTextStyles.navItemActive,
                                color = linColors.textPrimary
                            )
                            Text(
                                text = activeCode,
                                style = LinTextStyles.tagPill.copy(fontWeight = FontWeight.SemiBold),
                                color = accentColor
                            )
                        }
                    }
                    Icon(
                        imageVector = LinIcons.ChevronDown,
                        contentDescription = if (showCountryPicker) "Collapse region selector" else "Expand region selector",
                        tint = if (showCountryPicker) accentColor else linColors.textSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation)
                    )
                }

                AnimatedVisibility(visible = showCountryPicker) {
                    val allCountries = remember { ContentRegions.allCountries() }
                    val presetCodes = remember { ContentRegions.quickPresets.map { it.code }.toSet() }
                    val remainingCountries = remember(allCountries, presetCodes) {
                        allCountries.filterNot { it.code in presetCodes }
                    }
                    val query = regionSearch.trim()
                    val searchResults = remember(query) {
                        if (query.isBlank()) emptyList() else ContentRegions.filterCountries(query)
                    }
                    val deviceCode = remember { ContentRegions.deviceRegionCode() }
                    val deviceLabel = remember(deviceCode) {
                        ContentRegions.displayNameFor(deviceCode).lowercase()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(linColors.background)
                            .border(1.dp, linColors.glassBorder)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            LinUnderlineTextField(
                                value = regionSearch,
                                onValueChange = { regionSearch = it },
                                placeholder = "search country or 2-letter iso code (e.g. japan, jp)..."
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetContentRegion(deviceCode)
                                    showCountryPicker = false
                                    regionSearch = ""
                                    onToast("location set to $deviceLabel")
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = linLabel("use device region ($deviceLabel)"),
                                style = LinTextStyles.subtitle,
                                color = accentColor
                            )
                            Text(
                                text = deviceCode,
                                style = LinTextStyles.tagPill.copy(fontWeight = FontWeight.SemiBold),
                                color = accentColor
                            )
                        }
                        HorizontalDivider(color = linColors.glassBorder)

                        val selectRegion: (ContentRegionOption) -> Unit = { option ->
                            onSetContentRegion(option.code)
                            showCountryPicker = false
                            regionSearch = ""
                            onToast("location set to ${option.name.lowercase()} (${option.code})")
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                        ) {
                            if (query.isBlank()) {
                                item(key = "hdr_popular") {
                                    Text(
                                        text = linLabel("popular"),
                                        style = LinTextStyles.tagPill,
                                        color = linColors.textSecondary,
                                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp)
                                    )
                                }
                                items(ContentRegions.quickPresets, key = { "preset_${it.code}" }) { option ->
                                    RegionMenuRow(
                                        option = option,
                                        selected = activeCode == option.code,
                                        accentColor = accentColor,
                                        onClick = { selectRegion(option) }
                                    )
                                }

                                item(key = "hdr_all") {
                                    HorizontalDivider(color = linColors.glassBorder)
                                    Text(
                                        text = linLabel("all countries"),
                                        style = LinTextStyles.tagPill,
                                        color = linColors.textSecondary,
                                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp)
                                    )
                                }
                                items(remainingCountries, key = { "all_${it.code}" }) { option ->
                                    RegionMenuRow(
                                        option = option,
                                        selected = activeCode == option.code,
                                        accentColor = accentColor,
                                        onClick = { selectRegion(option) }
                                    )
                                }
                            } else {
                                if (searchResults.isEmpty()) {
                                    item(key = "no_matches") {
                                        Text(
                                            text = linLabel("no matching countries"),
                                            style = LinTextStyles.subtitle,
                                            color = linColors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                        )
                                    }
                                } else {
                                    items(searchResults, key = { "search_${it.code}" }) { option ->
                                        RegionMenuRow(
                                            option = option,
                                            selected = activeCode == option.code,
                                            accentColor = accentColor,
                                            onClick = { selectRegion(option) }
                                        )
                                    }
                                }

                                val exactCodeMatch = ContentRegions.isValidCode(query) &&
                                    searchResults.any { it.code.equals(query, ignoreCase = true) }
                                if (ContentRegions.isValidCode(query) && !exactCodeMatch) {
                                    val customCode = ContentRegions.normalize(query)
                                    item(key = "custom_$customCode") {
                                        HorizontalDivider(color = linColors.glassBorder)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSetContentRegion(customCode)
                                                    showCountryPicker = false
                                                    regionSearch = ""
                                                    onToast("location set to ${ContentRegions.displayNameFor(customCode).lowercase()}")
                                                }
                                                .padding(horizontal = 14.dp, vertical = 11.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = linLabel("use custom region code $customCode"),
                                                style = LinTextStyles.subtitle,
                                                color = accentColor
                                            )
                                            Text(
                                                text = customCode,
                                                style = LinTextStyles.tagPill,
                                                color = accentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { HorizontalDivider(color = linColors.glassBorder) }

        // Visuals & Theme
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = linBrandText(
                        text = "${linLabel("visuals")} · $APP_BRAND_NAME",
                        accentColor = accentColor,
                        baseColor = linColors.textPrimary
                    ),
                    style = LinTextStyles.sectionHeading
                )
                SettingToggleRow(linLabel("dark theme (oled black)"), preferences.isDarkTheme, onSetDarkTheme)
                SettingToggleRow(linLabel("capitalize first letters"), preferences.capitalizeLabels, onSetCapitalizeLabels)
                SettingToggleRow(linLabel("show track thumbnails in lists"), preferences.showTrackThumbnails, onSetShowThumbnails)

                Text(linLabel("ambient canvas"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmbientMode.entries.forEach { mode ->
                        LinTagPill(mode.label, active = preferences.ambientMode == mode, onClick = { onSetAmbientMode(mode) })
                    }
                }
                if (preferences.ambientMode == AmbientMode.AUDIO_REACTIVE) {
                    Text(linLabel("background reactiveness"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmbientReactivity.entries.forEach { level ->
                            LinTagPill(
                                text = level.label,
                                active = preferences.ambientReactivity == level,
                                onClick = { onSetAmbientReactivity(level) }
                            )
                        }
                    }
                }
            }
        }

        item { HorizontalDivider(color = linColors.glassBorder) }

        // Downloads & Caching
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(linLabel("downloads & caching"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)

                Text(linLabel("download quality"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.entries.forEach { q ->
                        LinTagPill(q.label, active = preferences.downloadQuality == q, onClick = { onSetDownloadQuality(q) })
                    }
                }

                SettingToggleRow(linLabel("download over wi-fi only"), preferences.downloadWifiOnly, onSetDownloadWifiOnly)

                SettingToggleRow(linLabel("smart downloads"), preferences.smartDownloadsEnabled, onSetSmartDownloadsEnabled)
                Text(
                    text = linLabel("daily wi-fi refresh · sticky favorites & frequent plays up to cap"),
                    style = LinTextStyles.subtitle,
                    color = linColors.textSecondary
                )
                if (preferences.smartDownloadsEnabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(20, 50, 100, 200).forEach { cap ->
                            LinTagPill(
                                text = "$cap songs",
                                active = preferences.smartDownloadsLimit == cap,
                                onClick = { onSetSmartDownloadsLimit(cap) }
                            )
                        }
                    }
                    LinOutlinedButton(
                        text = "sync smart downloads",
                        onClick = {
                            onSyncSmartDownloadsNow()
                            onToast("syncing smart downloads")
                        }
                    )
                }

                SettingToggleRow(
                    label = linLabel("recently played downloads"),
                    checked = preferences.recentlyPlayedDownloadsEnabled,
                    onCheckedChange = onSetRecentlyPlayedDownloadsEnabled
                )
                Text(
                    text = linLabel("24h refresh · longest listened last 24h first up to cap"),
                    style = LinTextStyles.subtitle,
                    color = linColors.textSecondary
                )
                if (preferences.recentlyPlayedDownloadsEnabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30, 50).forEach { cap ->
                            LinTagPill(
                                text = "$cap songs",
                                active = preferences.recentlyPlayedDownloadsLimit == cap,
                                onClick = { onSetRecentlyPlayedDownloadsLimit(cap) }
                            )
                        }
                    }
                    LinOutlinedButton(
                        text = "sync recently played",
                        onClick = {
                            onSyncRecentlyPlayedDownloadsNow()
                            onToast("syncing recently played downloads")
                        }
                    )
                }

                val downloadsSizeText = formatStorageBytes(displayedDownloadBytes)
                val cacheSizeText = formatStorageBytes(displayedCacheBytes)
                val totalStorageBytes = displayedDownloadBytes + displayedCacheBytes

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, linColors.glassBorder)
                        .background(linColors.queueItemBg)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = linLabel("offline storage"),
                            style = LinTextStyles.subtitle.copy(fontWeight = FontWeight.SemiBold),
                            color = linColors.textPrimary
                        )
                        Text(
                            text = linLabel("total ${formatStorageBytes(totalStorageBytes)}"),
                            style = LinTextStyles.tagPill,
                            color = linColors.textSecondary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(linColors.progressTrack)
                    ) {
                        if (totalStorageBytes > 0L) {
                            val dlWeight = if (displayedDownloadBytes > 0L) {
                                (displayedDownloadBytes.toFloat() / totalStorageBytes.toFloat()).coerceAtLeast(0.03f)
                            } else 0f
                            val cacheWeight = if (displayedCacheBytes > 0L) {
                                (displayedCacheBytes.toFloat() / totalStorageBytes.toFloat()).coerceAtLeast(0.03f)
                            } else 0f
                            if (dlWeight > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(dlWeight)
                                        .height(4.dp)
                                        .background(accentColor)
                                )
                            }
                            if (cacheWeight > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(cacheWeight)
                                        .height(4.dp)
                                        .background(linColors.textPrimary.copy(alpha = 0.45f))
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(accentColor)
                                )
                                Text(
                                    text = linLabel("downloaded songs"),
                                    style = LinTextStyles.tagPill,
                                    color = linColors.textSecondary
                                )
                            }
                            Text(
                                text = linLabel(downloadsSizeText),
                                style = LinTextStyles.trackTitlePlaying,
                                color = linColors.textPrimary
                            )
                            Text(
                                text = linLabel("$downloadedCount ${if (downloadedCount == 1) "song" else "songs"} saved"),
                                style = LinTextStyles.tagPill,
                                color = linColors.textSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(52.dp)
                                .background(linColors.glassBorder)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(linColors.textPrimary.copy(alpha = 0.45f))
                                )
                                Text(
                                    text = linLabel("stream cache"),
                                    style = LinTextStyles.tagPill,
                                    color = linColors.textSecondary
                                )
                            }
                            Text(
                                text = linLabel(cacheSizeText),
                                style = LinTextStyles.trackTitlePlaying,
                                color = linColors.textPrimary
                            )
                            Text(
                                text = linLabel("next 2 queued tracks"),
                                style = LinTextStyles.tagPill,
                                color = linColors.textSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LinOutlinedButton(
                            text = "delete downloads",
                            onClick = {
                                displayedDownloadBytes = 0L
                                onDeleteAllDownloads()
                                onToast("deleted all downloads")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        LinOutlinedButton(
                            text = "clear stream cache",
                            onClick = {
                                scope.launch {
                                    val freed = onClearStreamCache()
                                    displayedCacheBytes = (displayedCacheBytes - freed).coerceAtLeast(0L)
                                    onToast("cleared ${formatStorageBytes(freed)} stream cache")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item { HorizontalDivider(color = linColors.glassBorder) }

        // Audio & Playback
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(linLabel("audio & playback"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)

                Text(linLabel("streaming quality"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.entries.forEach { q ->
                        LinTagPill(q.label, active = preferences.audioQuality == q, onClick = { onSetAudioQuality(q) })
                    }
                }

                Spacer(Modifier.height(4.dp))
                val isNormalSpeed = abs(preferences.playbackSpeed - 1.0f) < 0.01f
                Text(
                    text = linLabel("playback speed (${if (isNormalSpeed) "1.0x · normal" else "${preferences.playbackSpeed}x"})"),
                    style = LinTextStyles.subtitle,
                    color = linColors.textSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.8f, 1.0f, 1.15f, 1.25f, 1.5f).forEach { spd ->
                        val active = abs(preferences.playbackSpeed - spd) < 0.01f
                        val label = if (abs(spd - 1.0f) < 0.01f) "1.0x (normal)" else "${spd}x"
                        LinTagPill(label, active = active, onClick = { onSetPlaybackSpeed(spd) })
                    }
                }

                SettingToggleRow(linLabel("loudness normalization"), preferences.normalizeVolume, onSetNormalizeVolume)
                SettingToggleRow(linLabel("skip silence"), preferences.skipSilence, onSetSkipSilence)
                SettingToggleRow(linLabel("automix continuation"), preferences.autoMix, onSetAutoMix)
                SettingToggleRow(linLabel("sponsorblock (skip non-music segments)"), preferences.sponsorBlockEnabled, onSetSponsorBlock)
                SettingToggleRow(linLabel("report playback history to ytm"), preferences.reportPlaybackHistory, onSetReportHistory)

                LinOutlinedButton(
                    text = "open system equalizer",
                    onClick = {
                        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                        runCatching { context.startActivity(intent) }.onFailure {
                            onToast("no system equalizer app found")
                        }
                    }
                )
            }
        }

        item { HorizontalDivider(color = linColors.glassBorder) }

        // Gestures Reference
        item {
            Column(
                modifier = Modifier.fillMaxWidth().background(linColors.queueItemBg).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(linLabel("gestures (locked)"), style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                Text(linLabel("right swipe any track → play next"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                Text(linLabel("left swipe any track → add to queue"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                Text(linLabel("tap ⋮ or long-press → library, playlist, album, artist, mix, share"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
            }
        }

        // Backup & Restore
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(linLabel("backup & #tags"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinOutlinedButton(
                        text = "export json",
                        onClick = {
                            scope.launch {
                                val payload = onExportBackup()
                                ShareHelper.shareText(context, payload, "LIN Backup")
                            }
                        }
                    )
                    LinOutlinedButton(
                        text = if (showImportBox) "cancel import" else "import json",
                        onClick = { showImportBox = !showImportBox }
                    )
                }

                if (showImportBox) {
                    LinUnderlineTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        placeholder = "paste exported JSON here..."
                    )
                    LinInvertedButton(
                        text = "restore backup",
                        enabled = importJsonInput.isNotBlank(),
                        onClick = {
                            scope.launch {
                                onImportBackup(importJsonInput)
                                    .onSuccess {
                                        onToast("backup restored")
                                        importJsonInput = ""
                                        showImportBox = false
                                    }
                                    .onFailure { onToast("invalid backup json") }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionMenuRow(
    option: ContentRegionOption,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val linColors = LinTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = linLabel(option.name.lowercase()),
            style = if (selected) LinTextStyles.navItemActive else LinTextStyles.subtitle,
            color = if (selected) accentColor else linColors.textPrimary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.code,
                style = LinTextStyles.tagPill,
                color = if (selected) accentColor else linColors.textSecondary
            )
            if (selected) {
                Icon(
                    imageVector = LinIcons.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val linColors = LinTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = LinTextStyles.navItem, color = linColors.textPrimary, modifier = Modifier.weight(1f))
        LinSquareToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}
