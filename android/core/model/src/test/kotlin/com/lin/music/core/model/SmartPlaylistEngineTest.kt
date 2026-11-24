package com.lin.music.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistEngineTest {

    private fun track(
        id: String,
        title: String = "Track $id",
        artist: String = "Artist A",
        durationSec: Int = 200
    ) = Track(
        videoId = id,
        title = title,
        artists = listOf(ArtistRef(id = null, name = artist)),
        durationMs = durationSec * 1000L
    )

    @Test
    fun `duplicate finder flags exact videoId and remastered fuzzy matches cleanly`() {
        val list = listOf(
            track("id1", title = "Midnight City", artist = "M83", durationSec = 243),
            track("id1", title = "Midnight City", artist = "M83", durationSec = 243),
            track("id2", title = "Midnight City (Remastered 2023)", artist = "M83", durationSec = 245),
            track("id3", title = "Outro", artist = "M83", durationSec = 247)
        )
        val indices = DuplicateTrackFinder.duplicateIndices(list)
        assertEquals(setOf(1, 2), indices)
        assertEquals(2, DuplicateTrackFinder.findDuplicates(list).size)
    }

    @Test
    fun `duplicate finder ignores distinct artists or wildly different durations`() {
        val list = listOf(
            track("a1", title = "Runaway", artist = "Aurora", durationSec = 248),
            track("a2", title = "Runaway", artist = "Kanye West", durationSec = 548),
            track("a3", title = "Runaway", artist = "Aurora", durationSec = 420) // live extended version (>6s diff)
        )
        val groups = DuplicateTrackFinder.findDuplicates(list)
        assertTrue(groups.isEmpty())
    }
}
