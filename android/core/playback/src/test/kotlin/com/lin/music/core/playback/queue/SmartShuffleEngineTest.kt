package com.lin.music.core.playback.queue

import com.lin.music.core.model.ArtistRef
import com.lin.music.core.model.ShuffleMode
import com.lin.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartShuffleEngineTest {

    private fun tr(id: String, artist: String, durSec: Int = 180) = Track(
        videoId = id,
        title = "Track $id",
        artists = listOf(ArtistRef(null, artist)),
        durationMs = durSec * 1000L
    )

    @Test
    fun `artist spread avoids back-to-back tracks by same artist`() {
        val tracks = listOf(
            tr("1", "Artist A"), tr("2", "Artist A"), tr("3", "Artist A"),
            tr("4", "Artist B"), tr("5", "Artist B"),
            tr("6", "Artist C"), tr("7", "Artist C")
        )
        val shuffled = SmartShuffleEngine.shuffle(
            tracks = tracks,
            anchorIndex = 0,
            mode = ShuffleMode.ARTIST_SPREAD,
            random = Random(42)
        )
        assertEquals("1", shuffled.first().videoId)
        assertEquals(tracks.size, shuffled.size)
        for (i in 0 until shuffled.lastIndex) {
            assertNotEquals(
                "Adjacent duplicate artist at $i",
                shuffled[i].artists.first().name,
                shuffled[i + 1].artists.first().name
            )
        }
    }

    @Test
    fun `least played prioritizes lower play counts first and roundtrips in QueueEngine`() {
        val t1 = tr("v1", "A", 100)
        val t2 = tr("v2", "B", 200)
        val t3 = tr("v3", "C", 300)
        val engine = QueueEngine()
        engine.replaceWithContext(listOf(t1, t2, t3), startIndex = 0, sourceLabel = "album")
        // With position 40s (40_000ms) on t1(100s), remaining is 60 + 200 + 300 = 560s = 560_000ms
        assertEquals(560_000L, engine.snapshot.remainingDurationMs(40_000L))

        val shuffledSnap = engine.setShuffleWithOrder(
            enabled = true,
            mode = ShuffleMode.LEAST_PLAYED,
            playCounts = mapOf("v1" to 10, "v2" to 25, "v3" to 1)
        )
        assertTrue(shuffledSnap.shuffleEnabled)
        assertEquals("v1", shuffledSnap.items[0].videoId)
        assertEquals("v3", shuffledSnap.items[1].videoId)
        assertEquals("v2", shuffledSnap.items[2].videoId)

        val unshuffled = engine.setShuffleWithOrder(enabled = false, mode = ShuffleMode.LEAST_PLAYED)
        assertEquals(listOf("v1", "v2", "v3"), unshuffled.items.map { it.videoId })
        assertEquals(0, unshuffled.currentIndex)
    }
}
