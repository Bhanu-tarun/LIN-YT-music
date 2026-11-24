package com.lin.music.core.playback.queue

import com.lin.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartMixQueueTest {

    private fun track(id: String) = Track(
        videoId = id,
        title = "Track $id",
        durationMs = 180_000L
    )

    @Test
    fun `buildMixQueueKeepingCurrent keeps currently playing seed track uninterrupted at index 0`() {
        val active = track("cur_1").copy(isLiked = true)
        val fetched = listOf(track("mix_a"), track("cur_1"), track("mix_b"))

        val (result, keepActive) = PlayerController.buildMixQueueKeepingCurrent(
            activeTrack = active,
            seedVideoId = "cur_1",
            fetchedMixTracks = fetched
        )

        assertTrue(keepActive)
        assertEquals(listOf("cur_1", "mix_a", "mix_b"), result.map { it.videoId })
        assertTrue(result.first().isLiked)
    }

    @Test
    fun `buildMixQueueKeepingCurrent preserves active track when selecting mix chip on same song`() {
        val active = track("cur_2")
        val fetched = listOf(track("cur_2"), track("chip_1"), track("chip_2"))

        val (result, keepActive) = PlayerController.buildMixQueueKeepingCurrent(
            activeTrack = active,
            seedVideoId = "cur_2",
            fetchedMixTracks = fetched
        )

        assertTrue(keepActive)
        assertEquals(listOf("cur_2", "chip_1", "chip_2"), result.map { it.videoId })
    }

    @Test
    fun `buildMixQueueKeepingCurrent keeps active track when starting playlist mix containing it`() {
        val active = track("pl_cur")
        val fetched = listOf(track("other_1"), track("pl_cur"), track("other_2"))

        val (result, keepActive) = PlayerController.buildMixQueueKeepingCurrent(
            activeTrack = active,
            seedVideoId = null,
            fetchedMixTracks = fetched
        )

        assertTrue(keepActive)
        assertEquals(listOf("pl_cur", "other_1", "other_2"), result.map { it.videoId })
    }

    @Test
    fun `buildMixQueueKeepingCurrent starts fresh when starting mix for a different song`() {
        val active = track("playing_now")
        val fetched = listOf(track("different_seed"), track("next_1"), track("next_2"))

        val (result, keepActive) = PlayerController.buildMixQueueKeepingCurrent(
            activeTrack = active,
            seedVideoId = "different_seed",
            fetchedMixTracks = fetched
        )

        assertFalse(keepActive)
        assertEquals(fetched, result)
    }
}
