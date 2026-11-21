package com.lin.music.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.linLabel

@Composable
fun LinTagPill(
    text: String,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val bgColor by animateColorAsState(
        targetValue = if (active) linColors.tagActiveBg else linColors.glassBorder,
        animationSpec = tween(180),
        label = "tagBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (active) linColors.tagActiveText else linColors.textSecondary,
        animationSpec = tween(180),
        label = "tagText"
    )
    Text(
        text = linLabel(text.lowercase()),
        style = LinTextStyles.tagPill,
        color = textColor,
        modifier = modifier
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun LinInvertedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val linColors = LinTheme.colors
    Box(
        modifier = modifier
            .background(if (enabled) linColors.btnInvertBg else linColors.glassBorder)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = linLabel(text),
            style = LinTextStyles.subtitle.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) linColors.btnInvertText else linColors.textSecondary
        )
    }
}

@Composable
fun LinOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val linColors = LinTheme.colors
    Box(
        modifier = modifier
            .border(1.dp, if (enabled) linColors.textPrimary.copy(alpha = 0.35f) else linColors.glassBorder)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = linLabel(text),
            style = LinTextStyles.subtitle,
            color = if (enabled) linColors.textPrimary else linColors.textSecondary
        )
    }
}

/**
 * Sharp-cornered architectural toggle switch matching LIN's 0dp editorial aesthetic.
 */
@Composable
fun LinSquareToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val linColors = LinTheme.colors
    val trackBg by animateColorAsState(
        targetValue = if (checked) linColors.btnInvertBg else Color.Transparent,
        animationSpec = tween(durationMillis = 160),
        label = "squareToggleTrackBg"
    )
    val trackBorder by animateColorAsState(
        targetValue = if (checked) linColors.textPrimary else linColors.textSecondary.copy(alpha = 0.55f),
        animationSpec = tween(durationMillis = 160),
        label = "squareToggleTrackBorder"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) linColors.btnInvertText else linColors.textSecondary,
        animationSpec = tween(durationMillis = 160),
        label = "squareToggleThumbColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 25.dp else 5.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f),
        label = "squareToggleThumbOffset"
    )

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .background(trackBg)
            .border(1.dp, trackBorder)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.coerceAtLeast(0.dp))
                .size(14.dp)
                .background(thumbColor)
        )
    }
}

/**
 * 2dp hairline LIN progress scrubber matching .progress-bar in frontend_css/styles.css
 * with a generous 32dp vertical hit area for accurate mobile scrubbing.
 */
@Composable
fun LinScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 2.dp
) {
    val linColors = LinTheme.colors
    var widthPx by remember { mutableFloatStateOf(1f) }
    var scrubbingFraction by remember { mutableFloatStateOf(-1f) }

    val activeFraction = if (scrubbingFraction >= 0f) {
        scrubbingFraction
    } else if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val isDragging = scrubbingFraction >= 0f
    val animatedHeight by animateDpAsState(
        targetValue = if (isDragging) trackHeight + 2.dp else trackHeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 460f),
        label = "scrubberHeight"
    )
    val safeHeight = if (animatedHeight > 1.dp) animatedHeight else 1.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0L) {
                        val frac = (offset.x / widthPx).coerceIn(0f, 1f)
                        onSeekTo((frac * durationMs).toLong())
                    }
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (durationMs > 0L) {
                            scrubbingFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (durationMs > 0L) {
                            scrubbingFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        if (scrubbingFraction >= 0f && durationMs > 0L) {
                            onSeekTo((scrubbingFraction * durationMs).toLong())
                        }
                        scrubbingFraction = -1f
                    },
                    onDragCancel = {
                        scrubbingFraction = -1f
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(safeHeight)
                .background(linColors.progressTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(activeFraction)
                    .height(safeHeight)
                    .background(linColors.textPrimary)
            )
        }
        if (isDragging) {
            Box(
                modifier = Modifier
                    .offset(x = ((maxWidth - 2.dp) * activeFraction).coerceAtLeast(0.dp))
                    .width(2.dp)
                    .height(12.dp)
                    .background(linColors.textPrimary)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinStatusMarquee(
    message: String,
    accentColor: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(accentColor)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = linLabel(message.lowercase()),
            style = LinTextStyles.subtitle.copy(fontSize = 12.sp),
            color = linColors.textPrimary,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 200,
                    velocity = 48.dp
                )
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = linLabel(actionLabel.lowercase()),
                style = LinTextStyles.tagPill.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = linColors.btnInvertText,
                modifier = Modifier
                    .background(linColors.btnInvertBg)
                    .clickable {
                        onAction()
                        onDismiss()
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun LinToastBanner(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(linColors.glassBg)
            .border(1.dp, linColors.textPrimary.copy(alpha = 0.25f))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = message,
            style = LinTextStyles.subtitle,
            color = linColors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = actionLabel.uppercase(),
                style = LinTextStyles.subtitle.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                color = linColors.btnInvertText,
                modifier = Modifier
                    .background(linColors.btnInvertBg)
                    .clickable {
                        onAction()
                        onDismiss()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

fun highResArtworkUrl(url: String?): String? = normalizeThumbnailUrl(url, "=w1080-h1080-l90-rj")

fun previewArtworkUrl(url: String?): String? = normalizeThumbnailUrl(url, "=w544-h544-l90-rj")

private fun normalizeThumbnailUrl(url: String?, googleSizeSuffix: String): String? {
    if (url.isNullOrBlank()) return url
    val normalized = if (url.startsWith("//")) "https:$url" else url
    return when {
        normalized.contains("googleusercontent.com") || normalized.contains("ggpht.com") -> {
            val wHPattern = Regex("=w\\d+-h\\d+[^\"\\s]*")
            val sPattern = Regex("=s\\d+[^\"\\s]*")
            when {
                wHPattern.containsMatchIn(normalized) ->
                    normalized.replace(wHPattern, googleSizeSuffix)
                sPattern.containsMatchIn(normalized) ->
                    normalized.replace(sPattern, googleSizeSuffix)
                else -> normalized
            }
        }
        normalized.contains("i.ytimg.com") || normalized.contains("img.youtube.com") -> {
            normalized
                .replace("/default.jpg", "/sddefault.jpg")
                .replace("/mqdefault.jpg", "/sddefault.jpg")
                .replace("/hqdefault.jpg", "/sddefault.jpg")
        }
        else -> normalized
    }
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "$mins:${secs.toString().padStart(2, '0')}"
}
