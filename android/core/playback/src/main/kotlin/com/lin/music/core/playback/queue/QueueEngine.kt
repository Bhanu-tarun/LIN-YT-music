package com.lin.music.core.playback.queue

import com.lin.music.core.model.RepeatMode
import com.lin.music.core.model.Track

data class QueueSnapshot(
    val items: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val sourceLabel: String = "queue",
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
) {
    val currentTrack: Track?
        get() = items.getOrNull(currentIndex)

    fun nextTrackIndexOrNull(): Int? {
        if (shuffleEnabled || items.isEmpty()) return null
        val next = currentIndex + 1
        return when {
            next in items.indices -> next
            repeatMode == RepeatMode.ALL -> 0
            else -> null
        }
    }

    fun previousTrackIndexOrNull(): Int? {
        if (shuffleEnabled || items.isEmpty()) return null
        val prev = currentIndex - 1
        return when {
            prev in items.indices -> prev
            repeatMode == RepeatMode.ALL -> items.lastIndex
            else -> null
        }
    }
}

data class QueueUndoToken(
    val previousItems: List<Track>,
    val previousIndex: Int,
    val description: String
)

/**
 * Pure deterministic queue state machine implementing Play Next, Add to Queue,
 * undo, drag reordering, removal, and track navigation.
 */
class QueueEngine(initial: QueueSnapshot = QueueSnapshot()) {

    var snapshot: QueueSnapshot = initial
        private set

    fun replaceWithContext(
        tracks: List<Track>,
        startIndex: Int,
        sourceLabel: String
    ): QueueSnapshot {
        val safeIdx = if (tracks.isEmpty()) -1 else startIndex.coerceIn(0, tracks.lastIndex)
        snapshot = snapshot.copy(
            items = tracks,
            currentIndex = safeIdx,
            sourceLabel = sourceLabel
        )
        return snapshot
    }

    fun playNext(track: Track): Pair<QueueSnapshot, QueueUndoToken> {
        val undo = QueueUndoToken(
            previousItems = snapshot.items,
            previousIndex = snapshot.currentIndex,
            description = "queued next — ${track.title}"
        )
        val mutable = snapshot.items.toMutableList()
        if (mutable.isEmpty() || snapshot.currentIndex < 0) {
            mutable.add(track)
            snapshot = snapshot.copy(items = mutable, currentIndex = 0)
        } else {
            val insertIdx = (snapshot.currentIndex + 1).coerceAtMost(mutable.size)
            mutable.add(insertIdx, track)
            snapshot = snapshot.copy(items = mutable)
        }
        return snapshot to undo
    }

    fun playNextAll(tracks: List<Track>, label: String): Pair<QueueSnapshot, QueueUndoToken> {
        val undo = QueueUndoToken(
            previousItems = snapshot.items,
            previousIndex = snapshot.currentIndex,
            description = "queued next — $label"
        )
        if (tracks.isEmpty()) return snapshot to undo
        val mutable = snapshot.items.toMutableList()
        if (mutable.isEmpty() || snapshot.currentIndex < 0) {
            mutable.addAll(tracks)
            snapshot = snapshot.copy(items = mutable, currentIndex = 0)
        } else {
            val insertIdx = (snapshot.currentIndex + 1).coerceAtMost(mutable.size)
            mutable.addAll(insertIdx, tracks)
            snapshot = snapshot.copy(items = mutable)
        }
        return snapshot to undo
    }

    fun addToQueue(track: Track): Pair<QueueSnapshot, QueueUndoToken> {
        val undo = QueueUndoToken(
            previousItems = snapshot.items,
            previousIndex = snapshot.currentIndex,
            description = "added to queue — ${track.title}"
        )
        val mutable = snapshot.items.toMutableList()
        mutable.add(track)
        val newIdx = if (snapshot.currentIndex < 0) 0 else snapshot.currentIndex
        snapshot = snapshot.copy(items = mutable, currentIndex = newIdx)
        return snapshot to undo
    }

    fun addAllToQueue(tracks: List<Track>, label: String): Pair<QueueSnapshot, QueueUndoToken> {
        val undo = QueueUndoToken(
            previousItems = snapshot.items,
            previousIndex = snapshot.currentIndex,
            description = "added to queue — $label"
        )
        if (tracks.isEmpty()) return snapshot to undo
        val mutable = snapshot.items.toMutableList()
        mutable.addAll(tracks)
        val newIdx = if (snapshot.currentIndex < 0) 0 else snapshot.currentIndex
        snapshot = snapshot.copy(items = mutable, currentIndex = newIdx)
        return snapshot to undo
    }

    fun removeAt(index: Int): Pair<QueueSnapshot, QueueUndoToken?> {
        if (index !in snapshot.items.indices) return snapshot to null
        val removed = snapshot.items[index]
        val undo = QueueUndoToken(
            previousItems = snapshot.items,
            previousIndex = snapshot.currentIndex,
            description = "removed — ${removed.title}"
        )
        val mutable = snapshot.items.toMutableList()
        mutable.removeAt(index)
        val newIdx = when {
            mutable.isEmpty() -> -1
            index < snapshot.currentIndex -> snapshot.currentIndex - 1
            index == snapshot.currentIndex -> snapshot.currentIndex.coerceAtMost(mutable.lastIndex)
            else -> snapshot.currentIndex
        }
        snapshot = snapshot.copy(items = mutable, currentIndex = newIdx)
        return snapshot to undo
    }

    fun move(fromIndex: Int, toIndex: Int): QueueSnapshot {
        if (fromIndex !in snapshot.items.indices || toIndex !in snapshot.items.indices || fromIndex == toIndex) {
            return snapshot
        }
        val mutable = snapshot.items.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        val cur = snapshot.currentIndex
        val newCur = when {
            cur == fromIndex -> toIndex
            fromIndex < cur && toIndex >= cur -> cur - 1
            fromIndex > cur && toIndex <= cur -> cur + 1
            else -> cur
        }
        snapshot = snapshot.copy(items = mutable, currentIndex = newCur)
        return snapshot
    }

    fun clearUpcoming(): Pair<QueueSnapshot, QueueUndoToken> {
        val undo = QueueUndoToken(
            previousItems = snapshot.items,
            previousIndex = snapshot.currentIndex,
            description = "cleared upcoming queue"
        )
        val cur = snapshot.currentTrack
        snapshot = if (cur != null) {
            snapshot.copy(items = listOf(cur), currentIndex = 0)
        } else {
            snapshot.copy(items = emptyList(), currentIndex = -1)
        }
        return snapshot to undo
    }

    fun restore(token: QueueUndoToken): QueueSnapshot {
        val safeIdx = if (token.previousItems.isEmpty()) -1 else token.previousIndex.coerceIn(0, token.previousItems.lastIndex)
        snapshot = snapshot.copy(items = token.previousItems, currentIndex = safeIdx)
        return snapshot
    }

    fun setCurrentIndex(index: Int): QueueSnapshot {
        if (index in snapshot.items.indices) {
            snapshot = snapshot.copy(currentIndex = index)
        }
        return snapshot
    }

    fun setRepeatMode(mode: RepeatMode): QueueSnapshot {
        snapshot = snapshot.copy(repeatMode = mode)
        return snapshot
    }

    fun setShuffleEnabled(enabled: Boolean): QueueSnapshot {
        snapshot = snapshot.copy(shuffleEnabled = enabled)
        return snapshot
    }
}
