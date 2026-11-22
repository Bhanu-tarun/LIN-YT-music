package com.lin.music.core.playback.download

import com.lin.music.core.data.database.DownloadEntity
import com.lin.music.core.data.database.TrackEntity
import com.lin.music.core.data.database.TrackListenStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartDownloadManagerTest {

    private fun track(
        id: String,
        playCount: Int = 1,
        isLiked: Boolean = false,
        inLibrary: Boolean = false,
        lastPlayMs: Long = 1_000_000_000L
    ) = TrackEntity(
        videoId = id,
        title = "Title $id",
        artistsJson = "[]",
        albumId = null,
        albumTitle = null,
        durationMs = 180_000L,
        thumbnailUrl = null,
        inLibrary = inLibrary,
        isLiked = isLiked,
        inHistory = true,
        historyTimestampMs = lastPlayMs,
        feedbackTokenAdd = null,
        feedbackTokenRemove = null,
        isExplicit = false,
        playCount = playCount
    )

    private fun smartDownload(id: String, updatedAtMs: Long, state: Int = 2) = DownloadEntity(
        videoId = id,
        state = state,
        progressPercent = 100f,
        bytesDownloaded = 1024L,
        totalBytes = 1024L,
        sourceFlags = DownloadEntity.SOURCE_SMART,
        updatedAtMs = updatedAtMs
    )

    @Test
    fun `keeps recent downloads sticky under 7 day retention window even if challenger scores higher`() {
        val nowMs = 10_000_000_000L
        val threeDaysAgo = nowMs - 3L * 24L * 60L * 60_000L
        val incumbents = (1..5).map { idx -> smartDownload("inc_$idx", updatedAtMs = threeDaysAgo) }
        val incumbentTracks = (1..5).associate { idx ->
            "inc_$idx" to track("inc_$idx", playCount = 2, lastPlayMs = threeDaysAgo)
        }
        val strongChallenger = track("hot_new", playCount = 50, isLiked = true, lastPlayMs = nowMs)
        val plan = SmartDownloadManager.planSmartDownloadUpdates(
            currentSmart = incumbents,
            candidateTracks = incumbentTracks.values.toList() + strongChallenger,
            incumbentTracksById = incumbentTracks,
            pinnedVideoIds = emptySet(),
            limit = 5,
            nowMs = nowMs
        )
        assertTrue(plan.removeIds.isEmpty())
        assertTrue(plan.addTracks.isEmpty())
    }

    @Test
    fun `rotates only stale unpinned downloads with hysteresis and caps churn at 15 percent`() {
        val nowMs = 20_000_000_000L
        val tenDaysAgo = nowMs - 10L * 24L * 60L * 60_000L
        val twoDaysAgo = nowMs - 2L * 24L * 60L * 60_000L
        // Cap = 20 -> max 15% = 3 swaps per daily refresh
        val incumbents = (1..20).map { idx ->
            smartDownload(
                id = "inc_$idx",
                updatedAtMs = if (idx <= 10) tenDaysAgo else twoDaysAgo
            )
        }
        val incumbentTracks = (1..20).associate { idx ->
            "inc_$idx" to track("inc_$idx", playCount = 2, lastPlayMs = tenDaysAgo)
        }
        val challengers = (1..6).map { idx ->
            track("chal_$idx", playCount = 40, isLiked = true, lastPlayMs = nowMs)
        }

        val plan = SmartDownloadManager.planSmartDownloadUpdates(
            currentSmart = incumbents,
            candidateTracks = challengers + incumbentTracks.values,
            incumbentTracksById = incumbentTracks,
            pinnedVideoIds = setOf("inc_1"),
            limit = 20,
            nowMs = nowMs
        )

        assertEquals(3, plan.removeIds.size)
        assertEquals(3, plan.addTracks.size)
        // Pinned stale track inc_1 must NEVER be evicted
        assertFalse("inc_1" in plan.removeIds)
    }

    @Test
    fun `ignores single-play random exploration as challenger when full`() {
        val nowMs = 20_000_000_000L
        val tenDaysAgo = nowMs - 10L * 24L * 60L * 60_000L
        val incumbents = listOf(smartDownload("inc_1", updatedAtMs = tenDaysAgo))
        val incumbentTracks = mapOf("inc_1" to track("inc_1", playCount = 0, lastPlayMs = tenDaysAgo))
        val singlePlay = track("one_off", playCount = 1, isLiked = false, lastPlayMs = nowMs)

        val plan = SmartDownloadManager.planSmartDownloadUpdates(
            currentSmart = incumbents,
            candidateTracks = listOf(singlePlay),
            incumbentTracksById = incumbentTracks,
            pinnedVideoIds = emptySet(),
            limit = 1,
            nowMs = nowMs
        )
        assertTrue(plan.removeIds.isEmpty())
        assertTrue(plan.addTracks.isEmpty())
    }

    @Test
    fun `recently played selects 24h longest listened first and stops at limit`() {
        val nowMs = 30_000_000_000L
        // Short track replayed for 900s must outrank a 10-minute track skipped after 10s
        val stats24h = listOf(
            TrackListenStat("long_track_skipped", totalListenedMs = 10_000L, lastPlayedAtMs = nowMs - 1_000L),
            TrackListenStat("short_looped", totalListenedMs = 900_000L, lastPlayedAtMs = nowMs - 60_000L),
            TrackListenStat("medium_a", totalListenedMs = 300_000L, lastPlayedAtMs = nowMs - 30_000L),
            TrackListenStat("medium_b_newer_tie", totalListenedMs = 300_000L, lastPlayedAtMs = nowMs - 5_000L)
        )
        val tracksById = mapOf(
            "long_track_skipped" to track("long_track_skipped").copy(durationMs = 600_000L),
            "short_looped" to track("short_looped").copy(durationMs = 150_000L),
            "medium_a" to track("medium_a"),
            "medium_b_newer_tie" to track("medium_b_newer_tie")
        )

        val selected = SmartDownloadManager.selectRecentlyPlayedTracks(
            stats24h = stats24h,
            stats7d = emptyList(),
            tracksById = tracksById,
            fallbackHistory = emptyList(),
            limit = 3
        )

        assertEquals(
            listOf("short_looped", "medium_b_newer_tie", "medium_a"),
            selected.map { it.videoId }
        )
    }

    @Test
    fun `recently played fills spare slots from 7d then fallback history without duplicates`() {
        val nowMs = 30_000_000_000L
        val stats24h = listOf(
            TrackListenStat("today_low", totalListenedMs = 30_000L, lastPlayedAtMs = nowMs - 10_000L)
        )
        // Even though old_heavy has more listen time than today_low, 24h tracks come FIRST.
        val stats7d = listOf(
            TrackListenStat("today_low", totalListenedMs = 30_000L, lastPlayedAtMs = nowMs - 10_000L),
            TrackListenStat("old_heavy", totalListenedMs = 800_000L, lastPlayedAtMs = nowMs - 3L * 24L * 60L * 60_000L),
            TrackListenStat("old_light", totalListenedMs = 120_000L, lastPlayedAtMs = nowMs - 4L * 24L * 60L * 60_000L)
        )
        val tracksById = mapOf(
            "today_low" to track("today_low"),
            "old_heavy" to track("old_heavy"),
            "old_light" to track("old_light")
        )
        val fallbackHistory = listOf(
            track("old_light"),
            track("hist_1"),
            track("hist_2")
        )

        val selected = SmartDownloadManager.selectRecentlyPlayedTracks(
            stats24h = stats24h,
            stats7d = stats7d,
            tracksById = tracksById,
            fallbackHistory = fallbackHistory,
            limit = 4
        )

        assertEquals(
            listOf("today_low", "old_heavy", "old_light", "hist_1"),
            selected.map { it.videoId }
        )
    }

    @Test
    fun `recently played refresh interval is 24 hours`() {
        assertEquals(24L * 60L * 60_000L, SmartDownloadManager.RECENT_SYNC_INTERVAL_MS)
    }
}
