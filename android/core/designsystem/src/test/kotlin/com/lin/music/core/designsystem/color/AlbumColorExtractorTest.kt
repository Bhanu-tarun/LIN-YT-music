package com.lin.music.core.designsystem.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumColorExtractorTest {

    @Test
    fun `extractFromPixels picks 4 saturated colors and boosts very dark brightest pixel`() {
        // Dark image with red/blue tints
        val pixels = IntArray(400) { idx ->
            when {
                idx % 3 == 0 -> 0xFF301010.toInt()
                idx % 3 == 1 -> 0xFF103010.toInt()
                else -> 0xFF101030.toInt()
            }
        }
        val result = AlbumColorExtractor.extractFromPixels(pixels)
        assertEquals(4, result.colors.size)
        val r = (result.brightestAccent ushr 16) and 0xFF
        val g = (result.brightestAccent ushr 8) and 0xFF
        val b = result.brightestAccent and 0xFF
        // Boosted brightness should far exceed original max channel (0x30 = 48)
        assertTrue("Expected boosted brightest color, got ($r,$g,$b)", maxOf(r, g, b) > 150)
    }

    @Test
    fun `extractFromPixels handles empty input gracefully`() {
        val result = AlbumColorExtractor.extractFromPixels(IntArray(0))
        assertEquals(4, result.colors.size)
    }

    @Test
    fun `extractFromPixels avoids pure white accent and derives vivid blue accent from album cover`() {
        val pixels = IntArray(400) { idx ->
            if (idx % 5 == 0) 0xFF1A5CFF.toInt() else 0xFFFFFFFF.toInt()
        }
        val result = AlbumColorExtractor.extractFromPixels(pixels)
        assertEquals(4, result.colors.size)
        val r = (result.brightestAccent ushr 16) and 0xFF
        val b = result.brightestAccent and 0xFF
        assertTrue("Expected vivid blue non-white accent, got r=$r b=$b", b - r > 60)
    }
}
