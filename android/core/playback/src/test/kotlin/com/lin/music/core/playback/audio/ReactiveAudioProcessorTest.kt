package com.lin.music.core.playback.audio

import androidx.media3.common.C
import com.lin.music.core.model.AmbientReactivity
import com.lin.music.core.model.AudioEnergy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class ReactiveAudioProcessorTest {

    @Test
    fun `analyzePcmWindow returns zero energy for silence`() {
        val silence = FloatArray(ReactiveAudioProcessor.FFT_SIZE)
        val energy = ReactiveAudioProcessor.analyzePcmWindow(silence)
        assertEquals(0f, energy.bass, 0.001f)
        assertEquals(0f, energy.total, 0.001f)
    }

    @Test
    fun `analyzePcmWindow detects higher bass energy for low frequency wave than high frequency wave`() {
        val sampleRate = 44100f
        // Bin ~4 at 44100/256 (~172 Hz per bin -> ~688 Hz falls inside first 1/4 bass bins)
        val lowFreq = FloatArray(ReactiveAudioProcessor.FFT_SIZE) { i ->
            sin(2.0 * PI * 500.0 * i / sampleRate).toFloat() * 0.8f
        }
        // High frequency ~14000 Hz falls well outside bass bins
        val highFreq = FloatArray(ReactiveAudioProcessor.FFT_SIZE) { i ->
            sin(2.0 * PI * 14000.0 * i / sampleRate).toFloat() * 0.8f
        }

        val lowEnergy = ReactiveAudioProcessor.analyzePcmWindow(lowFreq)
        val highEnergy = ReactiveAudioProcessor.analyzePcmWindow(highFreq)

        assertTrue("Expected significant bass energy for 500Hz wave, got ${lowEnergy.bass}", lowEnergy.bass > 0.1f)
        assertTrue(
            "Expected low-freq bass (${lowEnergy.bass}) > high-freq bass (${highEnergy.bass})",
            lowEnergy.bass > highEnergy.bass * 3f
        )
        assertTrue(highEnergy.total > 0.02f)
    }

    @Test
    fun `analyzePcmWindow produces deterministic results across repeated invocations`() {
        val sampleRate = 48000f
        val tone = FloatArray(ReactiveAudioProcessor.FFT_SIZE) { i ->
            (sin(2.0 * PI * 320.0 * i / sampleRate) * 0.65).toFloat()
        }
        val first = ReactiveAudioProcessor.analyzePcmWindow(tone)
        val second = ReactiveAudioProcessor.analyzePcmWindow(tone)
        assertEquals(first.bass, second.bass, 0.0001f)
        assertEquals(first.total, second.total, 0.0001f)
        assertTrue(first.bass in 0f..1f)
        assertTrue(first.total in 0f..1f)
    }

    @Test
    fun `updateReactivity accepts all reactivity levels cleanly`() {
        val processor = ReactiveAudioProcessor()
        AmbientReactivity.entries.forEach { level ->
            processor.updateReactivity(level)
        }
        assertTrue(AmbientReactivity.SUBTLE.energyScale < AmbientReactivity.BALANCED.energyScale)
        assertTrue(AmbientReactivity.BALANCED.energyScale < AmbientReactivity.EXPRESSIVE.energyScale)
        assertTrue(AmbientReactivity.EXPRESSIVE.energyScale < AmbientReactivity.INTENSE.energyScale)
    }

    @Test
    fun `setEnabled false short-circuits handleBuffer and setEnabled true resumes FFT analysis`() {
        var nowMs = 100L
        val processor = ReactiveAudioProcessor(clockMs = { nowMs })
        processor.flush(sampleRateHz = 44100, channelCount = 2, encoding = C.ENCODING_PCM_16BIT)

        // Build a 256-frame stereo 16-bit PCM buffer carrying a 500 Hz tone
        val pcmBuffer = ByteBuffer
            .allocate(ReactiveAudioProcessor.FFT_SIZE * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until ReactiveAudioProcessor.FFT_SIZE) {
            val sample = (sin(2.0 * PI * 500.0 * i / 44100.0) * 24000.0).toInt().toShort()
            pcmBuffer.putShort(sample)
            pcmBuffer.putShort(sample)
        }

        // Disabled -> handleBuffer must immediately return without consuming or analyzing samples
        processor.setEnabled(false)
        pcmBuffer.flip()
        val roView = pcmBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        processor.handleBuffer(roView)
        assertEquals(0, roView.position())
        assertEquals(AudioEnergy(), processor.energyFlow.value)

        // Re-enabled -> handleBuffer analyzes PCM frames and emits non-zero energy
        processor.setEnabled(true)
        nowMs = 200L
        pcmBuffer.rewind()
        processor.handleBuffer(pcmBuffer)
        assertTrue(processor.energyFlow.value.bass > 0.05f)
        assertTrue(processor.energyFlow.value.total > 0.02f)

        // Disabling again resets emitted energy back to zero
        processor.setEnabled(false)
        assertEquals(AudioEnergy(), processor.energyFlow.value)
    }
}
