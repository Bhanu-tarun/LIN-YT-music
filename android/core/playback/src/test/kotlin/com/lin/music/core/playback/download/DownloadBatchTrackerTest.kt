package com.lin.music.core.playback.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadBatchTrackerTest {

    @Test
    fun `upfront batch registration starts at 0 of N indeterminate and formats title`() {
        val tracker = DownloadBatchTracker()
        tracker.registerPending(listOf("v1", "v2", "v3", "v4", "v5"))

        val snapshot = tracker.buildSnapshot(
            activeDownloads = listOf(ActiveDownloadProgress("v1", -1f, 0L)),
            isPausedForRequirements = false,
            waitingForWifi = false
        )

        assertEquals(5, snapshot.totalCount)
        assertEquals(0, snapshot.completedCount)
        assertEquals(5, snapshot.remainingCount)
        assertEquals(0, snapshot.progressPermille)
        assertTrue(snapshot.isIndeterminate)

        val copy = DownloadBatchTracker.formatBatchNotification(snapshot)
        assertEquals("Downloading 0 / 5 songs", copy.title)
        assertEquals("0% · 5 left", copy.text)
        assertEquals("0 / 5", copy.subText)
    }

    @Test
    fun `progress advances monotonically across songs without resetting between tracks`() {
        val tracker = DownloadBatchTracker()
        tracker.registerPending(listOf("v1", "v2", "v3", "v4", "v5"))

        val step1 = tracker.buildSnapshot(
            activeDownloads = listOf(ActiveDownloadProgress("v1", 50f, 500_000L)),
            isPausedForRequirements = false,
            waitingForWifi = false
        )
        assertFalse(step1.isIndeterminate)
        assertEquals(100, step1.progressPermille)
        assertEquals(10, step1.progressPercent)

        tracker.markCompleted("v1")
        val step2 = tracker.buildSnapshot(
            activeDownloads = listOf(ActiveDownloadProgress("v2", 25f, 250_000L)),
            isPausedForRequirements = false,
            waitingForWifi = false
        )
        assertEquals(5, step2.totalCount)
        assertEquals(1, step2.completedCount)
        assertEquals(4, step2.remainingCount)
        assertEquals(250, step2.progressPermille)
        assertEquals(25, step2.progressPercent)

        val copy2 = DownloadBatchTracker.formatBatchNotification(step2)
        assertEquals("Downloading 1 / 5 songs", copy2.title)
        assertEquals("25% · 4 left", copy2.text)
        assertEquals("1 / 5", copy2.subText)

        // Slight backward fluctuation on active stream estimate keeps monotonic high-water mark.
        val step3 = tracker.buildSnapshot(
            activeDownloads = listOf(ActiveDownloadProgress("v2", 20f, 250_000L)),
            isPausedForRequirements = false,
            waitingForWifi = false
        )
        assertEquals(250, step3.progressPermille)
    }

    @Test
    fun `appending more songs mid-batch recalculates total cleanly without sticking high`() {
        val tracker = DownloadBatchTracker()
        tracker.registerPending(listOf("v1", "v2", "v3", "v4"))
        tracker.markCompleted("v1")
        tracker.markCompleted("v2")

        val beforeAppend = tracker.buildSnapshot(
            activeDownloads = listOf(ActiveDownloadProgress("v3", 0f, 0L)),
            isPausedForRequirements = false,
            waitingForWifi = false
        )
        assertEquals(500, beforeAppend.progressPermille)

        tracker.registerPending(listOf("v5", "v6", "v7", "v8"))
        val afterAppend = tracker.buildSnapshot(
            activeDownloads = listOf(ActiveDownloadProgress("v3", 0f, 0L)),
            isPausedForRequirements = false,
            waitingForWifi = false
        )
        assertEquals(8, afterAppend.totalCount)
        assertEquals(2, afterAppend.completedCount)
        assertEquals(250, afterAppend.progressPermille)
    }

    @Test
    fun `tracks failed and removed items accurately in remaining count and notification copy`() {
        val tracker = DownloadBatchTracker()
        tracker.registerPending(listOf("v1", "v2", "v3", "v4", "v5"))
        tracker.markCompleted("v1")
        tracker.markFailed("v2")
        tracker.markRemoved("v5")

        val snapshot = tracker.buildSnapshot(
            activeDownloads = listOf(ActiveDownloadProgress("v3", 50f, 100_000L)),
            isPausedForRequirements = false,
            waitingForWifi = false
        )

        assertEquals(4, snapshot.totalCount)
        assertEquals(1, snapshot.completedCount)
        assertEquals(1, snapshot.failedCount)
        assertEquals(2, snapshot.remainingCount)

        val copy = DownloadBatchTracker.formatBatchNotification(snapshot)
        assertEquals("Downloading 1 / 4 songs", copy.title)
        assertEquals("62% · 2 left · 1 failed", copy.text)
    }

    @Test
    fun `paused for requirements displays wifi or network reason while keeping batch counts`() {
        val tracker = DownloadBatchTracker()
        tracker.registerPending(listOf("v1", "v2", "v3", "v4", "v5"))
        tracker.markCompleted("v1")
        tracker.markCompleted("v2")

        val wifiPaused = tracker.buildSnapshot(
            activeDownloads = listOf(ActiveDownloadProgress("v3", -1f, 0L)),
            isPausedForRequirements = true,
            waitingForWifi = true
        )
        val wifiCopy = DownloadBatchTracker.formatBatchNotification(wifiPaused)
        assertEquals("Downloads paused · 2 / 5", wifiCopy.title)
        assertEquals("Waiting for Wi-Fi · 3 left", wifiCopy.text)
        assertFalse(wifiPaused.isIndeterminate)

        val networkPaused = tracker.buildSnapshot(
            activeDownloads = listOf(ActiveDownloadProgress("v3", -1f, 0L)),
            isPausedForRequirements = true,
            waitingForWifi = false
        )
        val netCopy = DownloadBatchTracker.formatBatchNotification(networkPaused)
        assertEquals("Waiting for network · 3 left", netCopy.text)
    }

    @Test
    fun `consumeCompletedSummaryAndReset summarizes batch and resets for next batch`() {
        val tracker = DownloadBatchTracker()
        assertNull(tracker.consumeCompletedSummaryAndReset())

        tracker.registerPending(listOf("v1", "v2", "v3"))
        tracker.markCompleted("v1")
        tracker.markCompleted("v2")
        tracker.markCompleted("v3")

        val summary = tracker.consumeCompletedSummaryAndReset()
        assertEquals(DownloadBatchSummary(totalCount = 3, completedCount = 3, failedCount = 0), summary)
        val doneCopy = DownloadBatchTracker.formatCompletionNotification(summary!!)
        assertEquals("Downloaded 3 songs", doneCopy.title)
        assertEquals("Ready for offline playback", doneCopy.text)
        assertEquals("3 / 3", doneCopy.subText)

        // Subsequent consume returns null until new batch runs.
        assertNull(tracker.consumeCompletedSummaryAndReset())

        // Single song and partial failure copy variants.
        val singleCopy = DownloadBatchTracker.formatCompletionNotification(
            DownloadBatchSummary(totalCount = 1, completedCount = 1, failedCount = 0)
        )
        assertEquals("Downloaded 1 song", singleCopy.title)

        val partialFailureCopy = DownloadBatchTracker.formatCompletionNotification(
            DownloadBatchSummary(totalCount = 4, completedCount = 3, failedCount = 1)
        )
        assertEquals("Downloaded 3 / 4 songs", partialFailureCopy.title)
        assertEquals("1 failed", partialFailureCopy.text)

        // Clearing active batch prevents spurious completion summary.
        tracker.registerPending(listOf("x1"))
        tracker.clear()
        assertNull(tracker.consumeCompletedSummaryAndReset())
    }
}
