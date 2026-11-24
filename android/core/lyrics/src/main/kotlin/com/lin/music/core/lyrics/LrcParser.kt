package com.lin.music.core.lyrics

import com.lin.music.core.model.LyricLine

object LrcParser {

    private val timestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()

        lrcContent.lineSequence().forEach { rawLine ->
            val matches = timestampRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val text = rawLine.replace(timestampRegex, "").trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fracRaw = match.groupValues[3]
                val millis = when (fracRaw.length) {
                    0 -> 0L
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                    else -> fracRaw.take(3).toLongOrNull() ?: 0L
                }
                val totalMs = minutes * 60_000L + seconds * 1000L + millis
                lines += LyricLine(timeMs = totalMs, text = text)
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    fun findActiveLineIndex(
        lines: List<LyricLine>,
        positionMs: Long,
        offsetMs: Long = 0L
    ): Int {
        if (lines.isEmpty()) return -1
        val adjusted = (positionMs + offsetMs).coerceAtLeast(0L)
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= adjusted) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
