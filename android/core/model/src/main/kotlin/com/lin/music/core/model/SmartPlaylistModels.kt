package com.lin.music.core.model

import kotlin.math.abs

data class DuplicateTrackGroup(
    val key: String,
    val reason: String,
    val keptIndex: Int,
    val duplicateIndices: List<Int>,
    val tracks: List<Track>
)

object DuplicateTrackFinder {
    private val parenSuffixRegex = Regex("""\s*[\(\[](?:official|remaster(?:ed)?|live|explicit|clean|audio|video|lyric|hd|hq|bonus)[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE)
    private val nonWordRegex = Regex("""[^a-z0-9]""")

    fun findDuplicates(tracks: List<Track>): List<DuplicateTrackGroup> {
        if (tracks.size < 2) return emptyList()

        val groups = mutableListOf<DuplicateTrackGroup>()
        val claimedIndices = mutableSetOf<Int>()

        // Pass 1: exact videoId matches
        val byVideoId = linkedMapOf<String, MutableList<Int>>()
        tracks.forEachIndexed { index, track ->
            byVideoId.getOrPut(track.videoId) { mutableListOf() }.add(index)
        }
        for ((videoId, indices) in byVideoId) {
            if (indices.size > 1) {
                val kept = indices.first()
                val dupes = indices.drop(1)
                claimedIndices.addAll(dupes)
                groups += DuplicateTrackGroup(
                    key = "id:$videoId",
                    reason = "Exact duplicate",
                    keptIndex = kept,
                    duplicateIndices = dupes,
                    tracks = indices.map { tracks[it] }
                )
            }
        }

        // Pass 2: fuzzy title + artist + near duration (±6s when known)
        val remainingIndices = tracks.indices.filterNot { it in claimedIndices }
        val bySignature = linkedMapOf<String, MutableList<Int>>()
        for (idx in remainingIndices) {
            val sig = normalizeSignature(tracks[idx]) ?: continue
            bySignature.getOrPut(sig) { mutableListOf() }.add(idx)
        }
        for ((sig, indices) in bySignature) {
            if (indices.size < 2) continue
            // Verify duration compatibility against the kept track (if both have durations)
            val kept = indices.first()
            val keptDuration = tracks[kept].durationMs
            val matchingDupes = indices.drop(1).filter { idx ->
                val d = tracks[idx].durationMs
                keptDuration <= 0L || d <= 0L || abs(keptDuration - d) <= 6_000L
            }
            if (matchingDupes.isNotEmpty()) {
                val allInGroup = listOf(kept) + matchingDupes
                groups += DuplicateTrackGroup(
                    key = "fuzzy:$sig",
                    reason = "Same song & artist",
                    keptIndex = kept,
                    duplicateIndices = matchingDupes,
                    tracks = allInGroup.map { tracks[it] }
                )
            }
        }
        return groups
    }

    fun duplicateIndices(tracks: List<Track>): Set<Int> =
        findDuplicates(tracks).flatMap { it.duplicateIndices }.toSet()

    private fun normalizeSignature(track: Track): String? {
        val cleanTitle = parenSuffixRegex.replace(track.title, "")
            .lowercase()
            .let { nonWordRegex.replace(it, "") }
        val cleanArtist = (track.artists.firstOrNull()?.name ?: track.artistDisplay)
            .lowercase()
            .let { nonWordRegex.replace(it, "") }
        if (cleanTitle.isBlank()) return null
        return "$cleanTitle::$cleanArtist"
    }
}
