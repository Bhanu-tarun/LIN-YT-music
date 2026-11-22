package com.lin.music.core.playback.download

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.EOFException

/**
 * Splits open-ended or large download requests into bounded HTTP byte-range chunks (1 MiB default).
 * YouTube's googlevideo CDN often throttles single open-ended progressive requests near realtime
 * playback bitrate, whereas bounded range chunks transfer at full burst link speed.
 */
@OptIn(UnstableApi::class)
class ChunkedRangeDataSource(
    private val upstream: DataSource,
    private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
    private val onExpiredOrForbidden: ((videoId: String) -> Unit)? = null
) : DataSource {

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
        private val onExpiredOrForbidden: ((videoId: String) -> Unit)? = null
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedRangeDataSource(
            upstream = upstreamFactory.createDataSource(),
            chunkSizeBytes = chunkSizeBytes,
            onExpiredOrForbidden = onExpiredOrForbidden
        )
    }

    private var baseDataSpec: DataSpec? = null
    private var currentUri: Uri? = null
    private var currentPosition: Long = 0L
    private var bytesRemainingInRequest: Long = 0L
    private var chunkBytesRemaining: Long = 0L
    private var chunkBytesRead: Long = 0L
    private var currentChunkRequestedLength: Long = 0L
    private var singleStreamPassThrough: Boolean = false
    private var opened: Boolean = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        baseDataSpec = dataSpec
        currentUri = dataSpec.uri
        currentPosition = dataSpec.position
        chunkBytesRead = 0L

        if (dataSpec.length != C.LENGTH_UNSET.toLong() && dataSpec.length <= chunkSizeBytes) {
            singleStreamPassThrough = true
            val openedBytes = openChunkWithInvalidation(dataSpec)
            opened = true
            bytesRemainingInRequest = openedBytes
            return openedBytes
        }

        val firstChunkLength = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            chunkSizeBytes
        } else {
            minOf(chunkSizeBytes, dataSpec.length)
        }
        val firstChunkSpec = dataSpec.buildUpon()
            .setPosition(currentPosition)
            .setLength(firstChunkLength)
            .build()

        val chunkOpenedBytes = openChunkWithInvalidation(firstChunkSpec)
        opened = true
        currentUri = upstream.uri ?: dataSpec.uri
        val headers = upstream.responseHeaders

        if (!hasContentRangeHeader(headers)) {
            // Origin ignored Range and returned 200 OK full stream; read directly without re-opening.
            singleStreamPassThrough = true
            bytesRemainingInRequest = chunkOpenedBytes
            return chunkOpenedBytes
        }

        singleStreamPassThrough = false
        currentChunkRequestedLength = firstChunkLength
        chunkBytesRemaining = chunkOpenedBytes

        val totalEntityLength = parseContentRangeTotalLength(headers)
        if (totalEntityLength != null) {
            val availableFromPos = (totalEntityLength - dataSpec.position).coerceAtLeast(0L)
            bytesRemainingInRequest = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                availableFromPos
            } else {
                minOf(dataSpec.length, availableFromPos)
            }
            return bytesRemainingInRequest
        }

        bytesRemainingInRequest = dataSpec.length
        return bytesRemainingInRequest
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (singleStreamPassThrough) {
            return upstream.read(buffer, offset, length)
        }

        while (true) {
            if (bytesRemainingInRequest == 0L) {
                return C.RESULT_END_OF_INPUT
            }
            if (chunkBytesRemaining == 0L) {
                if (!openNextChunk()) {
                    return C.RESULT_END_OF_INPUT
                }
            }

            val maxToRead = if (chunkBytesRemaining != C.LENGTH_UNSET.toLong()) {
                minOf(length.toLong(), chunkBytesRemaining).toInt()
            } else {
                length
            }

            val read = upstream.read(buffer, offset, maxToRead)
            if (read == C.RESULT_END_OF_INPUT) {
                if (bytesRemainingInRequest == 0L) {
                    return C.RESULT_END_OF_INPUT
                }
                if (bytesRemainingInRequest == C.LENGTH_UNSET.toLong() && chunkBytesRead < currentChunkRequestedLength) {
                    return C.RESULT_END_OF_INPUT
                }
                if (chunkBytesRead == 0L) {
                    if (bytesRemainingInRequest > 0L) {
                        throw EOFException("Unexpected EOF at $currentPosition with $bytesRemainingInRequest bytes remaining")
                    }
                    return C.RESULT_END_OF_INPUT
                }
                if (!openNextChunk()) {
                    return C.RESULT_END_OF_INPUT
                }
                continue
            }

            if (read > 0) {
                currentPosition += read
                chunkBytesRead += read
                if (chunkBytesRemaining != C.LENGTH_UNSET.toLong()) {
                    chunkBytesRemaining = (chunkBytesRemaining - read).coerceAtLeast(0L)
                }
                if (bytesRemainingInRequest != C.LENGTH_UNSET.toLong()) {
                    bytesRemainingInRequest = (bytesRemainingInRequest - read).coerceAtLeast(0L)
                }
                return read
            }
            return 0
        }
    }

    private fun openNextChunk(): Boolean {
        closeUpstreamQuietly()
        val spec = baseDataSpec ?: return false
        if (bytesRemainingInRequest == 0L) return false

        val nextLength = if (bytesRemainingInRequest == C.LENGTH_UNSET.toLong()) {
            chunkSizeBytes
        } else {
            minOf(chunkSizeBytes, bytesRemainingInRequest)
        }
        val nextSpec = spec.buildUpon()
            .setPosition(currentPosition)
            .setLength(nextLength)
            .build()

        val openedBytes = try {
            openChunkWithInvalidation(nextSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 416 && bytesRemainingInRequest == C.LENGTH_UNSET.toLong()) {
                closeUpstreamQuietly()
                return false
            }
            throw e
        }

        opened = true
        currentUri = upstream.uri ?: spec.uri
        chunkBytesRead = 0L
        currentChunkRequestedLength = nextLength
        chunkBytesRemaining = openedBytes
        return true
    }

    private fun openChunkWithInvalidation(spec: DataSpec): Long {
        return try {
            upstream.open(spec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 403 || e.responseCode == 410) {
                baseDataSpec?.key?.takeIf { it.isNotBlank() }?.let { onExpiredOrForbidden?.invoke(it) }
            }
            throw e
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        baseDataSpec = null
        currentUri = null
        singleStreamPassThrough = false
        bytesRemainingInRequest = 0L
        chunkBytesRemaining = 0L
        chunkBytesRead = 0L
        currentChunkRequestedLength = 0L
        closeUpstreamQuietly()
    }

    private fun closeUpstreamQuietly() {
        if (!opened) return
        try {
            upstream.close()
        } finally {
            opened = false
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE_BYTES = 1024L * 1024L

        fun hasContentRangeHeader(headers: Map<String, List<String>>): Boolean =
            findHeaderValue(headers, "Content-Range") != null

        fun parseContentRangeTotalLength(headers: Map<String, List<String>>): Long? {
            val raw = findHeaderValue(headers, "Content-Range") ?: return null
            val slashIdx = raw.lastIndexOf('/')
            if (slashIdx < 0 || slashIdx >= raw.length - 1) return null
            val totalPart = raw.substring(slashIdx + 1).trim()
            if (totalPart == "*") return null
            return totalPart.toLongOrNull()?.takeIf { it >= 0L }
        }

        private fun findHeaderValue(headers: Map<String, List<String>>, targetName: String): String? {
            headers.entries.forEach { (key, values) ->
                if (key.equals(targetName, ignoreCase = true)) {
                    return values.firstOrNull { !it.isNullOrBlank() }?.trim()
                }
            }
            return null
        }
    }
}
