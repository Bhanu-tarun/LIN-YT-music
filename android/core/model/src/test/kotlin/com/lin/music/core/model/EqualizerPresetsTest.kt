package com.lin.music.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPresetsTest {

    @Test
    fun `parses Wavelet GraphicEQ string onto 10 ISO bands`() {
        val sample = "GraphicEQ: 20 -3.0; 100 2.0; 1000 -1.0; 4000 4.0; 16000 18.0"
        val profile = AutoEqParser.parse(sample, name = "Sony XM5")
        assertNotNull(profile)
        val parsed = profile!!
        assertEquals("Sony XM5", parsed.name)
        assertEquals(10, parsed.bandGainsDb.size)
        // Band 10 (index 9, 16kHz) must clamp at +12dB max
        assertEquals(12.0f, parsed.bandGainsDb[9], 0.01f)
        // Peak +12dB yields negative preamp to prevent digital clipping
        assertTrue(parsed.preampDb < 0f)
    }

    @Test
    fun `parses EqualizerAPO parametric filters onto 10 bands`() {
        val apo = """
            Preamp: -4.2 dB
            Filter 1: ON PK Fc 125 Hz Gain 5.0 dB Q 1.41
            Filter 2: ON LS Fc 80 Hz Gain 3.0 dB Q 0.71
            Filter 3: ON PK Fc 4000 Hz Gain -4.0 dB Q 2.00
        """.trimIndent()
        val parsed = AutoEqParser.parse(apo, name = "HD600 Harman")
        assertNotNull(parsed)
        val p = parsed!!
        assertEquals(-4.2f, p.preampDb, 0.01f)
        assertEquals(10, p.bandGainsDb.size)
        // 125 Hz boost (index 2) positive, 4 kHz cut (index 7) negative
        assertTrue(p.bandGainsDb[2] > 3.0f)
        assertTrue(p.bandGainsDb[7] < -2.0f)
    }

    @Test
    fun `returns null on unrecognized text and resolves fallback to Flat`() {
        assertNull(AutoEqParser.parse("hello world nothing here"))
        assertNull(AutoEqParser.parse("   "))
        assertEquals("flat", BuiltInEqPresets.resolve("missing_id", emptyList()).id)
    }
}
