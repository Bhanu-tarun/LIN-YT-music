package com.lin.music.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ScrollbarMetrics(
    val thumbHeightPx: Float,
    val thumbOffsetPx: Float,
    val scrollFraction: Float
)

fun calculateScrollbarMetrics(
    totalItemsCount: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    visibleItemsCount: Int,
    firstVisibleItemSize: Int,
    trackHeightPx: Float,
    minThumbHeightPx: Float
): ScrollbarMetrics {
    if (totalItemsCount <= 0 || trackHeightPx <= 0f) {
        return ScrollbarMetrics(thumbHeightPx = 0f, thumbOffsetPx = 0f, scrollFraction = 0f)
    }
    val safeVisible = visibleItemsCount.coerceIn(1, totalItemsCount)
    val maxScrollable = (totalItemsCount - safeVisible).coerceAtLeast(1)
    val itemFraction = if (firstVisibleItemSize > 0) {
        (firstVisibleItemScrollOffset.toFloat() / firstVisibleItemSize.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val scrollFraction = ((firstVisibleItemIndex + itemFraction) / maxScrollable.toFloat()).coerceIn(0f, 1f)
    val rawThumb = (safeVisible.toFloat() / totalItemsCount.toFloat()) * trackHeightPx
    val clampedMin = minThumbHeightPx.coerceAtMost(trackHeightPx)
    val thumbHeightPx = rawThumb.coerceIn(clampedMin, (trackHeightPx * 0.6f).coerceAtLeast(clampedMin))
    val thumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f) * scrollFraction
    return ScrollbarMetrics(
        thumbHeightPx = thumbHeightPx,
        thumbOffsetPx = thumbOffsetPx,
        scrollFraction = scrollFraction
    )
}

fun scrollbarTargetIndex(
    touchYPx: Float,
    trackHeightPx: Float,
    totalItemsCount: Int
): Int {
    if (totalItemsCount <= 1 || trackHeightPx <= 0f) return 0
    val fraction = (touchYPx / trackHeightPx).coerceIn(0f, 1f)
    return (fraction * (totalItemsCount - 1)).roundToInt().coerceIn(0, totalItemsCount - 1)
}

@Composable
fun LinLazyColumnScrollbar(
    listState: LazyListState,
    accentColor: Color,
    modifier: Modifier = Modifier,
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 172.dp,
    labelProvider: ((Int) -> String?)? = null
) {
    val canScroll by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    if (!canScroll) return

    val totalItemsCount by remember(listState) {
        derivedStateOf { listState.layoutInfo.totalItemsCount }
    }
    val visibleItemsCount by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.size }
    }
    val firstVisibleIndex by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    val firstVisibleOffset by remember(listState) {
        derivedStateOf { listState.firstVisibleItemScrollOffset }
    }
    val firstVisibleSize by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0 }
    }

    val scope = rememberCoroutineScope()
    var scrollJob by remember(listState) { mutableStateOf<Job?>(null) }

    LinScrollbarTrack(
        totalItemsCount = totalItemsCount,
        firstVisibleIndex = firstVisibleIndex,
        firstVisibleOffset = firstVisibleOffset,
        visibleItemsCount = visibleItemsCount,
        firstVisibleSize = firstVisibleSize,
        accentColor = accentColor,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        labelProvider = labelProvider,
        onScrollToIndex = { targetIndex ->
            scrollJob?.cancel()
            scrollJob = scope.launch {
                listState.scrollToItem(targetIndex)
            }
        },
        modifier = modifier
    )
}

@Composable
fun LinLazyGridScrollbar(
    gridState: LazyGridState,
    accentColor: Color,
    modifier: Modifier = Modifier,
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 172.dp,
    labelProvider: ((Int) -> String?)? = null
) {
    val canScroll by remember(gridState) {
        derivedStateOf { gridState.canScrollForward || gridState.canScrollBackward }
    }
    if (!canScroll) return

    val totalItemsCount by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.totalItemsCount }
    }
    val visibleItemsCount by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.size }
    }
    val firstVisibleIndex by remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemIndex }
    }
    val firstVisibleOffset by remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemScrollOffset }
    }
    val firstVisibleSize by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0 }
    }

    val scope = rememberCoroutineScope()
    var scrollJob by remember(gridState) { mutableStateOf<Job?>(null) }

    LinScrollbarTrack(
        totalItemsCount = totalItemsCount,
        firstVisibleIndex = firstVisibleIndex,
        firstVisibleOffset = firstVisibleOffset,
        visibleItemsCount = visibleItemsCount,
        firstVisibleSize = firstVisibleSize,
        accentColor = accentColor,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        labelProvider = labelProvider,
        onScrollToIndex = { targetIndex ->
            scrollJob?.cancel()
            scrollJob = scope.launch {
                gridState.scrollToItem(targetIndex)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun LinScrollbarTrack(
    totalItemsCount: Int,
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    visibleItemsCount: Int,
    firstVisibleSize: Int,
    accentColor: Color,
    topPadding: Dp,
    bottomPadding: Dp,
    labelProvider: ((Int) -> String?)?,
    onScrollToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { 28.dp.toPx() }
    val chipHalfHeightPx = with(density) { 12.dp.toPx() }

    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableIntStateOf(0) }
    var lastRequestedIndex by remember { mutableIntStateOf(-1) }
    var chipHeightPx by remember { mutableFloatStateOf(0f) }

    val latestTotalItems by rememberUpdatedState(totalItemsCount)
    val latestOnScrollToIndex by rememberUpdatedState(onScrollToIndex)

    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.42f,
        animationSpec = tween(durationMillis = 150),
        label = "linScrollbarAlpha"
    )
    val thumbWidthDp by animateDpAsState(
        targetValue = if (isDragging) 5.dp else 3.dp,
        animationSpec = tween(durationMillis = 120),
        label = "linScrollbarWidth"
    )

    val metrics = calculateScrollbarMetrics(
        totalItemsCount = totalItemsCount,
        firstVisibleItemIndex = firstVisibleIndex,
        firstVisibleItemScrollOffset = firstVisibleOffset,
        visibleItemsCount = visibleItemsCount,
        firstVisibleItemSize = firstVisibleSize,
        trackHeightPx = trackHeightPx,
        minThumbHeightPx = minThumbHeightPx
    )

    val activeLabel = if (isDragging) labelProvider?.invoke(dragIndex) else null

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        if (isDragging && activeLabel != null) {
            val thumbCenterY = metrics.thumbOffsetPx + metrics.thumbHeightPx / 2f
            val halfH = if (chipHeightPx > 0f) chipHeightPx / 2f else chipHalfHeightPx
            val maxTranslation = (trackHeightPx - halfH * 2f).coerceAtLeast(0f)
            val translationY = (thumbCenterY - halfH).coerceIn(0f, maxTranslation)
            Text(
                text = activeLabel,
                style = LinTextStyles.tagPill,
                color = linColors.textPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 26.dp)
                    .onSizeChanged { chipHeightPx = it.height.toFloat() }
                    .graphicsLayer { this.translationY = translationY }
                    .background(linColors.background.copy(alpha = 0.92f))
                    .border(1.dp, accentColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(20.dp)
                .fillMaxHeight()
                .onSizeChanged { trackHeightPx = it.height.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        try {
                            val startTarget = scrollbarTargetIndex(down.position.y, trackHeightPx, latestTotalItems)
                            dragIndex = startTarget
                            lastRequestedIndex = startTarget
                            latestOnScrollToIndex(startTarget)
                            down.consume()

                            verticalDrag(down.id) { change ->
                                val target = scrollbarTargetIndex(change.position.y, trackHeightPx, latestTotalItems)
                                dragIndex = target
                                if (target != lastRequestedIndex) {
                                    lastRequestedIndex = target
                                    latestOnScrollToIndex(target)
                                }
                                change.consume()
                            }
                        } finally {
                            isDragging = false
                            lastRequestedIndex = -1
                        }
                    }
                }
        ) {
            val idleThumbColor = linColors.textSecondary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackW = 1.dp.toPx()
                val rightInset = 3.dp.toPx()
                val activeW = thumbWidthDp.toPx()
                val trackLeft = size.width - rightInset - activeW / 2f - trackW / 2f
                drawRect(
                    color = idleThumbColor.copy(alpha = 0.15f),
                    topLeft = Offset(trackLeft, 0f),
                    size = Size(trackW, size.height)
                )
                if (metrics.thumbHeightPx > 0f) {
                    val thumbColor = if (isDragging) accentColor else idleThumbColor
                    drawRect(
                        color = thumbColor.copy(alpha = thumbAlpha),
                        topLeft = Offset(size.width - rightInset - activeW, metrics.thumbOffsetPx),
                        size = Size(activeW, metrics.thumbHeightPx)
                    )
                }
            }
        }
    }
}
