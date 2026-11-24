package com.lin.music.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class ListeningStatsCalculatorTest {

    @Test
    fun `aggregates listening duration, top rankings, peak hour, and consecutive streak`() {
        val utc = TimeZone.getTimeZone("UTC")
        val hourMs = 60L * 60L * 1000L
        val dayMs = 24L * hourMs
        val day0Start = 1_700_006_400_000L - (1_700_006_400_000L % dayMs) // midnight UTC
        val nowMs = day0Start + 18L * hourMs

        val tA = Track(
            videoId = "a",
            title = "Song A",
            artists = listOf(ArtistRef("ar1", "Artist One")),
            album = AlbumRef("al1", "Album One")
        )
        val tB = Track(
            videoId = "b",
            title = "Song B",
            artists = listOf(ArtistRef("ar2", "Artist Two")),
            album = AlbumRef("al2", "Album Two")
        )
        val tracks = mapOf("a" to tA, "b" to tB)

        val sessions = listOf(
            PlaySessionRecord("a", 120_000L, day0Start + 14L * hourMs),
            PlaySessionRecord("a", 180_000L, day0Start + 14L * hourMs + 300_000L),
            PlaySessionRecord("b", 90_000L, day0Start - dayMs + 9L * hourMs),
            PlaySessionRecord("b", 60_000L, day0Start - 2L * dayMs + 10L * hourMs),
            // Gap of 1 day at day0 - 3d; day0 - 4d should NOT count in current streak
            PlaySessionRecord("a", 200_000L, day0Start - 4L * dayMs + 8L * hourMs)
        )

        val summary7d = ListeningStatsCalculator.calculate(sessions, tracks, StatsRange.SEVEN_DAYS, nowMs, utc)
        assertEquals(650_000L, summary7d.totalListenedMs)
        assertEquals(5, summary7d.totalPlays)
        assertEquals(14, summary7d.peakHourOfDay)
        assertEquals(3, summary7d.activeStreakDays)
        assertEquals("a", summary7d.topTracks.first().track.videoId)
        assertEquals("Artist One", summary7d.topArtists.first().name)
        assertEquals("Album One", summary7d.topAlbums.first().title)

        val todayOnly = ListeningStatsCalculator.calculate(sessions, tracks, StatsRange.TODAY, nowMs, utc)
        assertEquals(300_000L, todayOnly.totalListenedMs)
        assertEquals(2, todayOnly.totalPlays)
    }
}
