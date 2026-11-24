package com.lin.music.core.playback.queue

import com.lin.music.core.model.LibrarySorter
import com.lin.music.core.model.ShuffleMode
import com.lin.music.core.model.Track
import kotlin.random.Random

object SmartShuffleEngine {

    fun shuffle(
        tracks: List<Track>,
        anchorIndex: Int? = null,
        mode: ShuffleMode = ShuffleMode.TRUE_RANDOM,
        playCounts: Map<String, Int> = emptyMap(),
        random: Random = Random.Default
    ): List<Track> = LibrarySorter.shuffleTracks(
        tracks = tracks,
        anchorIndex = anchorIndex,
        mode = mode,
        playCounts = playCounts,
        random = random
    )
}
