package com.lin.music.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parse handles centiseconds milliseconds and multi-timestamps`() {
        val raw = """
            [ti:Test Song]
            [00:10.50]First line
            [01:02.125][02:00.00]Chorus line
            [00:05.2]Intro
        """.trimIndent()

        val parsed = LrcParser.parse(raw)
        assertEquals(4, parsed.size)
        assertEquals(5_200L, parsed[0].timeMs)
        assertEquals("Intro", parsed[0].text)
        assertEquals(10_500L, parsed[1].timeMs)
        assertEquals("First line", parsed[1].text)
        assertEquals(62_125L, parsed[2].timeMs)
        assertEquals("Chorus line", parsed[2].text)
        assertEquals(120_000L, parsed[3].timeMs)
        assertEquals("Chorus line", parsed[3].text)

        assertEquals(-1, LrcParser.findActiveLineIndex(parsed, 1_000L))
        assertEquals(0, LrcParser.findActiveLineIndex(parsed, 5_200L))
        assertEquals(1, LrcParser.findActiveLineIndex(parsed, 15_000L))
        assertEquals(3, LrcParser.findActiveLineIndex(parsed, 180_000L))
    }
}
