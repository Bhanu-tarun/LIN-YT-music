package com.lin.music.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.lin.music.core.designsystem.components.LinInvertedButton
import com.lin.music.core.designsystem.components.LinLazyColumnScrollbar
import com.lin.music.core.designsystem.components.LinLazyGridScrollbar
import com.lin.music.core.designsystem.components.LinOutlinedButton
import com.lin.music.core.designsystem.components.LinTagPill
import com.lin.music.core.designsystem.components.LinTrackArtHoverOverlay
import com.lin.music.core.designsystem.components.PreloadPreviewArtworks
import com.lin.music.core.designsystem.components.SwipeableTrackRow
import com.lin.music.core.designsystem.sheets.PlaylistEditorDialog
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.linLabel
import com.lin.music.core.model.AlbumSummary
import com.lin.music.core.model.ArtistSummary
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.Track
import com.lin.music.ui.LibrarySubTab
import com.lin.music.ui.LocalDownloadPlaylistKind
import com.lin.music.ui.SongsSubFilter

@Composable
fun LibraryTabScreen(
    subTab: LibrarySubTab,
    onSubTabChange: (LibrarySubTab) -> Unit,
    songsSubFilter: SongsSubFilter,
    onSongsSubFilterChange: (SongsSubFilter) -> Unit,
    libraryTracks: List<Track>,
    likedTracks: List<Track>,
    downloadedTracks: List<Track>,
    smartDownloadedTracks: List<Track>,
    recentlyPlayedDownloadedTracks: List<Track>,
    smartDownloadsEnabled: Boolean,
    recentlyPlayedDownloadsEnabled: Boolean,
    historyTracks: List<Track>,
    artists: List<ArtistSummary>,
    albums: List<AlbumSummary>,
    downloadedAlbums: List<AlbumSummary>,
    distinctTags: List<String>,
    activeTagFilter: String?,
    onTagClick: (String) -> Unit,
    onTagClear: () -> Unit,
    currentVideoId: String?,
    accentColor: Color,
    downloadMap: Map<String, Int>,
    showThumbnails: Boolean,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onSwipeRightPlayNext: (Track) -> Unit,
    onSwipeLeftAddToQueue: (Track) -> Unit,
    onTrackMore: (Track) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onOpenLocalPlaylist: (LocalDownloadPlaylistKind) -> Unit,
    onPreviewTrack: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LibrarySubTab.entries.forEach { tab ->
                val selected = subTab == tab
                val textColor by animateColorAsState(
                    targetValue = if (selected) linColors.textPrimary else linColors.textSecondary,
                    animationSpec = tween(200),
                    label = "libSubTabText"
                )
                val underlineColor by animateColorAsState(
                    targetValue = if (selected) linColors.textPrimary else Color.Transparent,
                    animationSpec = tween(200),
                    label = "libSubTabBar"
                )
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSubTabChange(tab) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = linLabel(tab.label),
                        style = if (selected) LinTextStyles.navItemActive else LinTextStyles.navItem,
                        color = textColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(underlineColor)
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (subTab) {
                LibrarySubTab.SONGS -> SongsScreen(
                    subFilter = songsSubFilter,
                    onSubFilterChange = onSongsSubFilterChange,
                    libraryTracks = libraryTracks,
                    likedTracks = likedTracks,
                    historyTracks = historyTracks,
                    distinctTags = distinctTags,
                    activeTagFilter = activeTagFilter,
                    onTagClick = onTagClick,
                    currentVideoId = currentVideoId,
                    accentColor = accentColor,
                    downloadMap = downloadMap,
                    showThumbnails = showThumbnails,
                    onPlayTracks = onPlayTracks,
                    onSwipeRightPlayNext = onSwipeRightPlayNext,
                    onSwipeLeftAddToQueue = onSwipeLeftAddToQueue,
                    onTrackMore = onTrackMore,
                    onPreviewTrack = onPreviewTrack
                )

                LibrarySubTab.ARTISTS -> ArtistsGridScreen(
                    artists = artists,
                    onArtistClick = onArtistClick
                )

                LibrarySubTab.ALBUMS -> AlbumsGridScreen(
                    albums = albums,
                    activeTagFilter = activeTagFilter,
                    onTagClear = onTagClear,
                    onAlbumClick = onAlbumClick
                )

                LibrarySubTab.DOWNLOADS -> DownloadsHubScreen(
                    downloadedTracks = downloadedTracks,
                    smartDownloadedTracks = smartDownloadedTracks,
                    recentlyPlayedDownloadedTracks = recentlyPlayedDownloadedTracks,
                    downloadedAlbums = downloadedAlbums,
                    smartDownloadsEnabled = smartDownloadsEnabled,
                    recentlyPlayedDownloadsEnabled = recentlyPlayedDownloadsEnabled,
                    currentVideoId = currentVideoId,
                    accentColor = accentColor,
                    downloadMap = downloadMap,
                    showThumbnails = showThumbnails,
                    onOpenLocalPlaylist = onOpenLocalPlaylist,
                    onAlbumClick = onAlbumClick,
                    onPlayTracks = onPlayTracks,
                    onSwipeRightPlayNext = onSwipeRightPlayNext,
                    onSwipeLeftAddToQueue = onSwipeLeftAddToQueue,
                    onTrackMore = onTrackMore,
                    onPreviewTrack = onPreviewTrack
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongsScreen(
    subFilter: SongsSubFilter,
    onSubFilterChange: (SongsSubFilter) -> Unit,
    libraryTracks: List<Track>,
    likedTracks: List<Track>,
    historyTracks: List<Track>,
    distinctTags: List<String>,
    activeTagFilter: String?,
    onTagClick: (String) -> Unit,
    currentVideoId: String?,
    accentColor: Color,
    downloadMap: Map<String, Int>,
    showThumbnails: Boolean,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onSwipeRightPlayNext: (Track) -> Unit,
    onSwipeLeftAddToQueue: (Track) -> Unit,
    onTrackMore: (Track) -> Unit,
    onPreviewTrack: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val baseList = when (subFilter) {
        SongsSubFilter.LIBRARY -> libraryTracks
        SongsSubFilter.LIKED -> likedTracks
        SongsSubFilter.HISTORY -> historyTracks
    }
    val filtered = remember(baseList, activeTagFilter) {
        if (activeTagFilter == null) baseList
        else baseList.filter { activeTagFilter in it.tags }
    }

    val listState = rememberLazyListState()
    var previewedKey by remember { mutableStateOf<String?>(null) }
    var previewArtUrl by remember { mutableStateOf<String?>(null) }
    var previewCenterYPx by remember { mutableFloatStateOf(0f) }
    var boxTopInRoot by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(1200f) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) previewedKey = null
    }
    LaunchedEffect(subFilter, activeTagFilter) {
        previewedKey = null
    }

    val firstVisibleIdx by remember(listState) {
        derivedStateOf { (listState.firstVisibleItemIndex - 1).coerceAtLeast(0) }
    }
    val preloadUrls = remember(filtered, firstVisibleIdx) {
        val end = (firstVisibleIdx + 8).coerceAtMost(filtered.size)
        (firstVisibleIdx until end).map { filtered[it].thumbnailUrl }
    }
    PreloadPreviewArtworks(preloadUrls, isScrolling = listState.isScrollInProgress)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height.toFloat() }
            .onGloballyPositioned { boxTopInRoot = it.positionInRoot().y }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .clickable { previewedKey = null }
                ) {
                    SongsSubFilter.entries.forEach { f ->
                        LinTagPill(
                            text = f.label,
                            active = subFilter == f,
                            onClick = { onSubFilterChange(f) }
                        )
                    }
                    if (activeTagFilter != null) {
                        LinTagPill(
                            text = "#$activeTagFilter ×",
                            active = true,
                            onClick = { onTagClick(activeTagFilter) }
                        )
                    } else {
                        distinctTags.take(6).forEach { tag ->
                            LinTagPill(
                                text = "#$tag",
                                active = false,
                                onClick = { onTagClick(tag) }
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = linLabel("no tracks found."),
                        style = LinTextStyles.sectionHeading,
                        color = linColors.textSecondary,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            } else {
                itemsIndexed(filtered, key = { idx, t -> "${subFilter.name}_${idx}_${t.videoId}" }) { idx, track ->
                    val rowKey = "${subFilter.name}_${idx}_${track.videoId}"
                    var rowCenterY by remember { mutableFloatStateOf(0f) }
                    SwipeableTrackRow(
                        track = track,
                        isCurrentTrack = track.videoId == currentVideoId,
                        accentColor = accentColor,
                        isDownloaded = downloadMap[track.videoId] == 2,
                        showThumbnail = showThumbnails,
                        activeTagFilter = activeTagFilter,
                        isPreviewed = previewedKey == rowKey,
                        onClick = {
                            if (previewedKey == rowKey) {
                                previewedKey = null
                            } else {
                                previewedKey = rowKey
                                previewArtUrl = track.thumbnailUrl
                                previewCenterYPx = rowCenterY - boxTopInRoot
                                onPreviewTrack(track)
                            }
                        },
                        onDoubleClick = {
                            previewedKey = null
                            onPlayTracks(filtered, idx, subFilter.label)
                        },
                        onSwipeRightPlayNext = { onSwipeRightPlayNext(track) },
                        onSwipeLeftAddToQueue = { onSwipeLeftAddToQueue(track) },
                        onMoreClick = { onTrackMore(track) },
                        onTagClick = onTagClick,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            rowCenterY = coords.positionInRoot().y + coords.size.height / 2f
                        }
                    )
                }
            }
        }

        // Hover art renders IN FRONT of track rows cleanly (no pointer consumption)
        LinTrackArtHoverOverlay(
            imageUrl = if (previewedKey != null) previewArtUrl else null,
            centerYPx = previewCenterYPx,
            viewportHeightPx = viewportHeightPx
        )

        LinLazyColumnScrollbar(
            listState = listState,
            accentColor = accentColor,
            modifier = Modifier.align(Alignment.CenterEnd),
            bottomPadding = 172.dp,
            labelProvider = { idx ->
                if (filtered.isNotEmpty()) {
                    "${(idx - 1).coerceIn(0, filtered.lastIndex) + 1} / ${filtered.size}"
                } else null
            }
        )
    }
}

@Composable
fun ArtistsGridScreen(
    artists: List<ArtistSummary>,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    if (artists.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp)) {
            Text(linLabel("no subscribed artists yet."), style = LinTextStyles.sectionHeading, color = linColors.textSecondary)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 180.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        itemsIndexed(artists, key = { idx, it -> "art_${idx}_${it.browseId}" }) { _, artist ->
            Column(
                modifier = Modifier.clickable { onArtistClick(artist.browseId) },
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(linColors.glassBorder)
                ) {
                    if (!artist.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(artist.name, style = LinTextStyles.navItemActive, color = linColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                artist.subscriberCount?.let {
                    Text(it, style = LinTextStyles.subtitle, color = linColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private enum class AlbumSubFilter(val label: String) {
    ALL("all albums"),
    EPS_SINGLES("eps & singles")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumsGridScreen(
    albums: List<AlbumSummary>,
    activeTagFilter: String?,
    onTagClear: () -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    var albumFilter by remember { mutableStateOf(AlbumSubFilter.ALL) }

    val filtered = remember(albums, albumFilter, activeTagFilter) {
        albums.filter { alb ->
            (albumFilter != AlbumSubFilter.EPS_SINGLES || alb.isEpOrSingle) &&
                (activeTagFilter == null || activeTagFilter in alb.tags)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 180.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlbumSubFilter.entries.forEach { f ->
                    LinTagPill(f.label, active = albumFilter == f, onClick = { albumFilter = f })
                }
                if (activeTagFilter != null) {
                    LinTagPill("#$activeTagFilter ×", active = true, onClick = onTagClear)
                }
            }
        }

        if (filtered.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = linLabel("no saved albums yet."),
                    style = LinTextStyles.sectionHeading,
                    color = linColors.textSecondary
                )
            }
        } else {
            itemsIndexed(filtered, key = { idx, it -> "alb_${idx}_${it.browseId}" }) { _, album ->
                Column(modifier = Modifier.clickable { onAlbumClick(album.browseId) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(LinSharpShape)
                            .background(linColors.glassBorder)
                    ) {
                        if (!album.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = album.thumbnailUrl,
                                contentDescription = album.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(album.title, style = LinTextStyles.navItemActive, color = linColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = album.artistDisplay.ifBlank { linLabel(if (album.isEpOrSingle) "EP" else "Album") },
                        style = LinTextStyles.subtitle,
                        color = linColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadsHubScreen(
    downloadedTracks: List<Track>,
    smartDownloadedTracks: List<Track>,
    recentlyPlayedDownloadedTracks: List<Track>,
    downloadedAlbums: List<AlbumSummary>,
    smartDownloadsEnabled: Boolean,
    recentlyPlayedDownloadsEnabled: Boolean,
    currentVideoId: String?,
    accentColor: Color,
    downloadMap: Map<String, Int>,
    showThumbnails: Boolean,
    onOpenLocalPlaylist: (LocalDownloadPlaylistKind) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onSwipeRightPlayNext: (Track) -> Unit,
    onSwipeLeftAddToQueue: (Track) -> Unit,
    onTrackMore: (Track) -> Unit,
    onPreviewTrack: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val listState = rememberLazyListState()
    var previewedKey by remember { mutableStateOf<String?>(null) }
    var previewArtUrl by remember { mutableStateOf<String?>(null) }
    var previewCenterYPx by remember { mutableFloatStateOf(0f) }
    var boxTopInRoot by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(1200f) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) previewedKey = null
    }

    val firstVisibleIdx by remember(listState) {
        derivedStateOf { (listState.firstVisibleItemIndex - 3).coerceAtLeast(0) }
    }
    val preloadUrls = remember(downloadedTracks, firstVisibleIdx) {
        val end = (firstVisibleIdx + 8).coerceAtMost(downloadedTracks.size)
        (firstVisibleIdx until end).map { downloadedTracks[it].thumbnailUrl }
    }
    PreloadPreviewArtworks(preloadUrls, isScrolling = listState.isScrollInProgress)

    data class GeneratedCardData(
        val kind: LocalDownloadPlaylistKind,
        val subtitle: String,
        val thumb: String?
    )

    val cards = listOf(
        GeneratedCardData(
            kind = LocalDownloadPlaylistKind.SMART_DOWNLOADS,
            subtitle = if (smartDownloadsEnabled || smartDownloadedTracks.isNotEmpty()) {
                "${smartDownloadedTracks.size} tracks"
            } else {
                "off in settings"
            },
            thumb = smartDownloadedTracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
        ),
        GeneratedCardData(
            kind = LocalDownloadPlaylistKind.RECENTLY_PLAYED_DOWNLOADS,
            subtitle = if (recentlyPlayedDownloadsEnabled || recentlyPlayedDownloadedTracks.isNotEmpty()) {
                "${recentlyPlayedDownloadedTracks.size} tracks"
            } else {
                "off in settings"
            },
            thumb = recentlyPlayedDownloadedTracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height.toFloat() }
            .onGloballyPositioned { boxTopInRoot = it.positionInRoot().y }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(linLabel("offline playlists"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)

                    cards.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            pair.forEach { card ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            previewedKey = null
                                            onOpenLocalPlaylist(card.kind)
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(LinSharpShape)
                                            .background(linColors.glassBorder)
                                            .border(1.dp, linColors.glassBorder)
                                    ) {
                                        if (!card.thumb.isNullOrBlank()) {
                                            AsyncImage(
                                                model = card.thumb,
                                                contentDescription = card.kind.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = linLabel(card.kind.title),
                                        style = LinTextStyles.navItemActive,
                                        color = linColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = linLabel(card.subtitle),
                                        style = LinTextStyles.subtitle,
                                        color = linColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (downloadedAlbums.isNotEmpty()) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                    ) {
                        Text(linLabel("downloaded albums"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(downloadedAlbums, key = { idx, it -> "dl_alb_${idx}_${it.browseId}" }) { _, alb ->
                                Column(
                                    modifier = Modifier
                                        .width(136.dp)
                                        .clickable { onAlbumClick(alb.browseId) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(LinSharpShape)
                                            .background(linColors.glassBorder)
                                    ) {
                                        if (!alb.thumbnailUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = alb.thumbnailUrl,
                                                contentDescription = alb.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(alb.title, style = LinTextStyles.navItemActive, color = linColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(alb.artistDisplay, style = LinTextStyles.subtitle, color = linColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = linLabel("downloaded songs (${downloadedTracks.size})"),
                    style = LinTextStyles.sectionHeading,
                    color = linColors.textPrimary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )
            }

            if (downloadedTracks.isEmpty()) {
                item {
                    Text(
                        text = linLabel("no downloaded tracks yet."),
                        style = LinTextStyles.navItem,
                        color = linColors.textSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(downloadedTracks, key = { idx, t -> "dl_${idx}_${t.videoId}" }) { idx, track ->
                    val rowKey = "dl_${idx}_${track.videoId}"
                    var rowCenterY by remember { mutableFloatStateOf(0f) }
                    SwipeableTrackRow(
                        track = track,
                        isCurrentTrack = track.videoId == currentVideoId,
                        accentColor = accentColor,
                        isDownloaded = true,
                        showThumbnail = showThumbnails,
                        isPreviewed = previewedKey == rowKey,
                        onClick = {
                            if (previewedKey == rowKey) {
                                previewedKey = null
                            } else {
                                previewedKey = rowKey
                                previewArtUrl = track.thumbnailUrl
                                previewCenterYPx = rowCenterY - boxTopInRoot
                                onPreviewTrack(track)
                            }
                        },
                        onDoubleClick = {
                            previewedKey = null
                            onPlayTracks(downloadedTracks, idx, "downloads")
                        },
                        onSwipeRightPlayNext = { onSwipeRightPlayNext(track) },
                        onSwipeLeftAddToQueue = { onSwipeLeftAddToQueue(track) },
                        onMoreClick = { onTrackMore(track) },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            rowCenterY = coords.positionInRoot().y + coords.size.height / 2f
                        }
                    )
                }
            }
        }

        LinTrackArtHoverOverlay(
            imageUrl = if (previewedKey != null) previewArtUrl else null,
            centerYPx = previewCenterYPx,
            viewportHeightPx = viewportHeightPx
        )

        LinLazyColumnScrollbar(
            listState = listState,
            accentColor = accentColor,
            modifier = Modifier.align(Alignment.CenterEnd),
            bottomPadding = 172.dp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistSummary>,
    activeTagFilter: String?,
    accentColor: Color,
    onTagClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSwipeRightPlayNext: (PlaylistSummary) -> Unit,
    onSwipeLeftAddToQueue: (PlaylistSummary) -> Unit,
    onCreatePlaylist: (title: String, desc: String, privacy: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val haptic = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    var showCreate by remember { mutableStateOf(false) }
    var longPressedPlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }

    val filtered = remember(playlists, activeTagFilter) {
        if (activeTagFilter == null) playlists
        else playlists.filter { activeTagFilter in it.tags }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinInvertedButton(text = "create new", onClick = { showCreate = true })
                    if (activeTagFilter != null) {
                        LinTagPill("#$activeTagFilter ×", active = true, onClick = { onTagClick(activeTagFilter) })
                    }
                }
            }

            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = linLabel("no playlists found."),
                        style = LinTextStyles.sectionHeading,
                        color = linColors.textSecondary
                    )
                }
            } else {
                itemsIndexed(filtered, key = { idx, it -> "pl_${idx}_${it.id}" }) { _, pl ->
                    Column(
                        modifier = Modifier.combinedClickable(
                            onClick = { onPlaylistClick(pl.browseId) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                longPressedPlaylist = pl
                            }
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(LinSharpShape)
                                .background(linColors.glassBorder)
                        ) {
                            if (!pl.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = pl.thumbnailUrl,
                                    contentDescription = pl.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = pl.title,
                            style = LinTextStyles.navItemActive,
                            color = linColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = pl.subtitle ?: pl.trackCount?.let { "$it tracks" } ?: linLabel("Playlist")
                        Text(
                            text = sub,
                            style = LinTextStyles.subtitle,
                            color = linColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        LinLazyGridScrollbar(
            gridState = gridState,
            accentColor = accentColor,
            modifier = Modifier.align(Alignment.CenterEnd),
            bottomPadding = 172.dp
        )
    }

    longPressedPlaylist?.let { pl ->
        Dialog(onDismissRequest = { longPressedPlaylist = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(linColors.background)
                    .border(1.dp, linColors.glassBorder)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = pl.title,
                    style = LinTextStyles.sectionHeading,
                    color = linColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                LinInvertedButton(
                    text = "play playlist next",
                    onClick = {
                        onSwipeRightPlayNext(pl)
                        longPressedPlaylist = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                LinOutlinedButton(
                    text = "add playlist to queue",
                    onClick = {
                        onSwipeLeftAddToQueue(pl)
                        longPressedPlaylist = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                LinOutlinedButton(
                    text = "cancel",
                    onClick = { longPressedPlaylist = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showCreate) {
        PlaylistEditorDialog(
            title = "create playlist",
            initialName = "",
            initialDescription = "",
            nameEditable = true,
            onDismiss = { showCreate = false },
            onConfirm = { name, desc, privacy ->
                onCreatePlaylist(name, desc, privacy)
                showCreate = false
            }
        )
    }
}
