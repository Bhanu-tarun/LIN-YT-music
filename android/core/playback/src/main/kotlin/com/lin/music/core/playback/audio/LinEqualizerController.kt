package com.lin.music.core.playback.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.annotation.RequiresApi
import com.lin.music.core.model.EQ_BAND_FREQUENCIES_HZ
import com.lin.music.core.model.EqualizerProfile
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Low-overhead native EQ controller.
 * Uses API 28+ [DynamicsProcessing] (exact 10-band PreEQ + preamp + soft limiter)
 * with graceful fallback to standard Android [Equalizer].
 * Never allocates native DSP objects when disabled.
 */
class LinEqualizerController {
    private var attachedSessionId: Int = 0
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var legacyEqualizer: Equalizer? = null

    fun attachOrUpdate(audioSessionId: Int, enabled: Boolean, profile: EqualizerProfile) {
        if (audioSessionId <= 0) {
            release()
            return
        }
        if (!enabled) {
            release()
            return
        }
        if (attachedSessionId != audioSessionId) {
            release()
            attachedSessionId = audioSessionId
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val applied = runCatching {
                applyDynamicsProcessing(audioSessionId, profile)
            }.getOrDefault(false)
            if (applied) {
                runCatching { legacyEqualizer?.release() }
                legacyEqualizer = null
                return
            }
        }

        applyLegacyEqualizer(audioSessionId, profile)
    }

    fun release() {
        attachedSessionId = 0
        runCatching { dynamicsProcessing?.enabled = false }
        runCatching { dynamicsProcessing?.release() }
        dynamicsProcessing = null
        runCatching { legacyEqualizer?.enabled = false }
        runCatching { legacyEqualizer?.release() }
        legacyEqualizer = null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyDynamicsProcessing(audioSessionId: Int, profile: EqualizerProfile): Boolean {
        val gains = profile.normalizedBands()
        val dp = dynamicsProcessing ?: createDynamicsProcessing(audioSessionId)?.also {
            dynamicsProcessing = it
        } ?: return false

        val eq = DynamicsProcessing.Eq(true, true, EQ_BAND_FREQUENCIES_HZ.size)
        for (i in EQ_BAND_FREQUENCIES_HZ.indices) {
            val cutoffHz = if (i < EQ_BAND_FREQUENCIES_HZ.lastIndex) {
                sqrt(EQ_BAND_FREQUENCIES_HZ[i].toDouble() * EQ_BAND_FREQUENCIES_HZ[i + 1].toDouble()).toFloat()
            } else {
                20_000f
            }
            val band = eq.getBand(i)
            band.isEnabled = true
            band.cutoffFrequency = cutoffHz
            band.gain = gains[i]
        }

        dp.setInputGainAllChannelsTo(profile.preampDb.coerceIn(-12f, 12f))
        dp.setPreEqAllChannelsTo(eq)
        dp.enabled = true
        return true
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createDynamicsProcessing(audioSessionId: Int): DynamicsProcessing? = runCatching {
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true,
            EQ_BAND_FREQUENCIES_HZ.size,
            false,
            0,
            false,
            0,
            true
        )
        builder.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(
                true,
                true,
                0,
                1f,
                60f,
                4f,
                -0.8f,
                0f
            )
        )
        DynamicsProcessing(0, audioSessionId, builder.build())
    }.getOrNull()

    private fun applyLegacyEqualizer(audioSessionId: Int, profile: EqualizerProfile) {
        val eq = legacyEqualizer ?: runCatching {
            Equalizer(0, audioSessionId)
        }.getOrNull()?.also {
            legacyEqualizer = it
        } ?: return

        runCatching {
            val bandCount = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minMb = range.getOrElse(0) { -1500 }.toInt()
            val maxMb = range.getOrElse(1) { 1500 }.toInt()
            val gains = profile.normalizedBands()

            for (b in 0 until bandCount) {
                val centerHz = eq.getCenterFreq(b.toShort()) / 1000
                val nearestIdx = EQ_BAND_FREQUENCIES_HZ.indices.minByOrNull { idx ->
                    abs(EQ_BAND_FREQUENCIES_HZ[idx] - centerHz)
                } ?: 0
                val targetDb = (gains[nearestIdx] + profile.preampDb * 0.5f)
                val targetMb = (targetDb * 100f).roundToInt().coerceIn(minMb, maxMb)
                eq.setBandLevel(b.toShort(), targetMb.toShort())
            }
            eq.enabled = true
        }
    }
}
