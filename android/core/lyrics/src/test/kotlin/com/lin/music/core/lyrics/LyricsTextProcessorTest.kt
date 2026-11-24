package com.lin.music.core.lyrics

import com.lin.music.core.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTextProcessorTest {

    @Test
    fun `romanizes Hangul, Kana, Cyrillic, Greek, and Devanagari offline`() {
        assertEquals("saranghae", LyricsTextProcessor.romanize("사랑해"))
        assertEquals("sakura motto kya", LyricsTextProcessor.romanize("さくら もっと きゃ"))
        assertEquals("Privet mir", LyricsTextProcessor.romanize("Привет мир"))
        assertEquals("alfa", LyricsTextProcessor.romanize("άλφα"))
        assertEquals("namaste", LyricsTextProcessor.romanize("नमस्ते"))
        assertNull(LyricsTextProcessor.romanizeIfSupported("Pure Latin English Text"))
    }

    @Test
    fun `findActiveLineIndex applies positive and negative sync offsets`() {
        val lines = listOf(
            LyricLine(timeMs = 5_000L, text = "Line 1"),
            LyricLine(timeMs = 10_000L, text = "Line 2"),
            LyricLine(timeMs = 15_000L, text = "Line 3")
        )
        // Baseline at 9_600ms -> Line 1 (idx 0)
        assertEquals(0, LrcParser.findActiveLineIndex(lines, positionMs = 9_600L, offsetMs = 0L))
        // +500ms advance moves lookup to 10_100ms -> Line 2 (idx 1)
        assertEquals(1, LrcParser.findActiveLineIndex(lines, positionMs = 9_600L, offsetMs = 500L))
        // -500ms delay at 10_200ms moves lookup to 9_700ms -> Line 1 (idx 0)
        assertEquals(0, LrcParser.findActiveLineIndex(lines, positionMs = 10_200L, offsetMs = -500L))
    }
}
