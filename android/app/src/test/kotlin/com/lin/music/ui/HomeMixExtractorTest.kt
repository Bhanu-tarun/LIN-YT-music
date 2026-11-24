package com.lin.music.ui

import com.lin.music.core.model.HomeItem
import com.lin.music.core.model.HomeSection
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMixExtractorTest {

    private fun mixPlaylist(
        id: String,
        title: String,
        subtitle: String? = "Pritam, Arijit Singh, Vishal-Shekhar"
    ) = PlaylistSummary(
        id = id,
        browseId = if (id.startsWith("VL")) id else "VL$id",
        title = title,
        subtitle = subtitle
    )

    private fun song(id: String) = HomeItem.SongItem(
        Track(videoId = id, title = "Song $id", durationMs = 180_000L)
    )

    @Test
    fun `extractYoutubeMixes captures mixed for you personal mixes and marks shelf consumed`() {
        val supermix = mixPlaylist("RDTMAK5uy_kset8DisdE7LSD4TNjU6_1", "My Supermix")
        val mix1 = mixPlaylist("RDTMAK5uy_lY1", "My Mix 1")
        val discover = mixPlaylist("RDTMAK5uy_disc", "Discover Mix")
        val archive = mixPlaylist("RDTMAK5uy_arch", "Archive Mix")
        val regular = PlaylistSummary(
            id = "PL_abc",
            browseId = "VLPL_abc",
            title = "Roadtrip Hits",
            subtitle = "Community playlist"
        )

        val sections = listOf(
            HomeSection(
                title = "Speed dial",
                items = listOf(HomeItem.PlaylistItem(supermix), song("t1"))
            ),
            HomeSection(
                title = "Mixed for you",
                items = listOf(
                    HomeItem.PlaylistItem(supermix),
                    HomeItem.PlaylistItem(mix1),
                    HomeItem.PlaylistItem(discover),
                    HomeItem.PlaylistItem(archive)
                )
            ),
            HomeSection(
                title = "Community playlists",
                items = listOf(HomeItem.PlaylistItem(regular))
            )
        )

        val (extracted, consumed) = HomeViewModel.extractYoutubeMixes(sections)

        assertEquals(
            listOf("RDTMAK5uy_kset8DisdE7LSD4TNjU6_1", "RDTMAK5uy_lY1", "RDTMAK5uy_disc", "RDTMAK5uy_arch"),
            extracted.map { it.id }
        )
        assertEquals(setOf("Mixed for you"), consumed)
    }

    @Test
    fun `extractYoutubeMixes strictly excludes editorial RDCLAK playlists even with YouTube Music or Made for you subtitles`() {
        val bollywoodEssentials = PlaylistSummary(
            id = "RDCLAK5uy_editorial_bolly",
            browseId = "VLRDCLAK5uy_editorial_bolly",
            title = "Bollywood Essential Mix",
            subtitle = "YouTube Music"
        )
        val bollywoodHits = PlaylistSummary(
            id = "RDCLAK5uy_editorial_hits",
            browseId = "VLRDCLAK5uy_editorial_hits",
            title = "Bollywood Hitlist",
            subtitle = "Made for you"
        )
        val editorialInMixedShelf = PlaylistSummary(
            id = "RDCLAK5uy_editorial_in_mixed",
            browseId = "VLRDCLAK5uy_editorial_in_mixed",
            title = "Bollywood 2010s Essentials",
            subtitle = "YouTube Music"
        )
        val replayMix = mixPlaylist("RDTMAK5uy_replay", "Replay Mix")

        val sections = listOf(
            HomeSection(
                title = "Made for you",
                items = listOf(
                    HomeItem.PlaylistItem(bollywoodEssentials),
                    HomeItem.PlaylistItem(bollywoodHits)
                )
            ),
            HomeSection(
                title = "Mixed for you",
                items = listOf(
                    HomeItem.PlaylistItem(editorialInMixedShelf),
                    HomeItem.PlaylistItem(replayMix)
                )
            )
        )

        val (extracted, consumed) = HomeViewModel.extractYoutubeMixes(sections)

        assertEquals(listOf("RDTMAK5uy_replay"), extracted.map { it.id })
        assertTrue(consumed.isEmpty())
    }

    @Test
    fun `extractYoutubeMixes identifies personal mixes inside listen again without consuming mixed song shelf`() {
        val discoverMix = PlaylistSummary(
            id = "RDMM_disc_123",
            browseId = "VLRDMM_disc_123",
            title = "Discover Mix",
            subtitle = null
        )
        val newReleaseMix = PlaylistSummary(
            id = "PL_custom_or_dyn",
            browseId = "VLPL_custom_or_dyn",
            title = "New Release Mix",
            subtitle = "Updated every Friday"
        )
        val sections = listOf(
            HomeSection(
                title = "Listen again",
                items = listOf(
                    song("v1"),
                    HomeItem.PlaylistItem(discoverMix),
                    HomeItem.PlaylistItem(newReleaseMix)
                )
            )
        )

        val (extracted, consumed) = HomeViewModel.extractYoutubeMixes(sections)

        assertEquals(listOf(discoverMix, newReleaseMix), extracted)
        assertTrue(consumed.isEmpty())
    }

    @Test
    fun `extractYoutubeMixes ignores plain community and RDAMPL editorial playlists without personal mix signals`() {
        val community = PlaylistSummary(
            id = "PL_community_only",
            browseId = "VLPL_community_only",
            title = "Late Night Lo-Fi",
            subtitle = "YouTube Music"
        )
        val rdamplCurated = PlaylistSummary(
            id = "RDAMPL_curated",
            browseId = "VLRDAMPL_curated",
            title = "Indie Bollywood",
            subtitle = "Made for you"
        )
        val sections = listOf(
            HomeSection(
                title = "Recommended playlists",
                items = listOf(
                    HomeItem.PlaylistItem(community),
                    HomeItem.PlaylistItem(rdamplCurated)
                )
            )
        )

        val (extracted, consumed) = HomeViewModel.extractYoutubeMixes(sections)

        assertTrue(extracted.isEmpty())
        assertTrue(consumed.isEmpty())
    }

    @Test
    fun `extractDedicatedMixes captures FEmusic_mixed_for_you grid items excluding LM and RDCLAK`() {
        val likedMusic = PlaylistSummary(
            id = "LM",
            browseId = "VLLM",
            title = "Liked Music",
            subtitle = "Auto playlist"
        )
        val bollywood = PlaylistSummary(
            id = "RDCLAK5uy_bolly",
            browseId = "VLRDCLAK5uy_bolly",
            title = "Bollywood Essential Mix",
            subtitle = "YouTube Music"
        )
        val supermix = mixPlaylist("RDTMAK5uy_kset", "My Supermix")
        val discover = mixPlaylist("RDTMAK5uy_n5I", "Discover Mix")
        val archive = mixPlaylist("RDTMAK5uy_mZtX", "Archive Mix")

        val mfySections = listOf(
            HomeSection(
                title = "Speed dial",
                items = listOf(
                    HomeItem.PlaylistItem(likedMusic),
                    HomeItem.PlaylistItem(bollywood),
                    HomeItem.PlaylistItem(supermix),
                    HomeItem.PlaylistItem(discover),
                    HomeItem.PlaylistItem(archive)
                )
            )
        )

        val extracted = HomeViewModel.extractDedicatedMixes(mfySections)
        assertEquals(listOf("My Supermix", "Discover Mix", "Archive Mix"), extracted.map { it.title })
    }

    @Test
    fun `mergeAndPrioritizePersonalMixes places flagship personal mixes first and deduplicates`() {
        val mood = mixPlaylist("RDTMAK5uy_chill1", "Chill Mix 1")
        val custom = mixPlaylist("RDTMAK5uy_nGQK", "Melancholic Echoes and Distant Dreams")
        val archive = mixPlaylist("RDTMAK5uy_mZtX", "Archive Mix")
        val discover = mixPlaylist("RDTMAK5uy_n5I", "Discover Mix")
        val replay = mixPlaylist("RDTMAK5uy_nilr", "Replay Mix")
        val newRelease = mixPlaylist("RDTMAK5uy_lr0L", "New Release Mix")
        val supermix = mixPlaylist("RDTMAK5uy_kset", "My Supermix")
        val bollywood = PlaylistSummary(
            id = "RDCLAK5uy_bolly",
            browseId = "VLRDCLAK5uy_bolly",
            title = "Shaadi Essentials"
        )

        val merged = HomeViewModel.mergeAndPrioritizePersonalMixes(
            homeMixes = listOf(custom, supermix),
            dedicatedMixes = listOf(mood, replay, archive, discover, newRelease, supermix, bollywood)
        )

        assertEquals(
            listOf(
                "My Supermix",
                "Discover Mix",
                "Archive Mix",
                "Replay Mix",
                "New Release Mix",
                "Melancholic Echoes and Distant Dreams",
                "Chill Mix 1"
            ),
            merged.map { it.title }
        )
    }
}
