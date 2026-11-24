package com.lin.music.core.model

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Standard 10-band ISO graphic EQ center frequencies in Hz. */
val EQ_BAND_FREQUENCIES_HZ: List<Int> = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

val EQ_BAND_LABELS: List<String> = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

const val EQ_MIN_GAIN_DB = -12f
const val EQ_MAX_GAIN_DB = 12f

@Serializable
data class EqualizerProfile(
    val id: String,
    val name: String,
    val bandGainsDb: List<Float> = List(10) { 0f },
    val preampDb: Float = 0f,
    val isBuiltIn: Boolean = false
) {
    fun normalizedBands(): List<Float> =
        List(10) { idx -> (bandGainsDb.getOrElse(idx) { 0f }).coerceIn(EQ_MIN_GAIN_DB, EQ_MAX_GAIN_DB) }
}

@Serializable
data class BluetoothEqAssignment(
    val deviceKey: String,
    val deviceName: String,
    val profileId: String
)

object BuiltInEqPresets {
    val FLAT = EqualizerProfile(
        id = "flat",
        name = "Flat",
        bandGainsDb = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        preampDb = 0f,
        isBuiltIn = true
    )

    val ALL: List<EqualizerProfile> = listOf(
        FLAT,
        EqualizerProfile(
            id = "preset_bass_boost",
            name = "Bass Boost",
            bandGainsDb = listOf(5.5f, 4.8f, 3.6f, 1.8f, 0f, 0f, 0.5f, 1f, 1.2f, 1f),
            preampDb = -2f,
            isBuiltIn = true
        ),
        EqualizerProfile(
            id = "preset_vocal",
            name = "Vocal Clarity",
            bandGainsDb = listOf(-2f, -1.5f, -0.5f, 1f, 2.5f, 3.8f, 4.2f, 3f, 1.5f, 0.5f),
            preampDb = -1.5f,
            isBuiltIn = true
        ),
        EqualizerProfile(
            id = "preset_harman_iem",
            name = "Harman In-Ear",
            bandGainsDb = listOf(3.2f, 2.6f, 1.2f, -0.8f, -1.2f, 0f, 2.2f, 3f, 1.8f, 0.4f),
            preampDb = -2f,
            isBuiltIn = true
        ),
        EqualizerProfile(
            id = "preset_late_night",
            name = "Late Night",
            bandGainsDb = listOf(3.5f, 2.5f, 1.2f, 0f, -0.5f, 0f, 1f, 2.5f, 3.2f, 2.8f),
            preampDb = -1f,
            isBuiltIn = true
        ),
        EqualizerProfile(
            id = "preset_treble",
            name = "Air & Sparkle",
            bandGainsDb = listOf(0f, 0f, 0f, -0.5f, -0.5f, 0.5f, 1.8f, 3.2f, 4.5f, 5f),
            preampDb = -2f,
            isBuiltIn = true
        ),
        EqualizerProfile(
            id = "preset_electronic",
            name = "Electronic",
            bandGainsDb = listOf(4.5f, 3.8f, 1.5f, -1f, -1.5f, 0.5f, 2f, 3.2f, 4f, 3.5f),
            preampDb = -2f,
            isBuiltIn = true
        ),
        EqualizerProfile(
            id = "preset_acoustic",
            name = "Acoustic",
            bandGainsDb = listOf(2f, 1.5f, 1f, 0.5f, 1.2f, 1.5f, 2f, 2.6f, 2.2f, 1.2f),
            preampDb = -1f,
            isBuiltIn = true
        )
    )

    fun resolve(profileId: String, customProfiles: List<EqualizerProfile>): EqualizerProfile =
        customProfiles.firstOrNull { it.id == profileId }
            ?: ALL.firstOrNull { it.id == profileId }
            ?: FLAT
}

/**
 * Parses AutoEQ text exports in either Wavelet `GraphicEQ:` format or
 * EqualizerAPO `Preamp:` / `Filter N: ON PK Fc ... Hz Gain ... dB Q ...` format,
 * resampling them accurately onto the 10 ISO EQ frequencies.
 */
object AutoEqParser {
    private val graphicPairRegex = Regex("""(\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)""")
    private val preampRegex = Regex("""Preamp:\s*(-?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val filterRegex = Regex(
        """Filter\s+\d+:\s+ON\s+(PK|LS|HS|LSC|HSC)\s+Fc\s+(\d+(?:\.\d+)?)\s*Hz\s+Gain\s+(-?\d+(?:\.\d+)?)\s*dB(?:\s+Q\s+(\d+(?:\.\d+)?))?""",
        RegexOption.IGNORE_CASE
    )

    private data class ParametricBand(
        val type: String,
        val freqHz: Double,
        val gainDb: Double,
        val q: Double
    )

    fun parse(rawText: String, name: String = "Imported AutoEQ", id: String = "autoeq_${System.currentTimeMillis()}"): EqualizerProfile? {
        val text = rawText.trim()
        if (text.isEmpty()) return null

        val graphicLine = text.lineSequence()
            .firstOrNull { it.trimStart().startsWith("GraphicEQ:", ignoreCase = true) }
        if (graphicLine != null) {
            val payload = graphicLine.substringAfter(":", "")
            val points = graphicPairRegex.findAll(payload)
                .mapNotNull { m ->
                    val freq = m.groupValues[1].toDoubleOrNull()
                    val gain = m.groupValues[2].toDoubleOrNull()
                    if (freq != null && gain != null && freq > 0.0) freq to gain else null
                }
                .sortedBy { it.first }
                .toList()
            if (points.size >= 2) {
                val resampled = EQ_BAND_FREQUENCIES_HZ.map { target ->
                    interpolateLogFreq(points, target.toDouble()).toFloat().coerceIn(EQ_MIN_GAIN_DB, EQ_MAX_GAIN_DB)
                }
                val maxPositive = resampled.maxOrNull()?.coerceAtLeast(0f) ?: 0f
                val preamp = (-maxPositive).coerceIn(-12f, 0f)
                return EqualizerProfile(
                    id = id,
                    name = name.ifBlank { "Imported AutoEQ" },
                    bandGainsDb = resampled,
                    preampDb = preamp,
                    isBuiltIn = false
                )
            }
        }

        val preamp = preampRegex.find(text)?.groupValues?.get(1)?.toFloatOrNull()
        val filters = filterRegex.findAll(text).mapNotNull { m ->
            val type = m.groupValues[1].uppercase()
            val fc = m.groupValues[2].toDoubleOrNull()
            val gain = m.groupValues[3].toDoubleOrNull()
            val q = m.groupValues[4].toDoubleOrNull() ?: 0.707
            if (fc != null && gain != null && fc > 0.0 && q > 0.0) {
                ParametricBand(type, fc, gain, q)
            } else {
                null
            }
        }.toList()

        if (filters.isEmpty()) return null

        val sampleRate = 48_000.0
        val resampled = EQ_BAND_FREQUENCIES_HZ.map { target ->
            var sumDb = 0.0
            for (filter in filters) {
                sumDb += biquadMagnitudeDb(filter, target.toDouble(), sampleRate)
            }
            sumDb.toFloat().coerceIn(EQ_MIN_GAIN_DB, EQ_MAX_GAIN_DB)
        }
        val computedPreamp = preamp ?: (-(resampled.maxOrNull()?.coerceAtLeast(0f) ?: 0f))
        return EqualizerProfile(
            id = id,
            name = name.ifBlank { "Imported AutoEQ" },
            bandGainsDb = resampled,
            preampDb = computedPreamp.coerceIn(-12f, 6f),
            isBuiltIn = false
        )
    }

    private fun interpolateLogFreq(points: List<Pair<Double, Double>>, targetFreq: Double): Double {
        if (targetFreq <= points.first().first) return points.first().second
        if (targetFreq >= points.last().first) return points.last().second
        val targetLog = ln(targetFreq)
        for (i in 0 until points.lastIndex) {
            val (f0, g0) = points[i]
            val (f1, g1) = points[i + 1]
            if (targetFreq in f0..f1) {
                val l0 = ln(f0)
                val l1 = ln(f1)
                val t = if (l1 > l0) (targetLog - l0) / (l1 - l0) else 0.0
                return g0 + (g1 - g0) * t
            }
        }
        return 0.0
    }

    private fun biquadMagnitudeDb(filter: ParametricBand, freqHz: Double, sampleRate: Double): Double {
        if (filter.gainDb == 0.0) return 0.0
        val w0 = 2.0 * PI * filter.freqHz / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val a = 10.0.pow(filter.gainDb / 40.0)
        val alpha = sinW0 / (2.0 * filter.q)

        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double
        when (filter.type) {
            "LS", "LSC" -> {
                val sqrtA2Alpha = 2.0 * sqrt(a) * alpha
                b0 = a * ((a + 1) - (a - 1) * cosW0 + sqrtA2Alpha)
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW0)
                b2 = a * ((a + 1) - (a - 1) * cosW0 - sqrtA2Alpha)
                a0 = (a + 1) + (a - 1) * cosW0 + sqrtA2Alpha
                a1 = -2 * ((a - 1) + (a + 1) * cosW0)
                a2 = (a + 1) + (a - 1) * cosW0 - sqrtA2Alpha
            }
            "HS", "HSC" -> {
                val sqrtA2Alpha = 2.0 * sqrt(a) * alpha
                b0 = a * ((a + 1) + (a - 1) * cosW0 + sqrtA2Alpha)
                b1 = -2 * a * ((a - 1) + (a + 1) * cosW0)
                b2 = a * ((a + 1) + (a - 1) * cosW0 - sqrtA2Alpha)
                a0 = (a + 1) - (a - 1) * cosW0 + sqrtA2Alpha
                a1 = 2 * ((a - 1) - (a + 1) * cosW0)
                a2 = (a + 1) - (a - 1) * cosW0 - sqrtA2Alpha
            }
            else -> { // PK
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW0
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha / a
            }
        }

        val w = 2.0 * PI * freqHz / sampleRate
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)

        val numRe = (b0 / a0) + (b1 / a0) * cosW + (b2 / a0) * cos2W
        val numIm = -(b1 / a0) * sinW - (b2 / a0) * sin2W
        val denRe = 1.0 + (a1 / a0) * cosW + (a2 / a0) * cos2W
        val denIm = -(a1 / a0) * sinW - (a2 / a0) * sin2W

        val magSq = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm).coerceAtLeast(1e-18)
        return 10.0 * log10(magSq.coerceAtLeast(1e-18))
    }
}
