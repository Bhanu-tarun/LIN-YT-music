package com.lin.music.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AudioEnergy(
    val bass: Float = 0f,
    val total: Float = 0f
)

@Serializable
enum class AudioQuality(val label: String) {
    HIGH("high (best opus/aac)"),
    NORMAL("normal (balanced)"),
    LOW("low (data saver)"),
    AUTO("auto (wifi high / cellular normal)")
}

@Serializable
enum class AmbientMode(val label: String) {
    AUDIO_REACTIVE("audio-reactive"),
    AMBIENT_DRIFT("ambient drift"),
    PURE_OLED("pure oled")
}

@Serializable
enum class AmbientReactivity(
    val label: String,
    val energyScale: Float,
    val bassStiffness: Float,
    val totalStiffness: Float,
    val dampingRatio: Float,
    val attackScale: Float,
    val decayScale: Float
) {
    SUBTLE("subtle", 0.45f, 85f, 70f, 1.0f, 0.55f, 0.70f),
    BALANCED("balanced", 0.75f, 135f, 105f, 0.94f, 0.80f, 0.85f),
    EXPRESSIVE("expressive", 1.0f, 185f, 135f, 0.88f, 1.0f, 1.0f),
    INTENSE("intense", 1.45f, 250f, 185f, 0.84f, 1.25f, 1.15f)
}

@Serializable
enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data class Countdown(
        val remainingMs: Long,
        val endTimeEpochMs: Long,
        val fadeOutEnabled: Boolean = true
    ) : SleepTimerState

    data class EndOfTrack(
        val fadeOutEnabled: Boolean = false
    ) : SleepTimerState
}

@Serializable
data class StreamInfo(
    val videoId: String,
    val url: String,
    val mimeType: String,
    val bitrate: Int = 0,
    val loudnessDb: Float? = null,
    val expiresAtEpochMs: Long = 0L,
    val codecLabel: String = "OPUS",
    val clientSource: String = "WEB_REMIX",
    val userAgent: String = ""
)

@Serializable
data class SkipSegment(
    val startMs: Long,
    val endMs: Long,
    val category: String
)

@Serializable
data class LyricLine(
    val timeMs: Long,
    val text: String
)

@Serializable
data class LyricsResult(
    val videoId: String,
    val syncedLines: List<LyricLine> = emptyList(),
    val plainText: String? = null,
    val source: String = ""
)

@Serializable
data class TrackArtworkPalette(
    val colors: List<Int>,
    val brightestAccent: Int
)
