package com.lin.music.feature.stats

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin.music.core.designsystem.components.LinInvertedButton
import com.lin.music.core.designsystem.components.LinOutlinedButton
import com.lin.music.core.designsystem.icons.LinIcons
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.linLabel
import com.lin.music.core.model.ListeningStatsSummary
import com.lin.music.core.model.StatsRange
import com.lin.music.core.model.Track

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    summary: ListeningStatsSummary,
    selectedRange: StatsRange,
    accentColor: Color,
    onSelectRange: (StatsRange) -> Unit,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onGoToArtist: (String) -> Unit,
    onGoToAlbum: (String) -> Unit,
    onShareCard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val topTracksAsList = summary.topTracks.map { it.track }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(LinIcons.ArrowLeft, "Back", tint = linColors.textSecondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(linLabel("lin stats"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                }
                LinOutlinedButton(
                    text = "share card",
                    enabled = summary.totalPlays > 0,
                    onClick = onShareCard
                )
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsRange.entries.forEach { range ->
                    if (range == selectedRange) {
                        LinInvertedButton(text = range.label, onClick = { onSelectRange(range) })
                    } else {
                        LinOutlinedButton(text = range.label, onClick = { onSelectRange(range) })
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(linColors.glassBg)
                    .border(1.dp, linColors.glassBorder, LinSharpShape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = formatTotalHoursMinutes(summary.totalListenedMs),
                    style = LinTextStyles.drilldownTitle.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold),
                    color = linColors.textPrimary
                )
                Text(
                    text = linLabel("listening time · ${selectedRange.label.lowercase()}"),
                    style = LinTextStyles.subtitle,
                    color = linColors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCell(
                        label = "plays",
                        value = summary.totalPlays.toString(),
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    )
                    StatCell(
                        label = "artists",
                        value = summary.uniqueArtists.toString(),
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    )
                    StatCell(
                        label = "streak",
                        value = "${summary.activeStreakDays}d",
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (summary.totalPlays == 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, linColors.glassBorder, LinSharpShape)
                        .padding(20.dp)
                ) {
                    Text(
                        text = linLabel("no listening sessions recorded in this window yet — plays longer than 5 seconds appear here privately on-device."),
                        style = LinTextStyles.subtitle,
                        color = linColors.textSecondary
                    )
                }
            }
            return@LazyColumn
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, linColors.glassBorder, LinSharpShape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(linLabel("hourly listening clock"), style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                    summary.peakHourOfDay?.let { hour ->
                        Text(
                            text = linLabel("peak ${formatHour(hour)}"),
                            style = LinTextStyles.subtitle,
                            color = accentColor
                        )
                    }
                }
                val maxHourMs = (summary.hourlyMs.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    summary.hourlyMs.forEachIndexed { hour, ms ->
                        val frac = (ms / maxHourMs).coerceIn(0.06f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(frac)
                                .background(
                                    if (hour == summary.peakHourOfDay) accentColor
                                    else linColors.textPrimary.copy(alpha = if (ms > 0L) 0.45f else 0.12f)
                                )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("00h", "06h", "12h", "18h", "23h").forEach { tick ->
                        Text(tick, style = LinTextStyles.subtitle.copy(fontSize = 11.sp), color = linColors.textSecondary)
                    }
                }
            }
        }

        if (summary.topTracks.isNotEmpty()) {
            item {
                Text(linLabel("top tracks"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
            }
            itemsIndexed(summary.topTracks, key = { idx, item -> "tr_${idx}_${item.track.videoId}" }) { idx, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, linColors.glassBorder, LinSharpShape)
                        .clickable { onPlayTracks(topTracksAsList, idx, "top tracks") }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%02d", idx + 1),
                        style = LinTextStyles.navItemActive,
                        color = if (idx == 0) accentColor else linColors.textSecondary,
                        modifier = Modifier.width(28.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(LinSharpShape)
                            .background(linColors.glassBorder)
                    ) {
                        if (!item.track.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.track.thumbnailUrl,
                                contentDescription = item.track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.track.title,
                            style = LinTextStyles.navItemActive,
                            color = linColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.track.artistDisplay,
                            style = LinTextStyles.subtitle,
                            color = linColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${item.playCount}x", style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                        Text(
                            formatTotalHoursMinutes(item.totalListenedMs),
                            style = LinTextStyles.subtitle.copy(fontSize = 11.sp),
                            color = linColors.textSecondary
                        )
                    }
                }
            }
        }

        if (summary.topArtists.isNotEmpty()) {
            item {
                Text(linLabel("top artists"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
            }
            itemsIndexed(summary.topArtists, key = { idx, item -> "ar_${idx}_${item.name}" }) { idx, artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, linColors.glassBorder, LinSharpShape)
                        .clickable(enabled = !artist.artistId.isNullOrBlank()) {
                            artist.artistId?.let(onGoToArtist)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%02d", idx + 1),
                        style = LinTextStyles.navItemActive,
                        color = if (idx == 0) accentColor else linColors.textSecondary,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = artist.name,
                        style = LinTextStyles.navItemActive,
                        color = linColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${artist.playCount} plays · ${formatTotalHoursMinutes(artist.totalListenedMs)}",
                        style = LinTextStyles.subtitle,
                        color = linColors.textSecondary
                    )
                }
            }
        }

        if (summary.topAlbums.isNotEmpty()) {
            item {
                Text(linLabel("top albums"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
            }
            itemsIndexed(summary.topAlbums, key = { idx, item -> "al_${idx}_${item.title}" }) { idx, album ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, linColors.glassBorder, LinSharpShape)
                        .clickable(enabled = !album.albumId.isNullOrBlank()) {
                            album.albumId?.let(onGoToAlbum)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%02d", idx + 1),
                        style = LinTextStyles.navItemActive,
                        color = if (idx == 0) accentColor else linColors.textSecondary,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = album.title,
                        style = LinTextStyles.navItemActive,
                        color = linColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${album.playCount}x · ${formatTotalHoursMinutes(album.totalListenedMs)}",
                        style = LinTextStyles.subtitle,
                        color = linColors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    Column(
        modifier = modifier
            .border(1.dp, linColors.glassBorder, LinSharpShape)
            .padding(10.dp)
    ) {
        Text(value, style = LinTextStyles.sectionHeading, color = accentColor)
        Text(linLabel(label), style = LinTextStyles.subtitle.copy(fontSize = 11.sp), color = linColors.textSecondary)
    }
}

private fun formatTotalHoursMinutes(ms: Long): String {
    val totalMin = (ms / 60_000L).toInt()
    val hours = totalMin / 60
    val mins = totalMin % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

private fun formatHour(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}
