package com.lin.music.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinScrollbarTest {

    @Test
    fun `calculateScrollbarMetrics computes start, mid with partial offset, and end positions`() {
        val start = calculateScrollbarMetrics(
            totalItemsCount = 100,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
            visibleItemsCount = 10,
            firstVisibleItemSize = 100,
            trackHeightPx = 1000f,
            minThumbHeightPx = 50f
        )
        assertEquals(100f, start.thumbHeightPx, 0.01f)
        assertEquals(0f, start.thumbOffsetPx, 0.01f)
        assertEquals(0f, start.scrollFraction, 0.001f)

        val mid = calculateScrollbarMetrics(
            totalItemsCount = 100,
            firstVisibleItemIndex = 44,
            firstVisibleItemScrollOffset = 100,
            visibleItemsCount = 10,
            firstVisibleItemSize = 100,
            trackHeightPx = 1000f,
            minThumbHeightPx = 50f
        )
        assertEquals(100f, mid.thumbHeightPx, 0.01f)
        assertEquals(0.5f, mid.scrollFraction, 0.001f)
        assertEquals(450f, mid.thumbOffsetPx, 0.01f)

        val end = calculateScrollbarMetrics(
            totalItemsCount = 100,
            firstVisibleItemIndex = 90,
            firstVisibleItemScrollOffset = 0,
            visibleItemsCount = 10,
            firstVisibleItemSize = 100,
            trackHeightPx = 1000f,
            minThumbHeightPx = 50f
        )
        assertEquals(1f, end.scrollFraction, 0.001f)
        assertEquals(900f, end.thumbOffsetPx, 0.01f)
    }

    @Test
    fun `calculateScrollbarMetrics enforces minimum thumb height on 5000 item playlists`() {
        val large = calculateScrollbarMetrics(
            totalItemsCount = 5000,
            firstVisibleItemIndex = 1200,
            firstVisibleItemScrollOffset = 0,
            visibleItemsCount = 12,
            firstVisibleItemSize = 120,
            trackHeightPx = 1000f,
            minThumbHeightPx = 64f
        )
        assertEquals(64f, large.thumbHeightPx, 0.01f)
        assertTrue(large.thumbOffsetPx > 0f && large.thumbOffsetPx < 1000f - 64f)
    }

    @Test
    fun `scrollbarTargetIndex maps drag Y and clamps out-of-bounds input`() {
        assertEquals(0, scrollbarTargetIndex(touchYPx = -50f, trackHeightPx = 1000f, totalItemsCount = 5000))
        assertEquals(0, scrollbarTargetIndex(touchYPx = 0f, trackHeightPx = 1000f, totalItemsCount = 5000))
        assertEquals(2500, scrollbarTargetIndex(touchYPx = 500f, trackHeightPx = 1000f, totalItemsCount = 5000))
        assertEquals(4999, scrollbarTargetIndex(touchYPx = 1000f, trackHeightPx = 1000f, totalItemsCount = 5000))
        assertEquals(4999, scrollbarTargetIndex(touchYPx = 1400f, trackHeightPx = 1000f, totalItemsCount = 5000))
        assertEquals(0, scrollbarTargetIndex(touchYPx = 500f, trackHeightPx = 0f, totalItemsCount = 5000))
        assertEquals(0, scrollbarTargetIndex(touchYPx = 500f, trackHeightPx = 1000f, totalItemsCount = 1))
    }
}
