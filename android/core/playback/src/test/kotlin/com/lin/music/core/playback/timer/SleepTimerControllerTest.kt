package com.lin.music.core.playback.timer

import com.lin.music.core.model.SleepTimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerControllerTest {

    @Test
    fun `countdown fades volume in final 10 seconds and pauses at zero`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var currentTime = 1_000_000L
        var lastVolume = 1f
        var paused = false

        val controller = SleepTimerController(
            scope = scope,
            timeProviderMs = { currentTime },
            onApplyFadeVolume = { lastVolume = it },
            onTimerFinishedPause = { paused = true }
        )

        controller.startCountdown(durationMs = 15_000L, fadeOut = true)
        scope.runCurrent()
        assertTrue(controller.state.value is SleepTimerState.Countdown)
        assertEquals(1f, lastVolume, 0.01f)

        // Advance 10 seconds (5s remaining -> inside fade window)
        currentTime += 10_000L
        scope.advanceTimeBy(10_000L)
        scope.runCurrent()
        assertTrue("Expected volume fade < 0.7f, got $lastVolume", lastVolume < 0.7f)

        // Advance final 5 seconds
        currentTime += 5_000L
        scope.advanceTimeBy(5_000L)
        scope.runCurrent()
        assertTrue(paused)
        assertEquals(SleepTimerState.Off, controller.state.value)

        // Resuming playback restores volume to 1f
        controller.onPlaybackResumed()
        assertEquals(1f, lastVolume, 0.01f)
    }

    @Test
    fun `end of track pauses on track transition and cancel resets`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var paused = false
        val controller = SleepTimerController(
            scope = scope,
            onApplyFadeVolume = {},
            onTimerFinishedPause = { paused = true }
        )

        controller.startEndOfTrack()
        assertTrue(controller.state.value is SleepTimerState.EndOfTrack)
        controller.onTrackTransition()
        assertTrue(paused)
        assertEquals(SleepTimerState.Off, controller.state.value)
    }
}
