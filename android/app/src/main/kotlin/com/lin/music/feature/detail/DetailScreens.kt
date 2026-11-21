package com.lin.music.feature.detail

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import com.lin.music.core.designsystem.components.LinInvertedButton
import com.lin.music.core.designsystem.components.LinOutlinedButton
import com.lin.music.core.designsystem.components.LinTagPill
import com.lin.music.core.designsystem.components.LinTrackArtHoverOverlay
import com.lin.music.core.designsystem.components.PreloadPreviewArtworks
import com.lin.music.core.designsystem.components.SwipeableTrackRow
import com.lin.music.core.designsystem.components.highResArtworkUrl
import com.lin.music.core.designsystem.icons.LinIcons
import com.lin.music.core.designsystem.sheets.LinUnderlineTextField
import com.lin.music.core.designsystem.sheets.PlaylistEditorDialog
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.linLabel
import com.lin.music.core.model.AlbumDetail
import com.lin.music.core.model.ArtistDetail
import com.lin.music.core.model.PlaylistDetail
import com.lin.music.core.model.Track
import com.lin.music.ui.EditablePlaylistContext
import com.lin.music.ui.SortOption
import com.lin.music.util.ShareHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumDetailScreen(
    album: AlbumDetail?,
    isLoading: Boolean,
    error: String?,
    currentVideoId: String?,
    accentColor: Color,
    downloadMap: Map<String, Int>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onShuffle: (List<Track>, String) -> Unit,
    onStartMix: (Track) -> Unit,
    onToggleSaveLibrary: () -> Unit,
    onDownloadAll: (List<Track>) -> Unit,
    onGoToArtist: (String) -> Unit,
    onSwipeRightPlayNext: (Track) -> Unit,
    onSwipeLeftAddToQueue: (Track) -> Unit,
    onTrackMore: (Track) -> Unit,
    onPreviewTrack: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val context = LocalContext.current
    var filterQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var previewedKey by remember { mutableStateOf<String?>(null) }
    var previewArtUrl by remember { mutableStateOf<String?>(null) }
    var previewCenterYPx by remember { mutableFloatStateOf(0f) }
    var boxTopInRoot by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(1200f) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) previewedKey = null
    }

    val visibleAlbumTracks = remember(album?.tracks, filterQuery) {
        val tracks = album?.tracks.orEmpty()
        if (filterQuery.isBlank()) tracks else tracks.filter {
            it.title.contains(filterQuery, true) || it.artistDisplay.contains(filterQuery, true)
        }
    }
    val firstVisibleTrackIdx by remember(listState) {
        derivedStateOf { (listState.firstVisibleItemIndex - 2).coerceAtLeast(0) }
    }
    val preloadUrls = remember(visibleAlbumTracks, album?.thumbnailUrl, firstVisibleTrackIdx) {
        buildList {
            album?.thumbnailUrl?.let { add(it) }
            val start = firstVisibleTrackIdx
            val end = (start + 8).coerceAtMost(visibleAlbumTracks.size)
            for (i in start until end) {
                add(visibleAlbumTracks[i].thumbnailUrl ?: album?.thumbnailUrl)
            }
        }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { previewedKey = null }
                ) {
                    IconButton(onClick = onBack) {
                        Icon(LinIcons.ArrowLeft, "Back", tint = linColors.textSecondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = linLabel(album?.let { if (it.isEpOrSingle) "ep / single" else "album" } ?: "album"),
                        style = LinTextStyles.subtitle,
                        color = linColors.textSecondary
                    )
                }
            }

            if (isLoading && album == null) {
                item { Text(linLabel("loading album..."), style = LinTextStyles.navItem, color = linColors.textSecondary) }
                return@LazyColumn
            }

            if (error != null && album == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(error, style = LinTextStyles.subtitle, color = linColors.danger)
                        LinInvertedButton("retry", onClick = onRetry)
                    }
                }
                return@LazyColumn
            }

            val data = album ?: return@LazyColumn

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(top = 6.dp, bottom = 14.dp)
                        .clickable { previewedKey = null }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(LinSharpShape)
                                .background(linColors.glassBorder)
                        ) {
                            val hiResThumb = remember(data.thumbnailUrl) { highResArtworkUrl(data.thumbnailUrl) }
                            if (!hiResThumb.isNullOrBlank()) {
                                val req = remember(hiResThumb) {
                                    ImageRequest.Builder(context)
                                        .data(hiResThumb)
                                        .size(Size.ORIGINAL)
                                        .precision(Precision.EXACT)
                                        .build()
                                }
                                AsyncImage(
                                    model = req,
                                    contentDescription = data.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(data.title, style = LinTextStyles.drilldownTitle, color = linColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            val firstArtistId = data.artists.firstOrNull { !it.id.isNullOrBlank() }?.id
                            Text(
                                text = listOfNotNull(data.artistDisplay.takeIf { it.isNotBlank() }, data.year, "${data.tracks.size} tracks").joinToString(" · "),
                                style = LinTextStyles.subtitle,
                                color = linColors.textSecondary,
                                modifier = if (firstArtistId != null) Modifier.clickable { onGoToArtist(firstArtistId) } else Modifier
                            )
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinInvertedButton("play", enabled = data.tracks.isNotEmpty(), onClick = { onPlayTracks(data.tracks, 0, data.title) })
                        LinOutlinedButton("shuffle", enabled = data.tracks.isNotEmpty(), onClick = { onShuffle(data.tracks, data.title) })
                        LinOutlinedButton(
                            text = if (data.inLibrary) "in library ✓" else "+ library",
                            onClick = onToggleSaveLibrary
                        )
                        if (data.tracks.isNotEmpty()) {
                            LinOutlinedButton("start mix", onClick = { onStartMix(data.tracks.first()) })
                            LinOutlinedButton("download", onClick = { onDownloadAll(data.tracks) })
                        }
                        LinOutlinedButton(
                            text = "share",
                            onClick = {
                                ShareHelper.shareText(context, "${data.title} — ${data.artistDisplay}\n${ShareHelper.albumUrl(data.browseId)}", data.title)
                            }
                        )
                    }

                    if (data.tracks.size > 6) {
                        LinUnderlineTextField(
                            value = filterQuery,
                            onValueChange = { filterQuery = it },
                            placeholder = "filter tracks in album..."
                        )
                    }
                }
            }

            itemsIndexed(visibleAlbumTracks, key = { idx, t -> "${idx}_${t.videoId}" }) { idx, track ->
                val rowKey = "${idx}_${track.videoId}"
                var rowCenterY by remember { mutableFloatStateOf(0f) }
                SwipeableTrackRow(
                    track = track,
                    isCurrentTrack = track.videoId == currentVideoId,
                    accentColor = accentColor,
                    isDownloaded = downloadMap[track.videoId] == 2,
                    showThumbnail = false,
                    isPreviewed = previewedKey == rowKey,
                    onClick = {
                        if (previewedKey == rowKey) {
                            previewedKey = null
                        } else {
                            previewedKey = rowKey
                            previewArtUrl = track.thumbnailUrl ?: data.thumbnailUrl
                            previewCenterYPx = rowCenterY - boxTopInRoot
                            onPreviewTrack(track)
                        }
                    },
                    onDoubleClick = {
                        previewedKey = null
                        onPlayTracks(visibleAlbumTracks, idx, data.title)
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

        // Hover art renders IN FRONT of track rows cleanly (no pointer consumption)
        LinTrackArtHoverOverlay(
            imageUrl = if (previewedKey != null) previewArtUrl else null,
            centerYPx = previewCenterYPx,
            viewportHeightPx = viewportHeightPx
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistDetail?,
    isLoading: Boolean,
    error: String?,
    currentVideoId: String?,
    accentColor: Color,
    downloadMap: Map<String, Int>,
    libraryTracks: List<Track>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onShuffle: (List<Track>, String) -> Unit,
    onStartMix: (String, String) -> Unit,
    onDownloadAll: (List<Track>) -> Unit,
    onToggleSaveLibrary: () -> Unit,
    onCloneToEditable: () -> Unit,
    onUpdatePlaylistMeta: (String, String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onAddSongToPlaylist: (Track) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onSwipeRightPlayNext: (Track) -> Unit,
    onSwipeLeftAddToQueue: (Track) -> Unit,
    onTrackMore: (Track, EditablePlaylistContext?) -> Unit,
    onTrackRemovedLocal: (Track) -> Unit,
    onTrackRestoredLocal: (Track) -> Unit,
    onPreviewTrack: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val context = LocalContext.current
    var filterQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.CUSTOM) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddSongs by remember { mutableStateOf(false) }
    var addSongSearch by remember { mutableStateOf("") }
    var reorderMode by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var previewedKey by remember { mutableStateOf<String?>(null) }
    var previewArtUrl by remember { mutableStateOf<String?>(null) }
    var previewCenterYPx by remember { mutableFloatStateOf(0f) }
    var boxTopInRoot by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(1200f) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) previewedKey = null
    }

    val processed = remember(playlist?.tracks, filterQuery, sortOption) {
        val raw = playlist?.tracks.orEmpty()
        val base = if (filterQuery.isBlank()) raw else raw.filter {
            it.title.contains(filterQuery, true) || it.artistDisplay.contains(filterQuery, true)
        }
        when (sortOption) {
            SortOption.CUSTOM -> base
            SortOption.TITLE -> base.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> base.sortedBy { it.artistDisplay.lowercase() }
        }
    }
    val firstVisibleTrackIdx by remember(listState) {
        derivedStateOf { (listState.firstVisibleItemIndex - 2).coerceAtLeast(0) }
    }
    val preloadUrls = remember(processed, playlist?.thumbnailUrl, firstVisibleTrackIdx) {
        buildList {
            playlist?.thumbnailUrl?.let { add(it) }
            val start = firstVisibleTrackIdx
            val end = (start + 8).coerceAtMost(processed.size)
            for (i in start until end) {
                add(processed[i].thumbnailUrl ?: playlist?.thumbnailUrl)
            }
        }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { previewedKey = null },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(LinIcons.ArrowLeft, "Back", tint = linColors.textSecondary, modifier = Modifier.size(24.dp))
                        }
                        Text(
                            text = linLabel(if (playlist?.id?.startsWith("LOCAL_") == true) "offline playlist" else "playlist"),
                            style = LinTextStyles.subtitle,
                            color = linColors.textSecondary
                        )
                    }
                    if (playlist?.isEditable == true && playlist.id != "LM") {
                        Row {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(LinIcons.Edit, "Edit playlist", tint = linColors.textSecondary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onDeletePlaylist) {
                                Icon(LinIcons.Trash, "Delete playlist", tint = linColors.danger, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            if (isLoading && playlist == null) {
                item { Text(linLabel("loading playlist..."), style = LinTextStyles.navItem, color = linColors.textSecondary) }
                return@LazyColumn
            }
            if (error != null && playlist == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(error, style = LinTextStyles.subtitle, color = linColors.danger)
                        LinInvertedButton("retry", onClick = onRetry)
                    }
                }
                return@LazyColumn
            }

            val data = playlist ?: return@LazyColumn
            val isLocalCollection = data.id.startsWith("LOCAL_")
            val isOwnedEditable = !isLocalCollection && data.isEditable && data.id != "LM"
            val editableCtx = if (isOwnedEditable) {
                EditablePlaylistContext(
                    playlistId = data.id,
                    playlistTitle = data.title,
                    onRemoved = onTrackRemovedLocal,
                    onRestored = onTrackRestoredLocal
                )
            } else null

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(top = 6.dp, bottom = 12.dp)
                        .clickable { previewedKey = null }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(LinSharpShape)
                                .background(linColors.glassBorder)
                        ) {
                            val hiResThumb = remember(data.thumbnailUrl) { highResArtworkUrl(data.thumbnailUrl) }
                            if (!hiResThumb.isNullOrBlank()) {
                                val req = remember(hiResThumb) {
                                    ImageRequest.Builder(context)
                                        .data(hiResThumb)
                                        .size(Size.ORIGINAL)
                                        .precision(Precision.EXACT)
                                        .build()
                                }
                                AsyncImage(
                                    model = req,
                                    contentDescription = data.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(data.title, style = LinTextStyles.drilldownTitle, color = linColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = listOfNotNull(data.author, "${data.tracks.size} tracks").joinToString(" · "),
                                style = LinTextStyles.subtitle,
                                color = linColors.textSecondary
                            )
                        }
                    }

                    if (!data.description.isNullOrBlank()) {
                        Text(data.description!!, style = LinTextStyles.subtitle, color = linColors.textSecondary)
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinInvertedButton("play", enabled = data.tracks.isNotEmpty(), onClick = { onPlayTracks(data.tracks, 0, data.title) })
                        LinOutlinedButton("shuffle", enabled = data.tracks.isNotEmpty(), onClick = { onShuffle(data.tracks, data.title) })
                        if (!isLocalCollection) {
                            LinOutlinedButton("start mix", onClick = { onStartMix(data.radioEndpointPlaylistId ?: data.id, data.title) })
                            if (data.tracks.isNotEmpty()) {
                                LinOutlinedButton("download", onClick = { onDownloadAll(data.tracks) })
                            }
                            if (isOwnedEditable) {
                                LinOutlinedButton(if (showAddSongs) "close add" else "+ add songs", onClick = { showAddSongs = !showAddSongs })
                                LinOutlinedButton(if (reorderMode) "done reorder" else "reorder", onClick = { reorderMode = !reorderMode })
                            } else if (data.id != "LM") {
                                LinOutlinedButton(
                                    text = if (data.inLibrary) "in library ✓" else "+ library",
                                    onClick = onToggleSaveLibrary
                                )
                                if (data.tracks.isNotEmpty()) {
                                    LinOutlinedButton("copy to edit", onClick = onCloneToEditable)
                                }
                            }
                            LinOutlinedButton(
                                text = "share",
                                onClick = { ShareHelper.shareText(context, "${data.title}\n${ShareHelper.playlistUrl(data.id)}", data.title) }
                            )
                        }
                    }

                    if (isOwnedEditable && showAddSongs) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(linColors.glassBg)
                                .border(1.dp, linColors.glassBorder)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LinUnderlineTextField(
                                value = addSongSearch,
                                onValueChange = { addSongSearch = it },
                                placeholder = "search library to add..."
                            )
                            val candidates = remember(addSongSearch, libraryTracks) {
                                if (addSongSearch.isBlank()) libraryTracks.take(8)
                                else libraryTracks.filter {
                                    it.title.contains(addSongSearch, true) || it.artistDisplay.contains(addSongSearch, true)
                                }.take(12)
                            }
                            candidates.forEach { cand ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cand.title, style = LinTextStyles.navItemActive, color = linColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(cand.artistDisplay, style = LinTextStyles.subtitle, color = linColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    LinOutlinedButton("+ add", onClick = { onAddSongToPlaylist(cand) })
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortOption.entries.forEach { opt ->
                            LinTagPill("sort: ${opt.label}", active = sortOption == opt, onClick = { sortOption = opt })
                        }
                    }

                    LinUnderlineTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = "filter playlist..."
                    )
                }
            }

            itemsIndexed(processed, key = { idx, t -> "${idx}_${t.videoId}_${t.setVideoId ?: ""}" }) { idx, track ->
                val rowKey = "${idx}_${track.videoId}_${track.setVideoId ?: ""}"
                var rowCenterY by remember { mutableFloatStateOf(0f) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isOwnedEditable && reorderMode && sortOption == SortOption.CUSTOM && filterQuery.isBlank()) {
                        Column(modifier = Modifier.padding(end = 6.dp)) {
                            Text(
                                "↑",
                                style = LinTextStyles.navItemActive,
                                color = if (idx > 0) linColors.textPrimary else linColors.textSecondary,
                                modifier = Modifier.clickable(enabled = idx > 0) { onMoveTrack(idx, -1) }.padding(4.dp)
                            )
                            Text(
                                "↓",
                                style = LinTextStyles.navItemActive,
                                color = if (idx < processed.lastIndex) linColors.textPrimary else linColors.textSecondary,
                                modifier = Modifier.clickable(enabled = idx < processed.lastIndex) { onMoveTrack(idx, +1) }.padding(4.dp)
                            )
                        }
                    }
                    SwipeableTrackRow(
                        track = track,
                        isCurrentTrack = track.videoId == currentVideoId,
                        accentColor = accentColor,
                        isDownloaded = downloadMap[track.videoId] == 2,
                        showThumbnail = false,
                        isPreviewed = previewedKey == rowKey,
                        onClick = {
                            if (previewedKey == rowKey) {
                                previewedKey = null
                            } else {
                                previewedKey = rowKey
                                previewArtUrl = track.thumbnailUrl ?: data.thumbnailUrl
                                previewCenterYPx = rowCenterY - boxTopInRoot
                                onPreviewTrack(track)
                            }
                        },
                        onDoubleClick = {
                            previewedKey = null
                            onPlayTracks(processed, idx, data.title)
                        },
                        onSwipeRightPlayNext = { onSwipeRightPlayNext(track) },
                        onSwipeLeftAddToQueue = { onSwipeLeftAddToQueue(track) },
                        onMoreClick = { onTrackMore(track, editableCtx) },
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
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
    }

    if (showEditDialog && playlist != null && playlist.isEditable && playlist.id != "LM") {
        PlaylistEditorDialog(
            title = "edit playlist",
            initialName = playlist.title,
            initialDescription = playlist.description.orEmpty(),
            nameEditable = true,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, desc, _ ->
                onUpdatePlaylistMeta(name, desc)
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistDetailScreen(
    artist: ArtistDetail?,
    isLoading: Boolean,
    error: String?,
    currentVideoId: String?,
    accentColor: Color,
    downloadMap: Map<String, Int>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onStartMix: (String, String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onSwipeRightPlayNext: (Track) -> Unit,
    onSwipeLeftAddToQueue: (Track) -> Unit,
    onTrackMore: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(LinIcons.ArrowLeft, "Back", tint = linColors.textSecondary, modifier = Modifier.size(24.dp))
                }
                Text(linLabel("artist"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
            }
        }

        if (isLoading && artist == null) {
            item { Text(linLabel("loading artist..."), style = LinTextStyles.navItem, color = linColors.textSecondary) }
            return@LazyColumn
        }
        if (error != null && artist == null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(error, style = LinTextStyles.subtitle, color = linColors.danger)
                    LinInvertedButton("retry", onClick = onRetry)
                }
            }
            return@LazyColumn
        }

        val data = artist ?: return@LazyColumn

        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(linColors.glassBorder)
                    ) {
                        if (!data.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = data.thumbnailUrl,
                                contentDescription = data.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(data.name, style = LinTextStyles.drilldownTitle, color = linColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        data.subscriberCount?.let {
                            Text(it, style = LinTextStyles.subtitle, color = linColors.textSecondary)
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (data.topSongs.isNotEmpty()) {
                        LinInvertedButton("shuffle", onClick = { onPlayTracks(data.topSongs.shuffled(), 0, "${data.name} · shuffle") })
                    }
                    LinOutlinedButton(
                        text = if (data.isSubscribed) "subscribed ✓" else "subscribe",
                        onClick = onToggleSubscribe
                    )
                    (data.radioPlaylistId ?: data.topSongs.firstOrNull()?.let { "RDAMVM${it.videoId}" })?.let { mixId ->
                        LinOutlinedButton("start mix", onClick = { onStartMix(mixId, data.name) })
                    }
                    LinOutlinedButton("share", onClick = {
                        ShareHelper.shareText(context, "${data.name}\n${ShareHelper.artistUrl(data.browseId)}", data.name)
                    })
                }
            }
        }

        if (data.topSongs.isNotEmpty()) {
            item { Text(linLabel("top songs"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
            itemsIndexed(data.topSongs, key = { idx, t -> "${idx}_${t.videoId}" }) { idx, track ->
                SwipeableTrackRow(
                    track = track,
                    isCurrentTrack = track.videoId == currentVideoId,
                    accentColor = accentColor,
                    isDownloaded = downloadMap[track.videoId] == 2,
                    showThumbnail = true,
                    onClick = { onPlayTracks(data.topSongs, idx, data.name) },
                    onSwipeRightPlayNext = { onSwipeRightPlayNext(track) },
                    onSwipeLeftAddToQueue = { onSwipeLeftAddToQueue(track) },
                    onMoreClick = { onTrackMore(track) }
                )
            }
        }

        if (data.albums.isNotEmpty()) {
            item { Text(linLabel("albums"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary, modifier = Modifier.padding(top = 12.dp)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(data.albums, key = { idx, alb -> "ar_al_${idx}_${alb.browseId}" }) { _, alb ->
                        Column(modifier = Modifier.width(148.dp).clickable { onAlbumClick(alb.browseId) }) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(LinSharpShape).background(linColors.glassBorder)) {
                                if (!alb.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(alb.thumbnailUrl, alb.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(alb.title, style = LinTextStyles.navItemActive, color = linColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            alb.year?.let { Text(it, style = LinTextStyles.subtitle, color = linColors.textSecondary) }
                        }
                    }
                }
            }
        }

        if (data.singlesAndEps.isNotEmpty()) {
            item { Text("singles & eps", style = LinTextStyles.sectionHeading, color = linColors.textPrimary, modifier = Modifier.padding(top = 12.dp)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(data.singlesAndEps, key = { idx, ep -> "ar_ep_${idx}_${ep.browseId}" }) { _, ep ->
                        Column(modifier = Modifier.width(148.dp).clickable { onAlbumClick(ep.browseId) }) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(LinSharpShape).background(linColors.glassBorder)) {
                                if (!ep.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(ep.thumbnailUrl, ep.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(ep.title, style = LinTextStyles.navItemActive, color = linColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ep.year?.let { Text("EP · $it", style = LinTextStyles.subtitle, color = linColors.textSecondary) }
                        }
                    }
                }
            }
        }

        if (data.relatedArtists.isNotEmpty()) {
            item { Text("fans also like", style = LinTextStyles.sectionHeading, color = linColors.textPrimary, modifier = Modifier.padding(top = 12.dp)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(data.relatedArtists, key = { idx, rel -> "ar_rel_${idx}_${rel.browseId}" }) { _, rel ->
                        Column(modifier = Modifier.width(120.dp).clickable { onArtistClick(rel.browseId) }) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(linColors.glassBorder)) {
                                if (!rel.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(rel.thumbnailUrl, rel.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(rel.name, style = LinTextStyles.navItemActive, color = linColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
