package com.lin.music.core.innertube.client

import com.lin.music.core.model.ContentRegions
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeClientRegionTest {

    @Test
    fun `normalize cleans valid codes and falls back to US`() {
        assertEquals("JP", ContentRegions.normalize("  jp "))
        assertEquals("IN", ContentRegions.normalize("IN"))
        assertEquals("US", ContentRegions.normalize(""))
        assertEquals("US", ContentRegions.normalize("123"))
        assertEquals("US", ContentRegions.normalize(null))
    }

    @Test
    fun `allCountries includes worldwide ISO codes`() {
        val codes = ContentRegions.allCountries().map { it.code }.toSet()
        assertTrue(setOf("US", "IN", "JP", "GB", "BR", "NO").all { it in codes })
    }

    @Test
    fun `buildContextJson applies requested gl while preserving hl en`() {
        val webCtx = YouTubeClient.WEB_REMIX.buildContextJson(regionCode = "jp")["client"]!!.jsonObject
        assertEquals("en", webCtx["hl"]!!.jsonPrimitive.content)
        assertEquals("JP", webCtx["gl"]!!.jsonPrimitive.content)

        val visionCtx = YouTubeClient.VISIONOS.buildContextJson(regionCode = "GB")["client"]!!.jsonObject
        assertEquals("en", visionCtx["hl"]!!.jsonPrimitive.content)
        assertEquals("GB", visionCtx["gl"]!!.jsonPrimitive.content)
    }
}
