package com.lin.music.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared floating single-tap track cover preview rendered IN FRONT of track rows
 * without intercepting pointer events, preserving smooth spring entry and fade-out.
 */
@Composable
fun LinTrackArtHoverOverlay(
    imageUrl: String?,
    centerYPx: Float,
    viewportHeightPx: Float
) {
    val linColors = LinTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val artSizeDp = 160.dp
    val artSizePx = with(density) { artSizeDp.toPx() }
    val visible = !imageUrl.isNullOrBlank()

    var lastUrl by remember { mutableStateOf(imageUrl) }
    if (!imageUrl.isNullOrBlank()) {
        lastUrl = imageUrl
    }

    val progressState = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) {
            tween(durationMillis = 220, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 170, easing = FastOutSlowInEasing)
        },
        label = "hoverArtProgress"
    )

    val minTopPx = with(density) { 12.dp.toPx() }
    val maxTopPx = (viewportHeightPx - artSizePx - with(density) { 120.dp.toPx() }).coerceAtLeast(minTopPx)
    val targetTopPx = (centerYPx - artSizePx / 2f).coerceIn(minTopPx, maxTopPx)
    val slideDistancePx = with(density) { 14.dp.toPx() }
    val shadowElevationPx = with(density) { 12.dp.toPx() }

    val animatedTopPx = remember { Animatable(targetTopPx) }
    var wasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(visible, targetTopPx) {
        if (visible) {
            if (!wasVisible) {
                animatedTopPx.snapTo(targetTopPx)
            } else {
                animatedTopPx.animateTo(targetTopPx, tween(180, easing = FastOutSlowInEasing))
            }
        }
        wasVisible = visible
    }

    val displayUrl = if (visible) imageUrl else lastUrl
    if (!displayUrl.isNullOrBlank()) {
        val previewUrl = remember(displayUrl) { previewArtworkUrl(displayUrl) }
        val request = remember(previewUrl) {
            ImageRequest.Builder(context)
                .data(previewUrl)
                .size(544)
                .precision(Precision.EXACT)
                .crossfade(false)
                .build()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 20.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            AsyncImage(
                model = request,
                contentDescription = "Track artwork preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .graphicsLayer {
                        val p = progressState.value
                        val safeScale = (0.92f + 0.08f * p).coerceAtLeast(0.01f)
                        this.alpha = (p * 0.99f).coerceIn(0f, 1f)
                        this.scaleX = safeScale
                        this.scaleY = safeScale
                        this.translationX = -(1f - p) * slideDistancePx
                        this.translationY = animatedTopPx.value
                        this.shadowElevation = shadowElevationPx * p
                        this.shape = LinSharpShape
                        this.clip = false
                    }
                    .size(artSizeDp)
                    .background(linColors.queueItemBg)
                    .border(1.dp, linColors.glassBorder)
            )
        }
    }
}

/**
 * Warms Coil's shared MemoryCache with the exact request dimensions used by
 * [LinTrackArtHoverOverlay] so single-tap previews appear in 0ms.
 */
@Composable
fun PreloadPreviewArtworks(
    urls: List<String?>,
    isScrolling: Boolean = false
) {
    val context = LocalContext.current
    val loader = remember(context) { SingletonImageLoader.get(context) }
    val normalizedUrls = remember(urls) {
        urls.mapNotNull { previewArtworkUrl(it)?.takeIf(String::isNotBlank) }.distinct().take(14)
    }
    LaunchedEffect(normalizedUrls, isScrolling) {
        if (isScrolling || normalizedUrls.isEmpty()) return@LaunchedEffect
        delay(120L)
        normalizedUrls.forEach { url ->
            val req = ImageRequest.Builder(context)
                .data(url)
                .size(544)
                .precision(Precision.EXACT)
                .build()
            loader.enqueue(req)
        }
    }
}

/**
 * Coordinates single / double / triple taps across rows in a list without letting
 * `combinedClickable`'s built-in double-tap swallow a third tap or letting a pending
 * timer from row A fire after the user starts tapping row B, scrolling, or swiping.
 */
class MultiTapCoordinator internal constructor(
    private val tapWindowMs: Long = DEFAULT_TAP_WINDOW_MS,
    private val schedule: (delayMs: Long, block: () -> Unit) -> () -> Unit
) {
    constructor(
        scope: CoroutineScope,
        tapWindowMs: Long = DEFAULT_TAP_WINDOW_MS
    ) : this(
        tapWindowMs = tapWindowMs,
        schedule = { delayMs, block ->
            val job: Job = scope.launch {
                delay(delayMs)
                block()
            }
            val cancelFn: () -> Unit = { job.cancel() }
            cancelFn
        }
    )

    private var activeRowKey: String? = null
    private var tapCount: Int = 0
    private var cancelPending: (() -> Unit)? = null

    fun cancel(rowKey: String? = null) {
        if (rowKey != null && activeRowKey != rowKey) return
        cancelPending?.invoke()
        cancelPending = null
        activeRowKey = null
        tapCount = 0
    }

    fun onTap(
        rowKey: String,
        onSingleClick: () -> Unit,
        onDoubleClick: (() -> Unit)? = null,
        onTripleClick: (() -> Unit)? = null
    ) {
        if (onDoubleClick == null && onTripleClick == null) {
            cancel()
            onSingleClick()
            return
        }
        if (activeRowKey != rowKey) {
            cancel()
            activeRowKey = rowKey
        } else {
            cancelPending?.invoke()
            cancelPending = null
        }
        tapCount += 1
        when {
            tapCount == 1 -> {
                cancelPending = schedule(tapWindowMs) {
                    if (activeRowKey == rowKey && tapCount == 1) {
                        cancelPending = null
                        activeRowKey = null
                        tapCount = 0
                        onSingleClick()
                    }
                }
            }

            tapCount == 2 -> {
                if (onTripleClick == null) {
                    cancelPending = null
                    activeRowKey = null
                    tapCount = 0
                    (onDoubleClick ?: onSingleClick)()
                } else {
                    cancelPending = schedule(tapWindowMs) {
                        if (activeRowKey == rowKey && tapCount == 2) {
                            cancelPending = null
                            activeRowKey = null
                            tapCount = 0
                            (onDoubleClick ?: onSingleClick)()
                        }
                    }
                }
            }

            else -> {
                cancelPending = null
                activeRowKey = null
                tapCount = 0
                (onTripleClick ?: onDoubleClick ?: onSingleClick)()
            }
        }
    }

    companion object {
        const val DEFAULT_TAP_WINDOW_MS = 260L
    }
}

@Composable
fun rememberMultiTapCoordinator(
    tapWindowMs: Long = MultiTapCoordinator.DEFAULT_TAP_WINDOW_MS
): MultiTapCoordinator {
    val scope = rememberCoroutineScope()
    val coordinator = remember(scope, tapWindowMs) {
        MultiTapCoordinator(scope = scope, tapWindowMs = tapWindowMs)
    }
    DisposableEffect(coordinator) {
        onDispose { coordinator.cancel() }
    }
    return coordinator
}
