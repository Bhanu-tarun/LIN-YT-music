package com.lin.music.core.innertube.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionOsCooldownTrackerTest {

    @Test
    fun `activates cooldown after threshold fallback successes and expires automatically`() {
        val tracker = VisionOsCooldownTracker(failureThreshold = 3, cooldownMs = 600_000L)

        assertFalse(tracker.shouldSkipVisionOs("US", 1_000L))
        tracker.recordVisionOsFailureWithFallbackSuccess("US", 2_000L)
        assertFalse(tracker.shouldSkipVisionOs("US", 2_500L))
        tracker.recordVisionOsFailureWithFallbackSuccess("US", 3_000L)
        assertFalse(tracker.shouldSkipVisionOs("US", 3_500L))
        tracker.recordVisionOsFailureWithFallbackSuccess("US", 4_000L)

        // Threshold reached -> skip for 10 minutes
        assertTrue(tracker.shouldSkipVisionOs("US", 4_500L))
        assertTrue(tracker.shouldSkipVisionOs("US", 603_999L))
        // Cooldown expired -> retry VISIONOS
        assertFalse(tracker.shouldSkipVisionOs("US", 604_000L))
    }

    @Test
    fun `resets immediately on success or content region change`() {
        val tracker = VisionOsCooldownTracker(failureThreshold = 2, cooldownMs = 600_000L)

        tracker.recordVisionOsFailureWithFallbackSuccess("US", 1_000L)
        tracker.recordVisionOsFailureWithFallbackSuccess("US", 2_000L)
        assertTrue(tracker.shouldSkipVisionOs("US", 3_000L))

        // Changing content region resets cooldown immediately
        assertFalse(tracker.shouldSkipVisionOs("JP", 3_100L))

        tracker.recordVisionOsFailureWithFallbackSuccess("JP", 4_000L)
        tracker.recordVisionOsFailureWithFallbackSuccess("JP", 5_000L)
        assertTrue(tracker.shouldSkipVisionOs("JP", 6_000L))

        // Manual reset or success clears skip state
        tracker.recordVisionOsSuccess("JP")
        assertFalse(tracker.shouldSkipVisionOs("JP", 7_000L))
    }
}
