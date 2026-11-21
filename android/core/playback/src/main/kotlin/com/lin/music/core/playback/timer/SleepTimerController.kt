package com.lin.music.core.playback.timer

import com.lin.music.core.model.SleepTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SleepTimerController(
    private val scope: CoroutineScope,
    private val timeProviderMs: () -> Long = { System.currentTimeMillis() },
    private val onApplyFadeVolume: (Float) -> Unit,
    private val onTimerFinishedPause: () -> Unit
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var endEpochMs: Long = 0L
    private var fadeOutEnabled: Boolean = true
    private var pausedByTimer: Boolean = false

    fun startCountdown(durationMs: Long, fadeOut: Boolean = true) {
        if (durationMs <= 0L) {
            cancel()
            return
        }
        timerJob?.cancel()
        pausedByTimer = false
        fadeOutEnabled = fadeOut
        endEpochMs = timeProviderMs() + durationMs
        onApplyFadeVolume(1f)
        _state.value = SleepTimerState.Countdown(durationMs, endEpochMs, fadeOut)

        timerJob = scope.launch {
            while (isActive) {
                val remaining = (endEpochMs - timeProviderMs()).coerceAtLeast(0L)
                if (remaining == 0L) {
                    if (fadeOutEnabled) onApplyFadeVolume(0f)
                    pausedByTimer = true
                    _state.value = SleepTimerState.Off
                    onTimerFinishedPause()
                    break
                }
                _state.value = SleepTimerState.Countdown(remaining, endEpochMs, fadeOutEnabled)
                if (fadeOutEnabled && remaining <= FADE_WINDOW_MS) {
                    val gain = (remaining.toFloat() / FADE_WINDOW_MS.toFloat()).coerceIn(0.05f, 1f)
                    onApplyFadeVolume(gain)
                } else {
                    onApplyFadeVolume(1f)
                }
                val nextTick = if (fadeOutEnabled && remaining <= FADE_WINDOW_MS + 1_000L) {
                    FADE_TICK_INTERVAL_MS
                } else {
                    IDLE_TICK_INTERVAL_MS
                }
                delay(minOf(nextTick, remaining))
            }
        }
    }

    fun startEndOfTrack(fadeOut: Boolean = false) {
        timerJob?.cancel()
        pausedByTimer = false
        fadeOutEnabled = fadeOut
        onApplyFadeVolume(1f)
        _state.value = SleepTimerState.EndOfTrack(fadeOutEnabled = fadeOut)
    }

    fun extend(extraMs: Long = DEFAULT_EXTEND_MS) {
        when (_state.value) {
            is SleepTimerState.Countdown -> {
                val newRemaining = ((endEpochMs - timeProviderMs()).coerceAtLeast(0L)) + extraMs
                startCountdown(newRemaining, fadeOutEnabled)
            }
            is SleepTimerState.EndOfTrack -> {
                startCountdown(extraMs, fadeOutEnabled)
            }
            SleepTimerState.Off -> Unit
        }
    }

    fun onTrackTransition() {
        if (_state.value is SleepTimerState.EndOfTrack) {
            pausedByTimer = true
            _state.value = SleepTimerState.Off
            onTimerFinishedPause()
        }
    }

    fun onPlaybackResumed() {
        if (pausedByTimer) {
            pausedByTimer = false
            onApplyFadeVolume(1f)
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        pausedByTimer = false
        onApplyFadeVolume(1f)
        _state.value = SleepTimerState.Off
    }

    companion object {
        const val FADE_WINDOW_MS = 10_000L
        const val TICK_INTERVAL_MS = 500L
        const val FADE_TICK_INTERVAL_MS = 500L
        const val IDLE_TICK_INTERVAL_MS = 1_000L
        const val DEFAULT_EXTEND_MS = 5 * 60_000L
    }
}
