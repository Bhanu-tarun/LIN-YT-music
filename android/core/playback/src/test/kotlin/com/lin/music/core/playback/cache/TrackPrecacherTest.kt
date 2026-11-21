package com.lin.music.core.playback.cache

import androidx.media3.common.C
import com.lin.music.core.model.AudioQuality
import com.lin.music.core.model.Track
import com.lin.music.core.playback.di.PlaybackModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPrecacherTest {

    private fun track(id: String) = Track(videoId = id, title = "Title $id")

    @Test
    fun `selectTargets excludes active track and blank ids`() {
        val upcoming = listOf(
            track("active_1"),
            track(""),
            track("next_1"),
            track("next_2"),
            track("next_3")
        )
        val targets = TrackPrecacher.selectTargets(upcoming, activeVideoId = "active_1")
        assertEquals(listOf("next_1", "next_2"), targets.map { it.videoId })
    }

    @Test
    fun `selectTargets deduplicates and caps at two tracks`() {
        val upcoming = listOf(
            track("a"),
            track("a"),
            track("b"),
            track("c")
        )
        val targets = TrackPrecacher.selectTargets(upcoming, activeVideoId = "other")
        assertEquals(listOf("a", "b"), targets.map { it.videoId })
    }

    @Test
    fun `selectTargets returns empty when no valid upcoming tracks`() {
        val targets = TrackPrecacher.selectTargets(listOf(track("only_active")), activeVideoId = "only_active")
        assertTrue(targets.isEmpty())
    }

    @Test
    fun `buildPhasePlan schedules 512KB head of both tracks before full completion`() {
        val targets = listOf(track("t1"), track("t2"))
        val plan = TrackPrecacher.buildPhasePlan(targets)
        assertEquals(4, plan.size)
        assertEquals("t1" to TrackPrecacher.HEAD_PRECACHE_BYTES, plan[0].track.videoId to plan[0].lengthBytes)
        assertEquals("t2" to TrackPrecacher.HEAD_PRECACHE_BYTES, plan[1].track.videoId to plan[1].lengthBytes)
        assertEquals("t1" to C.LENGTH_UNSET.toLong(), plan[2].track.videoId to plan[2].lengthBytes)
        assertEquals("t2" to C.LENGTH_UNSET.toLong(), plan[3].track.videoId to plan[3].lengthBytes)
    }

    @Test
    fun `buildPhasePlan returns empty list for empty targets`() {
        assertTrue(TrackPrecacher.buildPhasePlan(emptyList()).isEmpty())
    }

    @Test
    fun `streamCacheKey scopes keys distinctly per AudioQuality and preserves underscore or dash ids`() {
        val videoId = "abc_123-XYZ"
        val highKey = PlaybackModule.streamCacheKey(videoId, AudioQuality.HIGH)
        val normalKey = PlaybackModule.streamCacheKey(videoId, AudioQuality.NORMAL)
        assertEquals("abc_123-XYZ:HIGH", highKey)
        assertEquals("abc_123-XYZ:NORMAL", normalKey)
        assertNotEquals(highKey, normalKey)
    }

    @Test
    fun `isStreamCacheKeyForVideo matches legacy and quality keys without prefix collisions`() {
        assertTrue(PlaybackModule.isStreamCacheKeyForVideo("abc_def", "abc_def"))
        assertTrue(PlaybackModule.isStreamCacheKeyForVideo("abc_def:HIGH", "abc_def"))
        assertFalse(PlaybackModule.isStreamCacheKeyForVideo("abc_def:HIGH", "abc"))
        assertFalse(PlaybackModule.isStreamCacheKeyForVideo("other:HIGH", "abc"))
    }
}
