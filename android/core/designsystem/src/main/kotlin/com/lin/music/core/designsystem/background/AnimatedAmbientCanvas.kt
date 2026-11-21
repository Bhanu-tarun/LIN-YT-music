package com.lin.music.core.designsystem.background

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.model.AmbientMode
import com.lin.music.core.model.AmbientReactivity
import com.lin.music.core.model.AudioEnergy
import com.lin.music.core.model.TrackArtworkPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DEFAULT_BLOB_COLORS = listOf(
    Color(0xFF00FF80),
    Color(0xFFFF0080),
    Color(0xFF0080FF),
    Color(0xFFFF8000)
)

/**
 * Battery-optimized living audio-reactive 4-blob ambient canvas:
 * - Uses smooth 6-stop Gaussian-falloff radial gradients instead of an offscreen GPU RenderEffect blur
 * - Paces at ~30 FPS in audio-reactive mode, ~15 FPS in ambient drift, and 0 FPS when paused or backgrounded
 * - Reads animated state strictly inside [Canvas] so parent composables never recompose on frame ticks
 */
@Composable
fun AnimatedAmbientCanvas(
    palette: TrackArtworkPalette?,
    audioEnergyFlow: StateFlow<AudioEnergy>,
    mode: AmbientMode,
    isPlaying: Boolean,
    isActive: Boolean = true,
    reactivity: AmbientReactivity = AmbientReactivity.EXPRESSIVE,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    if (!isActive || mode == AmbientMode.PURE_OLED) {
        Box(modifier = modifier.fillMaxSize().background(linColors.background))
        return
    }

    val targetColors = remember(palette) {
        palette?.colors?.takeIf { it.size >= 4 }?.map { Color(it) } ?: DEFAULT_BLOB_COLORS
    }

    val c1State = animateColorAsState(targetColors[0], tween(1400, easing = FastOutSlowInEasing), label = "blob1")
    val c2State = animateColorAsState(targetColors[1], tween(1400, easing = FastOutSlowInEasing), label = "blob2")
    val c3State = animateColorAsState(targetColors[2], tween(1400, easing = FastOutSlowInEasing), label = "blob3")
    val c4State = animateColorAsState(targetColors[3], tween(1400, easing = FastOutSlowInEasing), label = "blob4")

    val lifecycleOwner = LocalLifecycleOwner.current
    var isStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isStarted = true
                Lifecycle.Event.ON_STOP -> isStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val phaseSecState = remember { mutableFloatStateOf(0f) }
    val smoothBassState = remember { mutableFloatStateOf(0f) }
    val smoothTotalState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isStarted, isPlaying, mode, reactivity) {
        if (!isStarted) return@LaunchedEffect

        if (!isPlaying) {
            while (this.isActive && (smoothBassState.floatValue > 0.002f || smoothTotalState.floatValue > 0.002f)) {
                delay(33L)
                smoothBassState.floatValue *= 0.80f
                smoothTotalState.floatValue *= 0.80f
            }
            smoothBassState.floatValue = 0f
            smoothTotalState.floatValue = 0f
            return@LaunchedEffect
        }

        val frameMs = if (mode == AmbientMode.AUDIO_REACTIVE) 33L else 66L
        var lastTickMs = SystemClock.elapsedRealtime()
        while (this.isActive) {
            delay(frameMs)
            val nowMs = SystemClock.elapsedRealtime()
            val dtSec = ((nowMs - lastTickMs).coerceIn(8L, 100L)) / 1000f
            lastTickMs = nowMs
            phaseSecState.floatValue = (phaseSecState.floatValue + dtSec) % 3600f

            if (mode == AmbientMode.AUDIO_REACTIVE) {
                val raw = audioEnergyFlow.value
                val targetBass = (raw.bass * reactivity.energyScale).coerceIn(0f, 1.35f)
                val targetTotal = (raw.total * reactivity.energyScale).coerceIn(0f, 1.35f)
                val curBass = smoothBassState.floatValue
                val curTotal = smoothTotalState.floatValue
                val bassAlpha = if (targetBass >= curBass) {
                    (0.45f * reactivity.attackScale).coerceIn(0.10f, 0.85f)
                } else {
                    (0.16f * reactivity.decayScale).coerceIn(0.05f, 0.55f)
                }
                val totalAlpha = if (targetTotal >= curTotal) {
                    (0.38f * reactivity.attackScale).coerceIn(0.08f, 0.80f)
                } else {
                    (0.14f * reactivity.decayScale).coerceIn(0.04f, 0.50f)
                }
                smoothBassState.floatValue = (curBass + (targetBass - curBass) * bassAlpha).coerceIn(0f, 1.35f)
                smoothTotalState.floatValue = (curTotal + (targetTotal - curTotal) * totalAlpha).coerceIn(0f, 1.35f)
            } else {
                if (smoothBassState.floatValue > 0.002f) {
                    smoothBassState.floatValue *= 0.85f
                } else {
                    smoothBassState.floatValue = 0f
                }
                if (smoothTotalState.floatValue > 0.002f) {
                    smoothTotalState.floatValue *= 0.85f
                } else {
                    smoothTotalState.floatValue = 0f
                }
            }
        }
    }

    val blend = if (linColors.isDark) BlendMode.Plus else BlendMode.Darken
    val baseOpacity = if (linColors.isDark) 0.42f else 0.32f
    val maxOpacity = if (linColors.isDark) 0.85f else 0.64f

    val veilBrush = remember(linColors.background, linColors.isDark) {
        val topAlpha = if (linColors.isDark) 0.14f else 0.16f
        val bottomAlpha = if (linColors.isDark) 0.34f else 0.36f
        Brush.verticalGradient(
            listOf(
                linColors.background.copy(alpha = topAlpha),
                linColors.background.copy(alpha = bottomAlpha)
            )
        )
    }

    val twoPi = (2.0 * PI).toFloat()

    Box(modifier = modifier.fillMaxSize().background(linColors.background)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val vmax = maxOf(w, h)
            if (vmax <= 1f) return@Canvas

            val phase = phaseSecState.floatValue
            val p1 = (phase / 24f) * twoPi
            val p2 = (phase / 29f) * twoPi
            val p3 = (phase / 27f) * twoPi
            val p4 = (phase / 33f) * twoPi

            val bass = smoothBassState.floatValue.coerceIn(0f, 1.35f)
            val total = smoothTotalState.floatValue.coerceIn(0f, 1.35f)

            val c1 = c1State.value
            val c2 = c2State.value
            val c3 = c3State.value
            val c4 = c4State.value

            drawReactiveBlob(
                color = c1,
                baseCenter = Offset((0.25f + 0.38f * cos(p1)) * w, (0.28f + 0.24f * sin(p1)) * h),
                index = 0,
                energy = bass,
                w = w,
                h = h,
                vmax = vmax,
                baseOpacity = baseOpacity,
                maxOpacity = maxOpacity,
                blendMode = blend
            )
            drawReactiveBlob(
                color = c2,
                baseCenter = Offset((0.75f - 0.36f * sin(p2)) * w, (0.68f - 0.28f * cos(p2)) * h),
                index = 1,
                energy = total,
                w = w,
                h = h,
                vmax = vmax,
                baseOpacity = baseOpacity,
                maxOpacity = maxOpacity,
                blendMode = blend
            )
            drawReactiveBlob(
                color = c3,
                baseCenter = Offset((0.68f + 0.30f * cos(p3 + 1.2f)) * w, (0.35f + 0.34f * sin(p3 + 0.7f)) * h),
                index = 2,
                energy = bass,
                w = w,
                h = h,
                vmax = vmax,
                baseOpacity = baseOpacity,
                maxOpacity = maxOpacity,
                blendMode = blend
            )
            drawReactiveBlob(
                color = c4,
                baseCenter = Offset((0.32f + 0.34f * sin(p4 + 2.1f)) * w, (0.62f + 0.26f * cos(p4 + 1.5f)) * h),
                index = 3,
                energy = total,
                w = w,
                h = h,
                vmax = vmax,
                baseOpacity = baseOpacity,
                maxOpacity = maxOpacity,
                blendMode = blend
            )

            // Centered .audio-pulse warmth on bass hits
            if (bass > 0.015f) {
                val pulseRadius = (vmax * (0.36f + bass * 0.34f)).coerceAtLeast(1f)
                val pulseAlpha = (bass * 0.42f).coerceIn(0f, 0.54f)
                val pulseCenter = Offset(w * 0.5f, h * 0.5f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to c1.copy(alpha = pulseAlpha),
                        0.35f to c1.copy(alpha = pulseAlpha * 0.58f),
                        0.72f to c1.copy(alpha = pulseAlpha * 0.16f),
                        1.00f to Color.Transparent,
                        center = pulseCenter,
                        radius = pulseRadius
                    ),
                    radius = pulseRadius,
                    center = pulseCenter,
                    blendMode = blend
                )
            }
        }

        // Lighter frosted overlay preserving crisp contrast while letting reactive colors shine
        Box(modifier = Modifier.fillMaxSize().background(veilBrush))
    }
}

private fun DrawScope.drawReactiveBlob(
    color: Color,
    baseCenter: Offset,
    index: Int,
    energy: Float,
    w: Float,
    h: Float,
    vmax: Float,
    baseOpacity: Float,
    maxOpacity: Float,
    blendMode: BlendMode
) {
    val outwardPush = energy * 0.065f
    val cx = baseCenter.x + (baseCenter.x - w * 0.5f) * outwardPush
    val cy = baseCenter.y + (baseCenter.y - h * 0.5f) * outwardPush
    val center = Offset(cx, cy)
    val radius = (vmax * (0.40f + index * 0.035f + energy * 0.30f)).coerceAtLeast(1f)
    val alpha = (baseOpacity + energy * 0.34f).coerceIn(0.20f, maxOpacity).coerceIn(0f, 1f)

    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = alpha),
            0.22f to color.copy(alpha = alpha * 0.86f),
            0.45f to color.copy(alpha = alpha * 0.54f),
            0.68f to color.copy(alpha = alpha * 0.24f),
            0.86f to color.copy(alpha = alpha * 0.06f),
            1.00f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center,
        blendMode = blendMode
    )
}
