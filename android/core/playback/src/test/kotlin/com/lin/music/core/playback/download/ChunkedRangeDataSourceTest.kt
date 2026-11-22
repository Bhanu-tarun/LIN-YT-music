package com.lin.music.core.playback.download

import android.net.StubUri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(UnstableApi::class)
class ChunkedRangeDataSourceTest {

    private val testUri = StubUri.create()

    private class FakeRangeDataSource(
        private val payload: ByteArray,
        private val supportRange: Boolean = true,
        private val includeTotalLengthInContentRange: Boolean = true,
        private val forceResponseCode: Int? = null,
        private val recordedRequests: MutableList<Pair<Long, Long>> = mutableListOf()
    ) : DataSource {
        private var currentSpec: DataSpec? = null
        private var readPos = 0
        private var remaining = 0
        private var headers: Map<String, List<String>> = emptyMap()

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            recordedRequests += dataSpec.position to dataSpec.length
            forceResponseCode?.let { code ->
                throw HttpDataSource.InvalidResponseCodeException(
                    code,
                    "Forced error",
                    null,
                    emptyMap(),
                    dataSpec,
                    ByteArray(0)
                )
            }

            currentSpec = dataSpec
            val start = dataSpec.position.toInt()
            if (start >= payload.size) {
                throw HttpDataSource.InvalidResponseCodeException(
                    416,
                    "Range Not Satisfiable",
                    null,
                    mapOf("Content-Range" to listOf("bytes */${payload.size}")),
                    dataSpec,
                    ByteArray(0)
                )
            }

            readPos = start
            if (!supportRange) {
                remaining = payload.size - start
                headers = mapOf("Content-Length" to listOf(remaining.toString()))
                return remaining.toLong()
            }

            val requestedLen = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                payload.size - start
            } else {
                minOf(dataSpec.length.toInt(), payload.size - start)
            }
            remaining = requestedLen
            val end = start + requestedLen - 1
            val totalPart = if (includeTotalLengthInContentRange) payload.size.toString() else "*"
            headers = mapOf("Content-Range" to listOf("bytes $start-$end/$totalPart"))
            return requestedLen.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return C.RESULT_END_OF_INPUT
            val toCopy = minOf(length, remaining)
            System.arraycopy(payload, readPos, buffer, offset, toCopy)
            readPos += toCopy
            remaining -= toCopy
            return toCopy
        }

        override fun getUri() = currentSpec?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = headers

        override fun close() {
            currentSpec = null
            headers = emptyMap()
        }
    }

    private fun readAll(ds: DataSource, spec: DataSpec): Pair<Long, ByteArray> {
        val openedLen = ds.open(spec)
        val out = ArrayList<Byte>()
        val buf = ByteArray(8192)
        try {
            while (true) {
                val n = ds.read(buf, 0, buf.size)
                if (n == C.RESULT_END_OF_INPUT) break
                for (i in 0 until n) out += buf[i]
            }
        } finally {
            ds.close()
        }
        return openedLen to out.toByteArray()
    }

    @Test
    fun `splits open-ended download into bounded chunks and preserves full entity length`() {
        val totalSize = 3_460_300 // ~3.3 MiB
        val payload = ByteArray(totalSize) { (it * 31 and 0xFF).toByte() }
        val requests = mutableListOf<Pair<Long, Long>>()
        val ds = ChunkedRangeDataSource(
            upstream = FakeRangeDataSource(payload, recordedRequests = requests),
            chunkSizeBytes = 1024L * 1024L
        )

        val spec = DataSpec.Builder()
            .setUri(testUri)
            .setKey("vid_1")
            .setPosition(0L)
            .setLength(C.LENGTH_UNSET.toLong())
            .build()

        val (openedLength, actual) = readAll(ds, spec)
        assertEquals(totalSize.toLong(), openedLength)
        assertArrayEquals(payload, actual)
        assertEquals(
            listOf(
                0L to 1_048_576L,
                1_048_576L to 1_048_576L,
                2_097_152L to 1_048_576L,
                3_145_728L to 314_572L
            ),
            requests
        )
    }

    @Test
    fun `resumes from non-zero offset with exact remaining bytes`() {
        val totalSize = 3_000_000
        val offset = 1_500_000L
        val payload = ByteArray(totalSize) { (it and 0xFF).toByte() }
        val requests = mutableListOf<Pair<Long, Long>>()
        val ds = ChunkedRangeDataSource(
            upstream = FakeRangeDataSource(payload, recordedRequests = requests),
            chunkSizeBytes = 1024L * 1024L
        )

        val spec = DataSpec.Builder()
            .setUri(testUri)
            .setKey("vid_resume")
            .setPosition(offset)
            .setLength(C.LENGTH_UNSET.toLong())
            .build()

        val (openedLength, actual) = readAll(ds, spec)
        assertEquals(totalSize - offset, openedLength)
        assertArrayEquals(payload.copyOfRange(offset.toInt(), totalSize), actual)
        assertEquals(
            listOf(
                1_500_000L to 1_048_576L,
                2_548_576L to 451_424L
            ),
            requests
        )
    }

    @Test
    fun `passes through single stream when server ignores Range header`() {
        val payload = ByteArray(2_200_000) { (it * 13 and 0xFF).toByte() }
        val requests = mutableListOf<Pair<Long, Long>>()
        val ds = ChunkedRangeDataSource(
            upstream = FakeRangeDataSource(payload, supportRange = false, recordedRequests = requests),
            chunkSizeBytes = 1024L * 1024L
        )

        val spec = DataSpec.Builder()
            .setUri(testUri)
            .setKey("vid_200")
            .setPosition(0L)
            .setLength(C.LENGTH_UNSET.toLong())
            .build()

        val (openedLength, actual) = readAll(ds, spec)
        assertEquals(payload.size.toLong(), openedLength)
        assertArrayEquals(payload, actual)
        assertEquals(1, requests.size)
    }

    @Test
    fun `handles unknown total entity length cleanly across chunks`() {
        // Exact multiple of chunk size to exercise trailing HTTP 416 path, plus remainder case.
        val exactMultiple = 2 * 1024 * 1024
        val payload1 = ByteArray(exactMultiple) { (it * 7 and 0xFF).toByte() }
        val ds1 = ChunkedRangeDataSource(
            upstream = FakeRangeDataSource(payload1, includeTotalLengthInContentRange = false),
            chunkSizeBytes = 1024L * 1024L
        )
        val (len1, actual1) = readAll(
            ds1,
            DataSpec.Builder().setUri(testUri).setLength(C.LENGTH_UNSET.toLong()).build()
        )
        assertEquals(C.LENGTH_UNSET.toLong(), len1)
        assertArrayEquals(payload1, actual1)

        val partialSize = 2_400_000
        val payload2 = ByteArray(partialSize) { (it * 11 and 0xFF).toByte() }
        val ds2 = ChunkedRangeDataSource(
            upstream = FakeRangeDataSource(payload2, includeTotalLengthInContentRange = false),
            chunkSizeBytes = 1024L * 1024L
        )
        val (_, actual2) = readAll(
            ds2,
            DataSpec.Builder().setUri(testUri).setLength(C.LENGTH_UNSET.toLong()).build()
        )
        assertArrayEquals(payload2, actual2)
    }

    @Test
    fun `invokes expiration callback on HTTP 403 and rethrows`() {
        val invalidated = AtomicInteger(0)
        var capturedKey: String? = null
        val ds = ChunkedRangeDataSource(
            upstream = FakeRangeDataSource(ByteArray(100), forceResponseCode = 403),
            onExpiredOrForbidden = { key ->
                invalidated.incrementAndGet()
                capturedKey = key
            }
        )

        val spec = DataSpec.Builder()
            .setUri(testUri)
            .setKey("expired_vid")
            .build()

        try {
            ds.open(spec)
            fail("Expected InvalidResponseCodeException")
        } catch (e: IOException) {
            assertTrue(e is HttpDataSource.InvalidResponseCodeException)
            assertEquals(1, invalidated.get())
            assertEquals("expired_vid", capturedKey)
        }
    }

    @Test
    fun `parseContentRangeTotalLength handles casing wildcard and invalid headers`() {
        assertEquals(
            3_460_300L,
            ChunkedRangeDataSource.parseContentRangeTotalLength(
                mapOf("content-range" to listOf("bytes 0-1048575/3460300"))
            )
        )
        assertNull(
            ChunkedRangeDataSource.parseContentRangeTotalLength(
                mapOf("Content-Range" to listOf("bytes 0-1048575/*"))
            )
        )
        assertNull(
            ChunkedRangeDataSource.parseContentRangeTotalLength(
                mapOf("Content-Length" to listOf("1024"))
            )
        )
    }
}
