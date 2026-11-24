package com.lin.music.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin.music.core.designsystem.icons.LinIcons
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.Track
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Universal LIN Track Row:
 * - Right Swipe -> Play Next (always, everywhere)
 * - Left Swipe -> Add to Queue (always, everywhere)
 * - 24dp screen-edge exclusion so Android predictive system Back gesture is never intercepted.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SwipeableTrackRow(
    track: Track,
    isCurrentTrack: Boolean,
    accentColor: Color,
    isDownloaded: Boolean,
    showThumbnail: Boolean = false,
    activeTagFilter: String? = null,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onTripleClick: (() -> Unit)? = null,
    tapCoordinator: MultiTapCoordinator? = null,
    rowKey: String = track.videoId,
    isPreviewed: Boolean = false,
    hideThumbnail: Boolean = isPreviewed,
    onSwipeRightPlayNext: () -> Unit,
    onSwipeLeftAddToQueue: () -> Unit,
    onMoreClick: () -> Unit,
    onTagClick: (String) -> Unit = {},
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val localCoordinator = rememberMultiTapCoordinator()
    val effectiveCoordinator = tapCoordinator ?: localCoordinator
    val currentTapCoordinator by rememberUpdatedState(effectiveCoordinator)
    val currentRowKey by rememberUpdatedState(rowKey)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
    val currentOnTripleClick by rememberUpdatedState(onTripleClick)
    val currentOnMoreClick by rememberUpdatedState(onMoreClick)
    val currentOnToggleSelect by rememberUpdatedState(onToggleSelect)
    val currentSelectionMode by rememberUpdatedState(selectionMode)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRightPlayNext)
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeftAddToQueue)

    DisposableEffect(rowKey, effectiveCoordinator) {
        onDispose { effectiveCoordinator.cancel(rowKey) }
    }

    val offsetX = remember { Animatable(0f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    var crossedThreshold by remember { mutableStateOf(false) }

    val edgeGuardPx = with(density) { 24.dp.toPx() }
    val maxSwipePx = with(density) { 160.dp.toPx() }
    val thresholdPx = if (rowWidthPx > 0f) minOf(rowWidthPx * 0.28f, with(density) { 104.dp.toPx() }) else 220f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
            .pointerInput(track.videoId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    if (startX < edgeGuardPx || (rowWidthPx > 0f && startX > rowWidthPx - edgeGuardPx)) {
                        return@awaitEachGesture
                    }
                    crossedThreshold = false
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + overSlop).coerceIn(-maxSwipePx, maxSwipePx))
                        }
                    }
                    if (drag != null) {
                        currentTapCoordinator.cancel(currentRowKey)
                        var completed = true
                        try {
                            horizontalDrag(drag.id) { change ->
                                val dx = change.positionChange().x
                                if (dx != 0f) {
                                    change.consume()
                                    val next = (offsetX.value + dx).coerceIn(-maxSwipePx, maxSwipePx)
                                    val nowCrossed = abs(next) >= thresholdPx
                                    if (nowCrossed && !crossedThreshold) {
                                        crossedThreshold = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else if (!nowCrossed && crossedThreshold) {
                                        crossedThreshold = false
                                    }
                                    scope.launch { offsetX.snapTo(next) }
                                }
                            }
                        } catch (_: Throwable) {
                            completed = false
                        }
                        val finalOffset = offsetX.value
                        if (completed && abs(finalOffset) >= thresholdPx) {
                            if (finalOffset > 0f) currentOnSwipeRight() else currentOnSwipeLeft()
                        }
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    }
                }
            }
    ) {
        val swipeDirection by remember {
            derivedStateOf {
                val dx = offsetX.value
                when {
                    dx > 2f -> 1
                    dx < -2f -> -1
                    else -> 0
                }
            }
        }
        val activeThreshold by remember(thresholdPx) {
            derivedStateOf { abs(offsetX.value) >= thresholdPx }
        }
        val iconScale by animateFloatAsState(
            targetValue = if (activeThreshold) 1.15f else 0.85f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 480f),
            label = "swipeIconScale"
        )
        val safeIconScale = iconScale.coerceAtLeast(0.5f)

        // Transparent swipe action icon reveals (accent-tinted icons only, no dark backdrop or text)
        if (swipeDirection > 0) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = LinIcons.PlayNext,
                    contentDescription = "Play Next",
                    tint = accentColor.copy(alpha = if (activeThreshold) 1f else 0.55f),
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = safeIconScale
                            scaleY = safeIconScale
                        }
                )
            }
        } else if (swipeDirection < 0) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = LinIcons.AddToQueue,
                    contentDescription = "Add to Queue",
                    tint = accentColor.copy(alpha = if (activeThreshold) 1f else 0.55f),
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = safeIconScale
                            scaleY = safeIconScale
                        }
                )
            }
        }

        val hasThumbSlot = showThumbnail && !track.thumbnailUrl.isNullOrBlank()
        val thumbVisibilityState = animateFloatAsState(
            targetValue = if (hideThumbnail) 0f else 1f,
            animationSpec = if (hideThumbnail) {
                tween(durationMillis = 190, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 560, delayMillis = 70, easing = FastOutSlowInEasing)
            },
            label = "rowThumbVisibility"
        )
        val previewProgressState = animateFloatAsState(
            targetValue = if (isPreviewed) 1f else 0f,
            animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
            label = "rowPreviewProgress"
        )
        val thumbSlotPx = with(density) { 54.dp.toPx() }
        val thumbSlideOutPx = with(density) { 10.dp.toPx() }
        val previewIndentPx = with(density) { 10.dp.toPx() }
        val accentBarWidthPx = with(density) { 2.dp.toPx() }
        val accentBarGapPx = with(density) { 8.dp.toPx() }
        val accentBarHeightPx = with(density) { 26.dp.toPx() }

        // Foreground track content remains transparent throughout swipe (GPU-only animations prevent per-frame relayout)
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (currentSelectionMode && currentOnToggleSelect != null) {
                            currentTapCoordinator.cancel(currentRowKey)
                            currentOnToggleSelect?.invoke()
                        } else {
                            currentTapCoordinator.onTap(
                                rowKey = currentRowKey,
                                onSingleClick = { currentOnClick() },
                                onDoubleClick = currentOnDoubleClick?.let { dbl -> { dbl() } },
                                onTripleClick = currentOnTripleClick?.let { tri ->
                                    {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        tri()
                                    }
                                }
                            )
                        }
                    },
                    onDoubleClick = null,
                    onLongClick = {
                        currentTapCoordinator.cancel(currentRowKey)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (currentSelectionMode && currentOnToggleSelect != null) {
                            currentOnToggleSelect?.invoke()
                        } else {
                            currentOnMoreClick()
                        }
                    }
                )
                .padding(vertical = 7.dp, horizontal = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (hasThumbSlot) 44.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectionMode) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(if (isSelected) accentColor else Color.Transparent)
                                .border(1.5.dp, if (isSelected) accentColor else linColors.textSecondary, LinSharpShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = LinIcons.Check,
                                    contentDescription = "Selected",
                                    tint = Color.Black,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    if (hasThumbSlot) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer {
                                    val p = thumbVisibilityState.value
                                    alpha = p
                                    val s = 0.86f + 0.14f * p
                                    scaleX = s
                                    scaleY = s
                                    translationX = -(1f - p) * thumbSlideOutPx
                                }
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(LinSharpShape)
                                    .background(linColors.queueItemBg)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                val pr = previewProgressState.value
                                val thumbShift = if (hasThumbSlot) -(1f - thumbVisibilityState.value) * thumbSlotPx else 0f
                                translationX = thumbShift + (previewIndentPx + accentBarWidthPx + accentBarGapPx) * pr
                            }
                            .drawBehind {
                                val pr = previewProgressState.value
                                if (pr > 0.01f) {
                                    val barX = -(accentBarGapPx + accentBarWidthPx)
                                    val barTop = (size.height - accentBarHeightPx) / 2f
                                    drawRect(
                                        color = accentColor.copy(alpha = pr),
                                        topLeft = Offset(barX, barTop),
                                        size = Size(accentBarWidthPx, accentBarHeightPx)
                                    )
                                }
                            }
                    ) {
                        Text(
                            text = track.title,
                            style = LinTextStyles.navItem.copy(
                                fontSize = 15.5.sp,
                                lineHeight = 20.sp,
                                fontWeight = if (isCurrentTrack || isPreviewed) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isCurrentTrack) accentColor else linColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val metaParts = buildList {
                            if (track.artistDisplay.isNotBlank()) add(track.artistDisplay)
                            val dur = track.formattedDuration
                            if (dur.isNotBlank()) add(dur)
                        }
                        if (metaParts.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (track.isExplicit) {
                                    Text(
                                        text = "E",
                                        style = LinTextStyles.tagPill.copy(fontSize = 10.sp),
                                        color = linColors.btnInvertText,
                                        modifier = Modifier
                                            .background(linColors.textSecondary)
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                if (isDownloaded) {
                                    Icon(
                                        imageVector = LinIcons.Download,
                                        contentDescription = "Downloaded",
                                        tint = accentColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(
                                    text = metaParts.joinToString(" · "),
                                    style = LinTextStyles.subtitle.copy(fontSize = 12.sp, lineHeight = 15.sp),
                                    color = linColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    IconButton(onClick = onMoreClick, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = LinIcons.MoreVertical,
                            contentDescription = "Track options",
                            tint = linColors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (onDoubleClick != null) {
                    Text(
                        text = if (onTripleClick != null) "2× play · 3× mix" else "double-tap",
                        style = LinTextStyles.tagPill.copy(fontSize = 10.sp),
                        color = linColors.textSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 34.dp)
                            .graphicsLayer { alpha = previewProgressState.value }
                    )
                }
            }

            if (track.tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    track.tags.forEach { tag ->
                        LinTagPill(
                            text = "#$tag",
                            active = activeTagFilter == tag,
                            onClick = { onTagClick(tag) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Swipeable Playlist Row in the playlists library:
 * - Right Swipe -> Play Playlist Next
 * - Left Swipe -> Add Playlist to Queue
 */
@Composable
fun SwipeablePlaylistRow(
    playlist: PlaylistSummary,
    activeTagFilter: String? = null,
    onClick: () -> Unit,
    onSwipeRightPlayNext: () -> Unit,
    onSwipeLeftAddToQueue: () -> Unit,
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val offsetX = remember { Animatable(0f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    var crossed by remember { mutableStateOf(false) }

    val edgeGuardPx = with(density) { 24.dp.toPx() }
    val maxSwipePx = with(density) { 160.dp.toPx() }
    val thresholdPx = if (rowWidthPx > 0f) minOf(rowWidthPx * 0.28f, with(density) { 104.dp.toPx() }) else 220f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
            .pointerInput(playlist.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    if (startX < edgeGuardPx || (rowWidthPx > 0f && startX > rowWidthPx - edgeGuardPx)) {
                        return@awaitEachGesture
                    }
                    crossed = false
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + overSlop).coerceIn(-maxSwipePx, maxSwipePx))
                        }
                    }
                    if (drag != null) {
                        var completed = true
                        try {
                            horizontalDrag(drag.id) { change ->
                                val dx = change.positionChange().x
                                if (dx != 0f) {
                                    change.consume()
                                    val next = (offsetX.value + dx).coerceIn(-maxSwipePx, maxSwipePx)
                                    val nowCrossed = abs(next) >= thresholdPx
                                    if (nowCrossed && !crossed) {
                                        crossed = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else if (!nowCrossed && crossed) {
                                        crossed = false
                                    }
                                    scope.launch { offsetX.snapTo(next) }
                                }
                            }
                        } catch (_: Throwable) {
                            completed = false
                        }
                        val finalOffset = offsetX.value
                        if (completed && abs(finalOffset) >= thresholdPx) {
                            if (finalOffset > 0f) onSwipeRightPlayNext() else onSwipeLeftAddToQueue()
                        }
                        scope.launch {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                }
            }
    ) {
        val currentOffset = offsetX.value
        val activeThreshold = abs(currentOffset) >= thresholdPx

        if (currentOffset > 2f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (activeThreshold) linColors.btnInvertBg else linColors.queueItemHover)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tint = if (activeThreshold) linColors.btnInvertText else linColors.textPrimary
                Icon(LinIcons.PlayNext, null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("PLAY PLAYLIST NEXT", style = LinTextStyles.brand, color = tint)
            }
        } else if (currentOffset < -2f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (activeThreshold) linColors.btnInvertBg else linColors.queueItemHover)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                val tint = if (activeThreshold) linColors.btnInvertText else linColors.textPrimary
                Text("ADD PLAYLIST TO QUEUE", style = LinTextStyles.brand, color = tint)
                Spacer(Modifier.width(10.dp))
                Icon(LinIcons.AddToQueue, null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }

        Row(
            modifier = Modifier
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .fillMaxWidth()
                .background(if (abs(currentOffset) > 1f) linColors.background else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(linColors.glassBorder)
            ) {
                if (!playlist.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = LinTextStyles.trackTitle,
                    color = linColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = playlist.subtitle ?: playlist.trackCount?.let { "$it tracks" }
                if (!sub.isNullOrBlank()) {
                    Text(
                        text = sub,
                        style = LinTextStyles.subtitle,
                        color = linColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (playlist.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        playlist.tags.forEach { tag ->
                            LinTagPill("#$tag", active = activeTagFilter == tag, onClick = { onTagClick(tag) })
                        }
                    }
                }
            }
        }
    }
}
