package com.lin.music.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class LinStorageFormatterTest {

    @Test
    fun `formatStorageBytes renders zero and negative values as 0 mb`() {
        assertEquals("0 mb", formatStorageBytes(0L))
        assertEquals("0 mb", formatStorageBytes(-4096L))
    }

    @Test
    fun `formatStorageBytes renders sub-megabyte values in kb`() {
        assertEquals("1 kb", formatStorageBytes(512L))
        assertEquals("512 kb", formatStorageBytes(512L * 1024L))
    }

    @Test
    fun `formatStorageBytes renders megabyte values in mb`() {
        assertEquals("1 mb", formatStorageBytes(1024L * 1024L))
        assertEquals("245 mb", formatStorageBytes(245L * 1024L * 1024L))
    }

    @Test
    fun `formatStorageBytes renders gigabyte values with two decimals`() {
        assertEquals("1.00 gb", formatStorageBytes(1024L * 1024L * 1024L))
        assertEquals("2.50 gb", formatStorageBytes(2_684_354_560L))
    }
}
