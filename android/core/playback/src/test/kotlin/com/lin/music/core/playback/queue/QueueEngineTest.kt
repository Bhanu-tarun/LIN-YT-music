package com.lin.music.core.playback.queue

import com.lin.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QueueEngineTest {

    private fun track(id: String) = Track(videoId = id, title = "Track $id")

    @Test
    fun `playNext inserts immediately after current track and undo restores exact order`() {
        val engine = QueueEngine()
        engine.replaceWithContext(listOf(track("1"), track("2"), track("3")), startIndex = 0, sourceLabel = "album")

        val (afterNext, undoToken) = engine.playNext(track("X"))
        assertEquals(listOf("1", "X", "2", "3"), afterNext.items.map { it.videoId })
        assertEquals(0, afterNext.currentIndex)

        // Second consecutive Play Next inserts right after current (before X)
        val (afterSecondNext, _) = engine.playNext(track("Y"))
        assertEquals(listOf("1", "Y", "X", "2", "3"), afterSecondNext.items.map { it.videoId })

        val restored = engine.restore(undoToken)
        assertEquals(listOf("1", "2", "3"), restored.items.map { it.videoId })
        assertEquals(0, restored.currentIndex)
    }

    @Test
    fun `addToQueue appends to end and undo restores`() {
        val engine = QueueEngine()
        engine.replaceWithContext(listOf(track("1"), track("2")), startIndex = 1, sourceLabel = "playlist")

        val (afterAdd, undo) = engine.addToQueue(track("Z"))
        assertEquals(listOf("1", "2", "Z"), afterAdd.items.map { it.videoId })
        assertEquals(1, afterAdd.currentIndex)

        val restored = engine.restore(undo)
        assertEquals(listOf("1", "2"), restored.items.map { it.videoId })
    }

    @Test
    fun `playNext and addToQueue work when queue starts empty`() {
        val engine = QueueEngine()
        val (snap, _) = engine.playNext(track("A"))
        assertEquals(listOf("A"), snap.items.map { it.videoId })
        assertEquals(0, snap.currentIndex)
    }

    @Test
    fun `move and removeAt keep currentIndex tracking active track`() {
        val engine = QueueEngine()
        engine.replaceWithContext(listOf(track("1"), track("2"), track("3"), track("4")), startIndex = 1, sourceLabel = "mix")

        val moved = engine.move(fromIndex = 3, toIndex = 0)
        assertEquals(listOf("4", "1", "2", "3"), moved.items.map { it.videoId })
        assertEquals(2, moved.currentIndex)
        assertEquals("2", moved.currentTrack?.videoId)

        val (removed, undo) = engine.removeAt(0)
        assertNotNull(undo)
        assertEquals(listOf("1", "2", "3"), removed.items.map { it.videoId })
        assertEquals(1, removed.currentIndex)
        assertEquals("2", removed.currentTrack?.videoId)
    }
}
