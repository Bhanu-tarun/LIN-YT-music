package com.lin.music.core.playback.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.lin.music.core.model.AmbientReactivity
import com.lin.music.core.model.AudioEnergy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Non-mutating Media3 TeeAudioProcessor buffer sink that analyzes PCM samples in real time
 * via a zero-allocation 256-point Hann-windowed FFT with precomputed twiddle tables and
 * responsive attack / smooth decay EMA smoothing for vivid audio-reactive backgrounds.
 */
@OptIn(UnstableApi::class)
@Singleton
class ReactiveAudioProcessor internal constructor(
    private val clockMs: () -> Long
) : TeeAudioProcessor.AudioBufferSink {

    @Inject
    constructor() : this(clockMs = { android.os.SystemClock.elapsedRealtime() })

    private val _energyFlow = MutableStateFlow(AudioEnergy())
    val energyFlow: StateFlow<AudioEnergy> = _energyFlow.asStateFlow()

    private val windowSamples = FloatArray(FFT_SIZE)
    private val fftReal = FloatArray(FFT_SIZE)
    private val fftImag = FloatArray(FFT_SIZE)
    private var sampleWriteIdx = 0
    private var configuredChannels = 2
    private var configuredEncoding = C.ENCODING_PCM_16BIT

    private var smoothedBass = 0f
    private var smoothedTotal = 0f
    private var lastEmittedBass = 0f
    private var lastEmittedTotal = 0f
    private var lastFftTimeMs = 0L

    @Volatile
    private var reactivity: AmbientReactivity = AmbientReactivity.EXPRESSIVE

    @Volatile
    private var analysisEnabled: Boolean = true

    fun updateReactivity(level: AmbientReactivity) {
        reactivity = level
    }

    fun setEnabled(enabled: Boolean) {
        if (analysisEnabled == enabled) return
        analysisEnabled = enabled
        if (!enabled) {
            sampleWriteIdx = 0
            resetEnergy()
        }
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        configuredChannels = channelCount.coerceAtLeast(1)
        configuredEncoding = encoding
        sampleWriteIdx = 0
        resetEnergy()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!analysisEnabled) return
        val remaining = buffer.remaining()
        if (remaining == 0) return

        val slice = buffer.asReadOnlyBuffer().order(buffer.order())
        val channels = configuredChannels.coerceAtLeast(1)

        when (configuredEncoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameBytes = channels * 2
                while (slice.remaining() >= frameBytes) {
                    var sum = 0f
                    for (c in 0 until channels) {
                        sum += slice.short / 32768f
                    }
                    pushSample(sum / channels)
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val frameBytes = channels * 4
                while (slice.remaining() >= frameBytes) {
                    var sum = 0f
                    for (c in 0 until channels) {
                        sum += slice.float.coerceIn(-1f, 1f)
                    }
                    pushSample(sum / channels)
                }
            }
            else -> return
        }
    }

    private fun pushSample(sample: Float) {
        windowSamples[sampleWriteIdx++] = sample
        if (sampleWriteIdx >= FFT_SIZE) {
            sampleWriteIdx = 0
            val now = clockMs()
            if (now - lastFftTimeMs < FFT_INTERVAL_MS) return
            lastFftTimeMs = now
            val raw = analyzePcmWindowInto(windowSamples, fftReal, fftImag)
            emitEnergy(raw, now)
        }
    }

    fun resetEnergy() {
        smoothedBass = 0f
        smoothedTotal = 0f
        lastEmittedBass = 0f
        lastEmittedTotal = 0f
        lastFftTimeMs = 0L
        _energyFlow.value = AudioEnergy()
    }

    private fun emitEnergy(raw: AudioEnergy, nowMs: Long) {
        val level = reactivity
        val bassAlpha = if (raw.bass > smoothedBass) {
            (0.42f * level.attackScale).coerceIn(0.05f, 0.75f)
        } else {
            (0.14f * level.decayScale).coerceIn(0.03f, 0.50f)
        }
        val totalAlpha = if (raw.total > smoothedTotal) {
            (0.34f * level.attackScale).coerceIn(0.05f, 0.70f)
        } else {
            (0.11f * level.decayScale).coerceIn(0.03f, 0.45f)
        }
        smoothedBass += (raw.bass - smoothedBass) * bassAlpha
        smoothedTotal += (raw.total - smoothedTotal) * totalAlpha

        if (abs(smoothedBass - lastEmittedBass) >= DELTA_THRESHOLD ||
            abs(smoothedTotal - lastEmittedTotal) >= DELTA_THRESHOLD
        ) {
            lastEmittedBass = smoothedBass
            lastEmittedTotal = smoothedTotal
            _energyFlow.value = AudioEnergy(bass = smoothedBass, total = smoothedTotal)
        }
    }

    companion object {
        const val FFT_SIZE = 256
        private const val FFT_LOG2 = 8
        private const val FFT_INTERVAL_MS = 30L
        private const val DELTA_THRESHOLD = 0.005f

        private val HANN_WINDOW = FloatArray(FFT_SIZE) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
        }

        private val BIT_REVERSAL = IntArray(FFT_SIZE) { i ->
            var x = i
            var rev = 0
            for (b in 0 until FFT_LOG2) {
                rev = (rev shl 1) or (x and 1)
                x = x ushr 1
            }
            rev
        }

        private val STAGE_COS = Array(FFT_LOG2) { stage ->
            val half = 1 shl stage
            val len = half shl 1
            FloatArray(half) { k -> cos(-2.0 * PI * k / len).toFloat() }
        }

        private val STAGE_SIN = Array(FFT_LOG2) { stage ->
            val half = 1 shl stage
            val len = half shl 1
            FloatArray(half) { k -> sin(-2.0 * PI * k / len).toFloat() }
        }

        fun analyzePcmWindow(samples: FloatArray): AudioEnergy {
            if (min(samples.size, FFT_SIZE) < FFT_SIZE) return AudioEnergy()
            return analyzePcmWindowInto(samples, FloatArray(FFT_SIZE), FloatArray(FFT_SIZE))
        }

        private fun analyzePcmWindowInto(
            samples: FloatArray,
            real: FloatArray,
            imag: FloatArray
        ): AudioEnergy {
            val n = min(samples.size, FFT_SIZE)
            if (n < FFT_SIZE) return AudioEnergy()

            for (i in 0 until FFT_SIZE) {
                val srcIdx = BIT_REVERSAL[i]
                real[i] = samples[srcIdx] * HANN_WINDOW[srcIdx]
                imag[i] = 0f
            }

            // In-place radix-2 Cooley-Tukey FFT using precomputed twiddle tables
            for (stage in 0 until FFT_LOG2) {
                val half = 1 shl stage
                val len = half shl 1
                val cosTbl = STAGE_COS[stage]
                val sinTbl = STAGE_SIN[stage]
                var i = 0
                while (i < FFT_SIZE) {
                    for (k in 0 until half) {
                        val wR = cosTbl[k]
                        val wI = sinTbl[k]
                        val evenIdx = i + k
                        val oddIdx = evenIdx + half
                        val uR = real[evenIdx]
                        val uI = imag[evenIdx]
                        val oR = real[oddIdx]
                        val oI = imag[oddIdx]
                        val vR = oR * wR - oI * wI
                        val vI = oR * wI + oI * wR
                        real[evenIdx] = uR + vR
                        imag[evenIdx] = uI + vI
                        real[oddIdx] = uR - vR
                        imag[oddIdx] = uI - vI
                    }
                    i += len
                }
            }

            val halfBins = FFT_SIZE / 2
            val bassEnd = halfBins / 4
            var bassSum = 0f
            var bassPeak = 0f
            var totalSum = 0f
            var totalPeak = 0f
            for (bin in 1 until halfBins) {
                val mag = (hypot(real[bin], imag[bin]) / (FFT_SIZE / 4f)).coerceIn(0f, 1f)
                totalSum += mag
                if (mag > totalPeak) totalPeak = mag
                if (bin < bassEnd) {
                    bassSum += mag
                    if (mag > bassPeak) bassPeak = mag
                }
            }

            val meanBass = bassSum / (bassEnd - 1).coerceAtLeast(1)
            val meanTotal = totalSum / (halfBins - 1).coerceAtLeast(1)
            val rawBass = (meanBass * 2.5f + bassPeak * 0.65f).coerceIn(0f, 1f)
            val rawTotal = (meanTotal * 3.2f + totalPeak * 0.40f).coerceIn(0f, 1f)
            val bass = if (rawBass > 0f) rawBass.pow(0.85f) else 0f
            val total = if (rawTotal > 0f) rawTotal.pow(0.88f) else 0f
            return AudioEnergy(bass = bass, total = total)
        }
    }
}
