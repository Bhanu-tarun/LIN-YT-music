package com.lin.music.core.model

import java.util.Calendar
import java.util.TimeZone

enum class StatsRange(val label: String, val days: Int?) {
    TODAY("Today", 1),
    SEVEN_DAYS("7 Days", 7),
    THIRTY_DAYS("30 Days", 30),
    ALL_TIME("All Time", null)
}

data class PlaySessionRecord(
    val videoId: String,
    val listenedMs: Long,
    val playedAtMs: Long
)

data class RankedTrackStat(
    val track: Track,
    val playCount: Int,
    val totalListenedMs: Long
)

data class RankedArtistStat(
    val name: String,
    val artistId: String?,
    val playCount: Int,
    val totalListenedMs: Long
)

data class RankedAlbumStat(
    val title: String,
    val albumId: String?,
    val thumbnailUrl: String?,
    val playCount: Int,
    val totalListenedMs: Long
)

data class ListeningStatsSummary(
    val range: StatsRange,
    val totalListenedMs: Long = 0L,
    val totalPlays: Int = 0,
    val uniqueTracks: Int = 0,
    val uniqueArtists: Int = 0,
    val activeStreakDays: Int = 0,
    val peakHourOfDay: Int? = null,
    val hourlyMs: List<Long> = List(24) { 0L },
    val topTracks: List<RankedTrackStat> = emptyList(),
    val topArtists: List<RankedArtistStat> = emptyList(),
    val topAlbums: List<RankedAlbumStat> = emptyList()
)

object ListeningStatsCalculator {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun calculate(
        sessions: List<PlaySessionRecord>,
        tracksById: Map<String, Track>,
        range: StatsRange,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): ListeningStatsSummary {
        val cutoffMs = when (range) {
            StatsRange.TODAY -> startOfDayMs(nowMs, timeZone)
            StatsRange.SEVEN_DAYS -> startOfDayMs(nowMs - 6L * DAY_MS, timeZone)
            StatsRange.THIRTY_DAYS -> startOfDayMs(nowMs - 29L * DAY_MS, timeZone)
            StatsRange.ALL_TIME -> Long.MIN_VALUE
        }

        val filtered = sessions.filter { it.playedAtMs >= cutoffMs && it.listenedMs > 0L }
        val activeStreak = computeStreakDays(sessions, nowMs, timeZone)

        if (filtered.isEmpty()) {
            return ListeningStatsSummary(
                range = range,
                activeStreakDays = activeStreak
            )
        }

        val cal = Calendar.getInstance(timeZone)
        val hourly = LongArray(24)
        var totalMs = 0L

        val trackAccum = linkedMapOf<String, Pair<Int, Long>>() // videoId -> (plays, ms)
        val artistAccum = linkedMapOf<String, Triple<String?, Int, Long>>() // name -> (id, plays, ms)
        val albumAccum = linkedMapOf<String, Triple<AlbumRef, Int, Long>>() // key -> (ref, plays, ms)
        val albumThumb = linkedMapOf<String, String?>()

        for (session in filtered) {
            totalMs += session.listenedMs
            cal.timeInMillis = session.playedAtMs
            val hour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            hourly[hour] += session.listenedMs

            val prevTrack = trackAccum[session.videoId]
            trackAccum[session.videoId] = ((prevTrack?.first ?: 0) + 1) to ((prevTrack?.second ?: 0L) + session.listenedMs)

            val track = tracksById[session.videoId]
            if (track != null) {
                val primaryArtist = track.artists.firstOrNull()
                val artistName = primaryArtist?.name?.takeIf { it.isNotBlank() }
                    ?: track.artistDisplay.takeIf { it.isNotBlank() }
                if (!artistName.isNullOrBlank()) {
                    val prevArtist = artistAccum[artistName]
                    artistAccum[artistName] = Triple(
                        prevArtist?.first ?: primaryArtist?.id,
                        (prevArtist?.second ?: 0) + 1,
                        (prevArtist?.third ?: 0L) + session.listenedMs
                    )
                }

                val album = track.album
                if (album != null && album.title.isNotBlank()) {
                    val albumKey = album.id ?: album.title.lowercase()
                    val prevAlbum = albumAccum[albumKey]
                    albumAccum[albumKey] = Triple(
                        prevAlbum?.first ?: album,
                        (prevAlbum?.second ?: 0) + 1,
                        (prevAlbum?.third ?: 0L) + session.listenedMs
                    )
                    if (albumThumb[albumKey].isNullOrBlank() && !track.thumbnailUrl.isNullOrBlank()) {
                        albumThumb[albumKey] = track.thumbnailUrl
                    }
                }
            }
        }

        val peakHour = hourly.indices
            .maxByOrNull { hourly[it] }
            ?.takeIf { hourly[it] > 0L }

        val topTracks = trackAccum.entries
            .mapNotNull { (videoId, stats) ->
                tracksById[videoId]?.let { track ->
                    RankedTrackStat(track = track, playCount = stats.first, totalListenedMs = stats.second)
                }
            }
            .sortedWith(
                compareByDescending<RankedTrackStat> { it.totalListenedMs }
                    .thenByDescending { it.playCount }
            )
            .take(10)

        val topArtists = artistAccum.entries
            .map { (name, triple) ->
                RankedArtistStat(
                    name = name,
                    artistId = triple.first,
                    playCount = triple.second,
                    totalListenedMs = triple.third
                )
            }
            .sortedWith(
                compareByDescending<RankedArtistStat> { it.totalListenedMs }
                    .thenByDescending { it.playCount }
            )
            .take(10)

        val topAlbums = albumAccum.entries
            .map { (key, triple) ->
                RankedAlbumStat(
                    title = triple.first.title,
                    albumId = triple.first.id,
                    thumbnailUrl = albumThumb[key],
                    playCount = triple.second,
                    totalListenedMs = triple.third
                )
            }
            .sortedWith(
                compareByDescending<RankedAlbumStat> { it.totalListenedMs }
                    .thenByDescending { it.playCount }
            )
            .take(10)

        return ListeningStatsSummary(
            range = range,
            totalListenedMs = totalMs,
            totalPlays = filtered.size,
            uniqueTracks = trackAccum.size,
            uniqueArtists = artistAccum.size,
            activeStreakDays = activeStreak,
            peakHourOfDay = peakHour,
            hourlyMs = hourly.toList(),
            topTracks = topTracks,
            topArtists = topArtists,
            topAlbums = topAlbums
        )
    }

    fun computeStreakDays(
        sessions: List<PlaySessionRecord>,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        if (sessions.isEmpty()) return 0
        val dayKeys = sessions
            .filter { it.listenedMs > 0L }
            .map { dayKey(it.playedAtMs, timeZone) }
            .toHashSet()
        if (dayKeys.isEmpty()) return 0

        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMs }
        val todayKey = calendarDayKey(cal)
        val cursor = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayKey = calendarDayKey(cursor)

        val startOffset = when {
            todayKey in dayKeys -> 0
            yesterdayKey in dayKeys -> -1
            else -> return 0
        }

        var streak = 0
        val walk = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, startOffset)
        }
        while (calendarDayKey(walk) in dayKeys) {
            streak++
            walk.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    private fun startOfDayMs(timeMs: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun dayKey(timeMs: Long, timeZone: TimeZone): Int =
        calendarDayKey(Calendar.getInstance(timeZone).apply { timeInMillis = timeMs })

    private fun calendarDayKey(cal: Calendar): Int =
        cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}
