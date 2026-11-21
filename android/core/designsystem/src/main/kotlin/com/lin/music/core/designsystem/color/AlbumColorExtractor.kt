package com.lin.music.core.designsystem.color

import android.graphics.Bitmap
import com.lin.music.core.model.TrackArtworkPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Extracts dominant chromatic hue clusters and a saturated non-white accent color
 * directly from album artwork for ambient blobs and active UI indicators.
 */
object AlbumColorExtractor {

    private val defaultPalette = TrackArtworkPalette(
        colors = listOf(0xFF00FF80.toInt(), 0xFFFF0080.toInt(), 0xFF0080FF.toInt(), 0xFFFF8000.toInt()),
        brightestAccent = 0xFF00FF00.toInt()
    )

    private val monochromeFallbackPalette = TrackArtworkPalette(
        colors = listOf(0xFF3A4454.toInt(), 0xFF28303D.toInt(), 0xFF4A5568.toInt(), 0xFF1F2633.toInt()),
        brightestAccent = 0xFF4CD9B0.toInt()
    )

    fun extractFromBitmap(bitmap: Bitmap): TrackArtworkPalette {
        val scaled = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
        val pixels = IntArray(48 * 48)
        scaled.getPixels(pixels, 0, 48, 0, 0, 48, 48)
        if (scaled != bitmap) scaled.recycle()
        return extractFromPixels(pixels)
    }

    private data class HueCluster(
        val hue: Float,
        val avgS: Float,
        val avgL: Float,
        val weight: Float
    )

    fun extractFromPixels(pixels: IntArray): TrackArtworkPalette {
        if (pixels.isEmpty()) return defaultPalette

        val numBins = 24
        val binWeight = FloatArray(numBins)
        val binHueCos = DoubleArray(numBins)
        val binHueSin = DoubleArray(numBins)
        val binSatSum = FloatArray(numBins)
        val binLightSum = FloatArray(numBins)
        var totalChromaticWeight = 0f

        for (argb in pixels) {
            val alpha = (argb ushr 24) and 0xFF
            if (alpha < 128) continue
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF

            val (h, s, l) = rgbToHsl(r, g, b)
            // Skip near-black, near-white, and desaturated grays so artwork color reflects true palette
            if (s < 0.14f || l < 0.08f || l > 0.92f) continue

            val midWeight = 1f - abs(2f * l - 1f)
            val weight = s * (0.4f + 0.6f * midWeight) + 0.15f
            val bin = ((h * numBins).toInt()).coerceIn(0, numBins - 1)
            val angle = h.toDouble() * 2.0 * PI

            binWeight[bin] += weight
            binHueCos[bin] += weight * cos(angle)
            binHueSin[bin] += weight * sin(angle)
            binSatSum[bin] += weight * s
            binLightSum[bin] += weight * l
            totalChromaticWeight += weight
        }

        if (totalChromaticWeight <= 0f) {
            return monochromeFallbackPalette
        }

        val sortedBins = (0 until numBins)
            .filter { binWeight[it] > 0f }
            .sortedByDescending { binWeight[it] }

        val clusters = mutableListOf<HueCluster>()
        for (bin in sortedBins) {
            val w = binWeight[bin]
            var h = (atan2(binHueSin[bin], binHueCos[bin]) / (2.0 * PI)).toFloat()
            if (h < 0f) h += 1f
            if (clusters.none { circularHueDistance(it.hue, h) < 0.08f }) {
                clusters += HueCluster(
                    hue = h,
                    avgS = binSatSum[bin] / w,
                    avgL = binLightSum[bin] / w,
                    weight = w
                )
                if (clusters.size == 4) break
            }
        }

        val top = clusters.first()
        val blobColors = mutableListOf<Int>()
        for (c in clusters) {
            blobColors += hslToArgb(
                h = c.hue,
                s = c.avgS.coerceIn(0.32f, 0.80f),
                l = c.avgL.coerceIn(0.32f, 0.58f)
            )
        }

        // Fill remaining blob slots with subtle analogous/tonal artwork shifts rather than random rainbow hues
        val fallbackOffsets = listOf(
            Triple(0.045f, 0.90f, 0.44f),
            Triple(-0.045f, 0.82f, 0.35f),
            Triple(0.020f, 0.95f, 0.52f)
        )
        var fallbackIdx = 0
        while (blobColors.size < 4) {
            val base = clusters[blobColors.size % clusters.size]
            val (hShift, sMul, targetL) = fallbackOffsets[fallbackIdx % fallbackOffsets.size]
            val shiftedH = ((base.hue + hShift) % 1f + 1f) % 1f
            blobColors += hslToArgb(
                h = shiftedH,
                s = (base.avgS * sMul).coerceIn(0.28f, 0.76f),
                l = targetL
            )
            fallbackIdx++
        }

        // Vivid, non-white accent derived directly from the dominant chromatic cluster
        val accentColor = hslToArgb(
            h = top.hue,
            s = max(top.avgS, 0.72f).coerceAtMost(0.95f),
            l = 0.62f
        )

        return TrackArtworkPalette(
            colors = blobColors,
            brightestAccent = accentColor
        )
    }

    private fun circularHueDistance(a: Float, b: Float): Float {
        val d = abs(a - b)
        return min(d, 1f - d)
    }

    private fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val (r, g, b) = hslToRgb(h, s, l)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    internal fun rgbToHsl(rInt: Int, gInt: Int, bInt: Int): Triple<Float, Float, Float> {
        val r = rInt / 255f
        val g = gInt / 255f
        val b = bInt / 255f
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val l = (max + min) / 2f
        val h: Float
        val s: Float
        if (max == min) {
            h = 0f
            s = 0f
        } else {
            val d = max - min
            s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
            var hue = when (max) {
                r -> (g - b) / d + (if (g < b) 6f else 0f)
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            }
            hue /= 6f
            h = hue
        }
        return Triple(h, s, l)
    }

    internal fun hslToRgb(h: Float, s: Float, l: Float): Triple<Int, Int, Int> {
        if (s == 0f) {
            val v = (l * 255f).roundToInt()
            return Triple(v, v, v)
        }
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        fun hue2rgb(tIn: Float): Float {
            var t = tIn
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 0.5f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        val r = (hue2rgb(h + 1f / 3f) * 255f).roundToInt().coerceIn(0, 255)
        val g = (hue2rgb(h) * 255f).roundToInt().coerceIn(0, 255)
        val b = (hue2rgb(h - 1f / 3f) * 255f).roundToInt().coerceIn(0, 255)
        return Triple(r, g, b)
    }
}
