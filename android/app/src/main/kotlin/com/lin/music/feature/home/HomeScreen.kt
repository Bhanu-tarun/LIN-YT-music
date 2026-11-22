package com.lin.music.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.lin.music.core.model.HomeItem
import com.lin.music.core.model.HomeSection
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.SpeedDialEntry
import com.lin.music.core.model.Track
import com.lin.music.core.model.displayTitle
import com.lin.music.core.model.thumbnailUrl
import com.lin.music.ui.HomeUiState

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    currentVideoId: String?,
    accentColor: Color,
    downloadMap: Map<String, Int>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSeeAllPlaylists: () -> Unit,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onStartMixForTrack: (Track) -> Unit,
    onTogglePinSpeedDial: (HomeItem) -> Unit,
    onTrackMore: (Track) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (uiState.error != null && uiState.isEmpty) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(uiState.error, style = LinTextStyles.subtitle, color = linColors.danger)
                    LinInvertedButton(text = "retry", onClick = onRefresh)
                }
            }
        }

        if (uiState.isLoading && uiState.isEmpty) {
            item {
                Text(linLabel("loading your youtube music..."), style = LinTextStyles.navItem, color = linColors.textSecondary)
            }
        }

        // 1. Speed Dial (YTM-style 3x3 swipeable pages, mixed items, pinning & tap-to-mix)
        if (uiState.speedDialItems.isNotEmpty()) {
            item(key = "home_speed_dial") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(linLabel("speed dial"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                    SpeedDialPager3x3(
                        items = uiState.speedDialItems,
                        currentVideoId = currentVideoId,
                        accentColor = accentColor,
                        onStartMixForTrack = onStartMixForTrack,
                        onTrackMore = onTrackMore,
                        onAlbumClick = onAlbumClick,
                        onPlaylistClick = onPlaylistClick,
                        onArtistClick = onArtistClick,
                        onTogglePin = onTogglePinSpeedDial
                    )
                }
            }
        }

        // 2. Your Playlists from YouTube Music (shown BEFORE community playlists)
        if (uiState.userPlaylists.isNotEmpty()) {
            item(key = "home_your_playlists") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(linLabel("your playlists"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                        Text(
                            text = linLabel("see all"),
                            style = LinTextStyles.subtitle,
                            color = linColors.textSecondary,
                            modifier = Modifier.clickable(onClick = onSeeAllPlaylists).padding(4.dp)
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(uiState.userPlaylists, key = { idx, pl -> "upl_${idx}_${pl.id}" }) { _, pl ->
                            HomeSquareCard(
                                title = pl.title,
                                subtitle = pl.subtitle ?: pl.trackCount?.let { "$it tracks" }.orEmpty(),
                                imageUrl = pl.thumbnailUrl,
                                isCircle = false,
                                cardWidthDp = 134,
                                onClick = { onPlaylistClick(pl.browseId) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Quick Picks (max 4x4 grid)
        if (uiState.quickPicks.isNotEmpty()) {
            item(key = "home_quick_picks") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(linLabel("quick picks"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                        LinOutlinedButton(
                            text = "play all",
                            onClick = { onPlayTracks(uiState.quickPicks, 0, "quick picks") }
                        )
                    }
                    CompactTrackGrid4x4(
                        tracks = uiState.quickPicks,
                        currentVideoId = currentVideoId,
                        accentColor = accentColor,
                        downloadMap = downloadMap,
                        onPlayAtIndex = { idx -> onPlayTracks(uiState.quickPicks, idx, "quick picks") },
                        onTrackMore = onTrackMore
                    )
                }
            }
        }

        // 4. YouTube's Mixes & Personalized Recommendations for You
        uiState.personalMixSections.forEachIndexed { idx, section ->
            item(key = "personal_${idx}_${section.title}") {
                HomeCarouselSectionBlock(
                    section = section,
                    onPlayTracks = onPlayTracks,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick
                )
            }
        }

        // 5. Taste-Matched Community Playlists (at the end, no trending/charts)
        uiState.communityTasteSections.forEachIndexed { idx, section ->
            item(key = "community_${idx}_${section.title}") {
                HomeCarouselSectionBlock(
                    section = section,
                    onPlayTracks = onPlayTracks,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick
                )
            }
        }

        if (!uiState.isEmpty) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinOutlinedButton(text = "refresh", onClick = onRefresh)
                    LinOutlinedButton(text = "load more", onClick = onLoadMore)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialPager3x3(
    items: List<SpeedDialEntry>,
    currentVideoId: String?,
    accentColor: Color,
    onStartMixForTrack: (Track) -> Unit,
    onTrackMore: (Track) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onTogglePin: (HomeItem) -> Unit
) {
    val linColors = LinTheme.colors
    val haptic = LocalHapticFeedback.current
    val pages = remember(items) { items.take(27).chunked(9) }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val scrimBrush = remember {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 0,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { pageIdx ->
            val pageEntries = pages.getOrNull(pageIdx).orEmpty()
            val rows = pageEntries.chunked(3)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { rowEntries ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until 3) {
                            val entry = rowEntries.getOrNull(col)
                            if (entry == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                val item = entry.item
                                val isArtist = item is HomeItem.ArtistItem
                                val isPlaying = (item as? HomeItem.SongItem)?.track?.videoId == currentVideoId &&
                                    currentVideoId != null
                                val shape = if (isArtist) CircleShape else LinSharpShape
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(shape)
                                        .background(linColors.glassBorder)
                                        .border(1.dp, if (isPlaying) accentColor else linColors.glassBorder, shape)
                                        .combinedClickable(
                                            onClick = {
                                                when (item) {
                                                    is HomeItem.SongItem -> onStartMixForTrack(item.track)
                                                    is HomeItem.AlbumItem -> onAlbumClick(item.album.browseId)
                                                    is HomeItem.PlaylistItem -> onPlaylistClick(item.playlist.browseId)
                                                    is HomeItem.ArtistItem -> onArtistClick(item.artist.browseId)
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                when (item) {
                                                    is HomeItem.SongItem -> onTrackMore(item.track)
                                                    else -> onTogglePin(item)
                                                }
                                            }
                                        )
                                ) {
                                    val thumb = item.thumbnailUrl()
                                    if (!thumb.isNullOrBlank()) {
                                        AsyncImage(
                                            model = thumb,
                                            contentDescription = item.displayTitle(),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(scrimBrush)
                                            .padding(horizontal = 6.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = item.displayTitle(),
                                            style = LinTextStyles.subtitle.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (entry.isPinned) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .background(Color.Black.copy(alpha = 0.65f))
                                                .padding(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = LinIcons.Pin,
                                                contentDescription = "Pinned",
                                                tint = accentColor,
                                                modifier = Modifier.size(10.dp)
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

        if (pages.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { idx ->
                    val selected = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (selected) 18.dp else 6.dp)
                            .height(3.dp)
                            .background(if (selected) accentColor else linColors.glassBorder)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactTrackGrid4x4(
    tracks: List<Track>,
    currentVideoId: String?,
    accentColor: Color,
    downloadMap: Map<String, Int>,
    onPlayAtIndex: (Int) -> Unit,
    onTrackMore: (Track) -> Unit
) {
    val linColors = LinTheme.colors
    val haptic = LocalHapticFeedback.current
    val columns = remember(tracks) { tracks.take(16).chunked(4) }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(
            count = columns.size,
            key = { colIdx -> "col_${colIdx}_${columns[colIdx].firstOrNull()?.videoId.orEmpty()}" }
        ) { colIdx ->
            val colTracks = columns[colIdx]
            Column(
                modifier = Modifier.width(304.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                colTracks.forEachIndexed { rowIdx, track ->
                    val globalIdx = colIdx * 4 + rowIdx
                    val isCurrent = track.videoId == currentVideoId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onPlayAtIndex(globalIdx) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTrackMore(track)
                                }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(LinSharpShape)
                                .background(linColors.glassBorder)
                        ) {
                            if (!track.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = track.thumbnailUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = (if (isCurrent) LinTextStyles.navItemActive else LinTextStyles.navItem)
                                    .copy(fontSize = 15.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal),
                                color = if (isCurrent) accentColor else linColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (downloadMap[track.videoId] == 2) {
                                    Icon(LinIcons.Download, null, tint = accentColor, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = track.artistDisplay,
                                    style = LinTextStyles.subtitle.copy(fontSize = 12.sp),
                                    color = linColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onTrackMore(track) }, modifier = Modifier.size(30.dp)) {
                            Icon(LinIcons.MoreVertical, "Options", tint = linColors.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCarouselSectionBlock(
    section: HomeSection,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    val linColors = LinTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = linLabel(section.title.lowercase()),
            style = LinTextStyles.sectionHeading,
            color = linColors.textPrimary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(
                items = section.items,
                key = { idx, item ->
                    when (item) {
                        is HomeItem.SongItem -> "s_${idx}_${item.track.videoId}"
                        is HomeItem.AlbumItem -> "al_${idx}_${item.album.browseId}"
                        is HomeItem.PlaylistItem -> "pl_${idx}_${item.playlist.browseId}"
                        is HomeItem.ArtistItem -> "ar_${idx}_${item.artist.browseId}"
                    }
                }
            ) { _, item ->
                when (item) {
                    is HomeItem.SongItem -> {
                        HomeSquareCard(
                            title = item.track.title,
                            subtitle = item.track.artistDisplay,
                            imageUrl = item.track.thumbnailUrl,
                            isCircle = false,
                            onClick = { onPlayTracks(listOf(item.track), 0, section.title.lowercase()) }
                        )
                    }
                    is HomeItem.AlbumItem -> {
                        HomeSquareCard(
                            title = item.album.title,
                            subtitle = item.album.artistDisplay.ifBlank { if (item.album.isEpOrSingle) "EP" else "Album" },
                            imageUrl = item.album.thumbnailUrl,
                            isCircle = false,
                            onClick = { onAlbumClick(item.album.browseId) }
                        )
                    }
                    is HomeItem.PlaylistItem -> {
                        HomeSquareCard(
                            title = item.playlist.title,
                            subtitle = item.playlist.subtitle.orEmpty(),
                            imageUrl = item.playlist.thumbnailUrl,
                            isCircle = false,
                            onClick = { onPlaylistClick(item.playlist.browseId) }
                        )
                    }
                    is HomeItem.ArtistItem -> {
                        HomeSquareCard(
                            title = item.artist.name,
                            subtitle = item.artist.subscriberCount.orEmpty(),
                            imageUrl = item.artist.thumbnailUrl,
                            isCircle = true,
                            onClick = { onArtistClick(item.artist.browseId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSquareCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isCircle: Boolean,
    cardWidthDp: Int = 144,
    onClick: () -> Unit
) {
    val linColors = LinTheme.colors
    Column(
        modifier = Modifier
            .width(cardWidthDp.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(if (isCircle) CircleShape else LinSharpShape)
                .background(linColors.glassBorder)
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = LinTextStyles.navItemActive,
            color = linColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = LinTextStyles.subtitle,
                color = linColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
