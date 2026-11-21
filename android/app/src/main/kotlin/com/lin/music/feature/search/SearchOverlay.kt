package com.lin.music.feature.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lin.music.core.designsystem.components.LinTagPill
import com.lin.music.core.designsystem.components.SwipeableTrackRow
import com.lin.music.core.designsystem.icons.LinIcons
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.linLabel
import com.lin.music.core.model.SearchFilter
import com.lin.music.core.model.SearchResultPage
import com.lin.music.core.model.Track

/**
 * Exact mobile translation of .search-overlay in frontend_css/styles.css:
 * Frosted overlay, centered 2dp underline input, top-right close X, grid & swipeable track results below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    query: String,
    filter: SearchFilter,
    suggestions: List<String>,
    history: List<String>,
    results: SearchResultPage,
    isLoading: Boolean,
    currentVideoId: String?,
    accentColor: Color,
    downloadMap: Map<String, Int>,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SearchFilter) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClose: () -> Unit,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onSwipeRightPlayNext: (Track) -> Unit,
    onSwipeLeftAddToQueue: (Track) -> Unit,
    onTrackMore: (Track) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(linColors.searchOverlayBg)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose) {
                Icon(LinIcons.Close, contentDescription = "Close search", tint = linColors.textSecondary, modifier = Modifier.size(28.dp))
            }
        }

        // Centered underline input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search...",
                    style = LinTextStyles.searchInput,
                    color = linColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LinTextStyles.searchInput.copy(
                    color = linColors.textPrimary,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(linColors.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmitSearch(query) }),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .focusRequester(focusRequester)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .align(Alignment.CenterHorizontally)
                .height(2.dp)
                .background(linColors.textPrimary)
        )

        Spacer(Modifier.height(14.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(SearchFilter.entries) { f ->
                LinTagPill(text = f.label, active = filter == f, onClick = { onFilterChange(f) })
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (query.isBlank() && history.isNotEmpty()) {
                item {
                    Text(
                        linLabel("recent"),
                        style = LinTextStyles.subtitle,
                        color = linColors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(history, key = { idx, h -> "hist_${idx}_$h" }) { _, h ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSubmitSearch(h) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(h, style = LinTextStyles.navItem, color = linColors.textPrimary)
                        IconButton(onClick = { onDeleteHistory(h) }, modifier = Modifier.size(28.dp)) {
                            Icon(LinIcons.Close, null, tint = linColors.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        suggestions.forEach { sug ->
                            LinTagPill(text = sug, active = false, onClick = { onSubmitSearch(sug) })
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Text(linLabel("searching..."), style = LinTextStyles.navItem, color = linColors.textSecondary)
                }
            }

            if (results.tracks.isNotEmpty()) {
                item {
                    Text(
                        linLabel("songs"),
                        style = LinTextStyles.sectionHeading,
                        color = linColors.textPrimary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(results.tracks, key = { idx, t -> "sr_t_${idx}_${t.videoId}" }) { idx, track ->
                    SwipeableTrackRow(
                        track = track,
                        isCurrentTrack = track.videoId == currentVideoId,
                        accentColor = accentColor,
                        isDownloaded = downloadMap[track.videoId] == 2,
                        showThumbnail = true,
                        onClick = { onPlayTracks(results.tracks, idx, "search: $query") },
                        onSwipeRightPlayNext = { onSwipeRightPlayNext(track) },
                        onSwipeLeftAddToQueue = { onSwipeLeftAddToQueue(track) },
                        onMoreClick = { onTrackMore(track) }
                    )
                }
            }

            if (results.albums.isNotEmpty()) {
                item { Text(linLabel("albums & eps"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(results.albums, key = { idx, alb -> "sr_al_${idx}_${alb.browseId}" }) { _, alb ->
                            Column(modifier = Modifier.width(138.dp).clickable { onAlbumClick(alb.browseId) }) {
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(LinSharpShape).background(linColors.glassBorder)) {
                                    if (!alb.thumbnailUrl.isNullOrBlank()) {
                                        AsyncImage(alb.thumbnailUrl, alb.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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

            if (results.artists.isNotEmpty()) {
                item { Text(linLabel("artists"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(results.artists, key = { idx, art -> "sr_ar_${idx}_${art.browseId}" }) { _, art ->
                            Column(modifier = Modifier.width(116.dp).clickable { onArtistClick(art.browseId) }) {
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(linColors.glassBorder)) {
                                    if (!art.thumbnailUrl.isNullOrBlank()) {
                                        AsyncImage(art.thumbnailUrl, art.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(art.name, style = LinTextStyles.navItemActive, color = linColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            if (results.playlists.isNotEmpty()) {
                item { Text(linLabel("playlists"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(results.playlists, key = { idx, pl -> "sr_pl_${idx}_${pl.browseId}" }) { _, pl ->
                            Column(modifier = Modifier.width(138.dp).clickable { onPlaylistClick(pl.browseId) }) {
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(LinSharpShape).background(linColors.glassBorder)) {
                                    if (!pl.thumbnailUrl.isNullOrBlank()) {
                                        AsyncImage(pl.thumbnailUrl, pl.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(pl.title, style = LinTextStyles.navItemActive, color = linColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
