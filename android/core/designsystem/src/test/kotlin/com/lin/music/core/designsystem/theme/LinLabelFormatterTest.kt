package com.lin.music.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class LinLabelFormatterTest {

    @Test
    fun `formatLinLabel returns lowercase input untouched when disabled`() {
        assertEquals("all albums", formatLinLabel("all albums", false))
        assertEquals("+ library", formatLinLabel("+ library", false))
        assertEquals("#chill · mix", formatLinLabel("#chill · mix", false))
    }

    @Test
    fun `formatLinLabel capitalizes first letter of words while preserving tags and prefixes`() {
        assertEquals("All Albums", formatLinLabel("all albums", true))
        assertEquals("+ Library", formatLinLabel("+ library", true))
        assertEquals("Download For Offline", formatLinLabel("download for offline", true))
        assertEquals("#ambient Mix", formatLinLabel("#ambient mix", true))
        assertEquals("LIN · Home", formatLinLabel("LIN · home", true))
    }
}
