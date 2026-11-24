package com.lin.music.core.model

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.random.Random

@Serializable
enum class SongSortOrder(val label: String) {
    TITLE_ASC("title (a–z)"),
    TITLE_DESC("title (z–a)"),
    ARTIST_ASC("artist (a–z)"),
    DURATION_ASC("shortest"),
    DURATION_DESC("longest"),
    RANDOM("random")
}

@Serializable
enum class AlbumSortOrder(val label: String) {
    TITLE_ASC("title (a–z)"),
    TITLE_DESC("title (z–a)"),
    ARTIST_ASC("artist (a–z)"),
    YEAR_DESC("newest"),
    YEAR_ASC("oldest")
}

@Serializable
enum class ArtistSortOrder(val label: String) {
    NAME_ASC("name (a–z)"),
    NAME_DESC("name (z–a)"),
    SUBSCRIBERS_DESC("most subscribers")
}

object LibrarySorter {
    private val yearRegex = Regex("""\b(\d{4})\b""")
    private val subscriberRegex = Regex(
        """(\d[\d,]*(?:\.\d+)?)(?:\s*([KMB])(?![A-Za-z]))?""",
        RegexOption.IGNORE_CASE
    )

    fun sortTracks(
        tracks: List<Track>,
        order: SongSortOrder,
        shuffleMode: ShuffleMode = ShuffleMode.TRUE_RANDOM,
        playCounts: Map<String, Int> = emptyMap(),
        random: Random = Random.Default
    ): List<Track> = when (order) {
        SongSortOrder.TITLE_ASC -> tracks.sortedWith(
            compareBy<Track> { normalizeSortKey(it.title) }
                .thenBy { normalizeSortKey(it.artistDisplay) }
                .thenBy { it.videoId }
        )
        SongSortOrder.TITLE_DESC -> tracks.sortedWith(
            compareByDescending<Track> { normalizeSortKey(it.title) }
                .thenBy { normalizeSortKey(it.artistDisplay) }
                .thenBy { it.videoId }
        )
        SongSortOrder.ARTIST_ASC -> tracks.sortedWith(
            compareBy<Track> { it.artists.isEmpty() }
                .thenBy { normalizeSortKey(it.artistDisplay) }
                .thenBy { normalizeSortKey(it.title) }
                .thenBy { it.videoId }
        )
        SongSortOrder.DURATION_ASC -> tracks.sortedWith(
            compareBy<Track> { it.durationMs <= 0L }
                .thenBy { it.durationMs }
                .thenBy { normalizeSortKey(it.title) }
                .thenBy { it.videoId }
        )
        SongSortOrder.DURATION_DESC -> tracks.sortedWith(
            compareBy<Track> { it.durationMs <= 0L }
                .thenByDescending { it.durationMs }
                .thenBy { normalizeSortKey(it.title) }
                .thenBy { it.videoId }
        )
        SongSortOrder.RANDOM -> shuffleTracks(
            tracks = tracks,
            anchorIndex = null,
            mode = shuffleMode,
            playCounts = playCounts,
            random = random
        )
    }

    fun shuffleTracks(
        tracks: List<Track>,
        anchorIndex: Int? = null,
        mode: ShuffleMode = ShuffleMode.TRUE_RANDOM,
        playCounts: Map<String, Int> = emptyMap(),
        random: Random = Random.Default
    ): List<Track> {
        if (tracks.size <= 1) return tracks
        val validAnchor = anchorIndex?.takeIf { it in tracks.indices }
        val anchor = validAnchor?.let { tracks[it] }
        val pool = if (validAnchor != null) {
            tracks.filterIndexed { index, _ -> index != validAnchor }
        } else {
            tracks
        }

        val orderedPool = when (mode) {
            ShuffleMode.TRUE_RANDOM -> pool.shuffled(random)
            ShuffleMode.ARTIST_SPREAD -> artistSpreadShuffle(
                pool = pool,
                previousArtistKey = anchor?.let(::primaryArtistKey),
                random = random
            )
            ShuffleMode.LEAST_PLAYED -> leastPlayedShuffle(
                pool = pool,
                playCounts = playCounts,
                random = random
            )
        }

        return if (anchor != null) listOf(anchor) + orderedPool else orderedPool
    }

    private fun artistSpreadShuffle(
        pool: List<Track>,
        previousArtistKey: String?,
        random: Random
    ): List<Track> {
        if (pool.size <= 2) return pool.shuffled(random)

        val buckets = linkedMapOf<String, MutableList<Track>>()
        pool.shuffled(random).forEachIndexed { idx, track ->
            val key = primaryArtistKey(track) ?: "_unknown_$idx"
            buckets.getOrPut(key) { mutableListOf() }.add(track)
        }

        val result = ArrayList<Track>(pool.size)
        var lastArtist: String? = previousArtistKey

        while (buckets.isNotEmpty()) {
            val candidates = buckets.entries.filter { it.key != lastArtist }
            val chosenPool = if (candidates.isNotEmpty()) candidates else buckets.entries.toList()
            val maxRemaining = chosenPool.maxOf { it.value.size }
            // Bias slightly toward larger artist buckets so we don't strand repeats at the end
            val topBuckets = chosenPool.filter { it.value.size >= (maxRemaining - 1).coerceAtLeast(1) }
            val chosen = topBuckets[random.nextInt(topBuckets.size)]

            val nextTrack = chosen.value.removeAt(chosen.value.lastIndex)
            result.add(nextTrack)
            lastArtist = chosen.key
            if (chosen.value.isEmpty()) {
                buckets.remove(chosen.key)
            }
        }
        return result
    }

    private fun leastPlayedShuffle(
        pool: List<Track>,
        playCounts: Map<String, Int>,
        random: Random
    ): List<Track> {
        val tiers = pool.groupBy { (playCounts[it.videoId] ?: 0).coerceAtLeast(0) }
            .toSortedMap()
        val result = ArrayList<Track>(pool.size)
        for ((_, group) in tiers) {
            result.addAll(group.shuffled(random))
        }
        return result
    }

    private fun primaryArtistKey(track: Track): String? =
        track.artists.firstOrNull()?.name?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            ?: track.artistDisplay.trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }

    fun sortAlbums(albums: List<AlbumSummary>, order: AlbumSortOrder): List<AlbumSummary> = when (order) {
        AlbumSortOrder.TITLE_ASC -> albums.sortedWith(
            compareBy<AlbumSummary> { normalizeSortKey(it.title) }
                .thenBy { normalizeSortKey(it.artistDisplay) }
                .thenBy { it.browseId }
        )
        AlbumSortOrder.TITLE_DESC -> albums.sortedWith(
            compareByDescending<AlbumSummary> { normalizeSortKey(it.title) }
                .thenBy { normalizeSortKey(it.artistDisplay) }
                .thenBy { it.browseId }
        )
        AlbumSortOrder.ARTIST_ASC -> albums.sortedWith(
            compareBy<AlbumSummary> { it.artistDisplay.isBlank() }
                .thenBy { normalizeSortKey(it.artistDisplay) }
                .thenBy { normalizeSortKey(it.title) }
                .thenBy { it.browseId }
        )
        AlbumSortOrder.YEAR_DESC -> albums.sortedWith(
            compareBy<AlbumSummary> { parseAlbumYear(it.year) == null }
                .thenByDescending { parseAlbumYear(it.year) ?: Int.MIN_VALUE }
                .thenBy { normalizeSortKey(it.title) }
                .thenBy { it.browseId }
        )
        AlbumSortOrder.YEAR_ASC -> albums.sortedWith(
            compareBy<AlbumSummary> { parseAlbumYear(it.year) == null }
                .thenBy { parseAlbumYear(it.year) ?: Int.MAX_VALUE }
                .thenBy { normalizeSortKey(it.title) }
                .thenBy { it.browseId }
        )
    }

    fun sortArtists(artists: List<ArtistSummary>, order: ArtistSortOrder): List<ArtistSummary> = when (order) {
        ArtistSortOrder.NAME_ASC -> artists.sortedWith(
            compareBy<ArtistSummary> { normalizeSortKey(it.name) }
                .thenBy { it.browseId }
        )
        ArtistSortOrder.NAME_DESC -> artists.sortedWith(
            compareByDescending<ArtistSummary> { normalizeSortKey(it.name) }
                .thenBy { it.browseId }
        )
        ArtistSortOrder.SUBSCRIBERS_DESC -> artists.sortedWith(
            compareBy<ArtistSummary> { parseSubscriberCount(it.subscriberCount) == null }
                .thenByDescending { parseSubscriberCount(it.subscriberCount) ?: Long.MIN_VALUE }
                .thenBy { normalizeSortKey(it.name) }
                .thenBy { it.browseId }
        )
    }

    fun parseAlbumYear(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = yearRegex.find(raw) ?: return null
        val parsed = match.groupValues[1].toIntOrNull() ?: return null
        return parsed.takeIf { it in 1000..2999 }
    }

    fun parseSubscriberCount(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val match = subscriberRegex.find(raw) ?: return null
        val numberPart = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase(Locale.ROOT)) {
            "K" -> 1_000.0
            "M" -> 1_000_000.0
            "B" -> 1_000_000_000.0
            else -> 1.0
        }
        return (numberPart * multiplier).roundToLong()
    }

    private fun normalizeSortKey(value: String): String = value.trim().lowercase(Locale.ROOT)
}
