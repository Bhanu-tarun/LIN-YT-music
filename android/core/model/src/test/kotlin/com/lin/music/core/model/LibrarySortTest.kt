package com.lin.music.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LibrarySortTest {

    private fun track(
        id: String,
        title: String,
        artists: List<String> = emptyList(),
        durationMs: Long = 0L
    ) = Track(
        videoId = id,
        title = title,
        artists = artists.map { ArtistRef(name = it) },
        durationMs = durationMs
    )

    private fun album(
        id: String,
        title: String,
        artist: String = "",
        year: String? = null
    ) = AlbumSummary(
        browseId = id,
        title = title,
        artists = if (artist.isBlank()) emptyList() else listOf(ArtistRef(name = artist)),
        year = year
    )

    private fun artist(
        id: String,
        name: String,
        subscriberCount: String? = null
    ) = ArtistSummary(
        browseId = id,
        name = name,
        subscriberCount = subscriberCount
    )

    @Test
    fun `sortTracks handles title artist duration and random consistent with ShuffleMode`() {
        val t1 = track("v1", "beta", artists = listOf("Zed"), durationMs = 200_000L)
        val t2 = track("v2", "Alpha", artists = listOf("amy"), durationMs = 90_000L)
        val t3 = track("v3", "gamma", artists = emptyList(), durationMs = 0L)
        val input = listOf(t1, t2, t3)

        assertEquals(listOf("v2", "v1", "v3"), LibrarySorter.sortTracks(input, SongSortOrder.TITLE_ASC).map { it.videoId })
        assertEquals(listOf("v3", "v1", "v2"), LibrarySorter.sortTracks(input, SongSortOrder.TITLE_DESC).map { it.videoId })
        // Tracks with empty artists are pushed last regardless of "Unknown Artist" display fallback.
        assertEquals(listOf("v2", "v1", "v3"), LibrarySorter.sortTracks(input, SongSortOrder.ARTIST_ASC).map { it.videoId })
        // Zero/unknown duration is pushed last in both shortest and longest.
        assertEquals(listOf("v2", "v1", "v3"), LibrarySorter.sortTracks(input, SongSortOrder.DURATION_ASC).map { it.videoId })
        assertEquals(listOf("v1", "v2", "v3"), LibrarySorter.sortTracks(input, SongSortOrder.DURATION_DESC).map { it.videoId })

        val trueRandomA = LibrarySorter.sortTracks(
            tracks = input,
            order = SongSortOrder.RANDOM,
            shuffleMode = ShuffleMode.TRUE_RANDOM,
            random = Random(99)
        ).map { it.videoId }
        val trueRandomB = LibrarySorter.sortTracks(
            tracks = input,
            order = SongSortOrder.RANDOM,
            shuffleMode = ShuffleMode.TRUE_RANDOM,
            random = Random(99)
        ).map { it.videoId }
        assertEquals(trueRandomA, trueRandomB)
        assertEquals(input.map { it.videoId }.toSet(), trueRandomA.toSet())

        val spreadPool = listOf(
            track("s1", "One", artists = listOf("Artist A")),
            track("s2", "Two", artists = listOf("Artist A")),
            track("s3", "Three", artists = listOf("Artist B")),
            track("s4", "Four", artists = listOf("Artist B")),
            track("s5", "Five", artists = listOf("Artist C")),
            track("s6", "Six", artists = listOf("Artist C"))
        )
        val spread = LibrarySorter.sortTracks(
            tracks = spreadPool,
            order = SongSortOrder.RANDOM,
            shuffleMode = ShuffleMode.ARTIST_SPREAD,
            random = Random(7)
        )
        for (i in 0 until spread.lastIndex) {
            assertNotEquals(
                "Adjacent duplicate artist at $i",
                spread[i].artistDisplay,
                spread[i + 1].artistDisplay
            )
        }

        val leastPlayed = LibrarySorter.sortTracks(
            tracks = input,
            order = SongSortOrder.RANDOM,
            shuffleMode = ShuffleMode.LEAST_PLAYED,
            playCounts = mapOf("v1" to 25, "v2" to 5, "v3" to 0),
            random = Random(12)
        ).map { it.videoId }
        assertEquals(listOf("v3", "v2", "v1"), leastPlayed)
    }

    @Test
    fun `sortAlbums handles title artist and parsed release year`() {
        val a1 = album("b1", "Echoes", artist = "Zara", year = "Album • 2021")
        val a2 = album("b2", "amber", artist = "Abel", year = "1998")
        val a3 = album("b3", "Coastline", artist = "", year = null)
        val input = listOf(a1, a2, a3)

        assertEquals(listOf("b2", "b3", "b1"), LibrarySorter.sortAlbums(input, AlbumSortOrder.TITLE_ASC).map { it.browseId })
        assertEquals(listOf("b1", "b3", "b2"), LibrarySorter.sortAlbums(input, AlbumSortOrder.TITLE_DESC).map { it.browseId })
        assertEquals(listOf("b2", "b1", "b3"), LibrarySorter.sortAlbums(input, AlbumSortOrder.ARTIST_ASC).map { it.browseId })
        assertEquals(listOf("b1", "b2", "b3"), LibrarySorter.sortAlbums(input, AlbumSortOrder.YEAR_DESC).map { it.browseId })
        assertEquals(listOf("b2", "b1", "b3"), LibrarySorter.sortAlbums(input, AlbumSortOrder.YEAR_ASC).map { it.browseId })
        assertEquals(2021, LibrarySorter.parseAlbumYear("Album • 2021"))
        assertNull(LibrarySorter.parseAlbumYear("Single"))
    }

    @Test
    fun `sortArtists and parseSubscriberCount handle suffixes without misreading monthly audience`() {
        assertEquals(1_200_000L, LibrarySorter.parseSubscriberCount("1.2M subscribers"))
        assertEquals(450_000L, LibrarySorter.parseSubscriberCount("450K monthly audience"))
        assertEquals(12_340L, LibrarySorter.parseSubscriberCount("12,340 subscribers"))
        assertEquals(123L, LibrarySorter.parseSubscriberCount("123 monthly audience"))
        assertEquals(2_500_000_000L, LibrarySorter.parseSubscriberCount("2.5B"))
        assertNull(LibrarySorter.parseSubscriberCount(null))

        val r1 = artist("ar1", "zeta", "123 monthly audience")
        val r2 = artist("ar2", "Alpha", "1.2M subscribers")
        val r3 = artist("ar3", "mid", "450K monthly audience")
        val r4 = artist("ar4", "NoStat", null)
        val input = listOf(r1, r2, r3, r4)

        assertEquals(listOf("ar2", "ar3", "ar4", "ar1"), LibrarySorter.sortArtists(input, ArtistSortOrder.NAME_ASC).map { it.browseId })
        assertEquals(listOf("ar1", "ar4", "ar3", "ar2"), LibrarySorter.sortArtists(input, ArtistSortOrder.NAME_DESC).map { it.browseId })
        assertEquals(listOf("ar2", "ar3", "ar1", "ar4"), LibrarySorter.sortArtists(input, ArtistSortOrder.SUBSCRIBERS_DESC).map { it.browseId })
    }

    @Test
    fun `filterCountries prioritizes exact ISO code matches and handles blank queries`() {
        val all = ContentRegions.filterCountries("   ")
        assertEquals(ContentRegions.allCountries().size, all.size)

        val jp = ContentRegions.filterCountries("jp")
        assertTrue(jp.isNotEmpty())
        assertEquals("JP", jp.first().code)

        val united = ContentRegions.filterCountries("united")
        assertTrue(united.any { it.code == "US" })
        assertTrue(united.any { it.code == "GB" })
    }
}
