package com.lin.music.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
import kotlinx.coroutines.delay

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

    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.98f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "hoverArtAlpha"
    )
    val slideXPx by animateFloatAsState(
        targetValue = if (visible) 0f else with(density) { -16.dp.toPx() },
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "hoverArtSlideX"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.93f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "hoverArtScale"
    )

    val minTopPx = with(density) { 12.dp.toPx() }
    val maxTopPx = (viewportHeightPx - artSizePx - with(density) { 120.dp.toPx() }).coerceAtLeast(minTopPx)
    val targetTopPx = (centerYPx - artSizePx / 2f).coerceIn(minTopPx, maxTopPx)

    val displayUrl = if (visible) imageUrl else lastUrl
    if (alpha > 0.01f && !displayUrl.isNullOrBlank()) {
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
                        val safeScale = scale.coerceAtLeast(0.01f)
                        this.alpha = alpha.coerceIn(0f, 1f)
                        this.scaleX = safeScale
                        this.scaleY = safeScale
                        this.translationX = slideXPx
                        this.translationY = targetTopPx
                    }
                    .size(artSizeDp)
                    .shadow(20.dp, LinSharpShape)
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
        urls.mapNotNull { previewArtworkUrl(it)?.takeIf(String::isNotBlank) }.distinct().take(8)
    }
    LaunchedEffect(normalizedUrls, isScrolling) {
        if (isScrolling || normalizedUrls.isEmpty()) return@LaunchedEffect
        delay(250L)
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
