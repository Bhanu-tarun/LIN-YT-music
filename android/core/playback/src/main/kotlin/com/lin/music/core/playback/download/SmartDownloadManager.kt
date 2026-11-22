package com.lin.music.core.playback.download

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.DownloadEntity
import com.lin.music.core.data.database.TrackDao
import com.lin.music.core.data.database.TrackEntity
import com.lin.music.core.data.database.TrackListenStat
import com.lin.music.core.data.datastore.PreferencesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Battery-friendly, YouTube Music-aligned automated offline collection manager:
 * - Smart Downloads: refreshes at most once per 24h when eligible (battery >= 20% or charging,
 *   not in Power Saver mode, unmetered Wi-Fi when wifi-only is enabled), keeping downloads sticky
 *   for 7 days with score hysteresis and a 15% daily rotation cap.
 * - Recently Played Downloads: refreshes once every 24h when eligible, ranking songs from the
 *   last 24 hours first by cumulative listen time descending (then 7d listen time & history fallback).
 */
@Singleton
class SmartDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val cacheDao: CacheDao,
    private val downloadController: DownloadController,
    private val preferencesStore: PreferencesStore
) {
    private val syncMutex = Mutex()

    suspend fun syncAll(force: Boolean = false) {
        syncMutex.withLock {
            val prefs = preferencesStore.current()
            syncSmartDownloadsLocked(prefs.smartDownloadsEnabled, prefs.smartDownloadsLimit, prefs.downloadWifiOnly, force)
            syncRecentlyPlayedDownloadsLocked(
                prefs.recentlyPlayedDownloadsEnabled,
                prefs.recentlyPlayedDownloadsLimit,
                prefs.downloadWifiOnly,
                force
            )
        }
    }

    suspend fun syncSmartDownloads(force: Boolean = false) {
        syncMutex.withLock {
            val prefs = preferencesStore.current()
            syncSmartDownloadsLocked(prefs.smartDownloadsEnabled, prefs.smartDownloadsLimit, prefs.downloadWifiOnly, force)
        }
    }

    suspend fun syncRecentlyPlayedDownloads(force: Boolean = false) {
        syncMutex.withLock {
            val prefs = preferencesStore.current()
            syncRecentlyPlayedDownloadsLocked(
                prefs.recentlyPlayedDownloadsEnabled,
                prefs.recentlyPlayedDownloadsLimit,
                prefs.downloadWifiOnly,
                force
            )
        }
    }

    private suspend fun syncSmartDownloadsLocked(
        enabled: Boolean,
        limit: Int,
        wifiOnly: Boolean,
        force: Boolean
    ) {
        val currentSmart = cacheDao.getDownloadsBySource(DownloadEntity.SOURCE_SMART)
        if (!enabled) {
            currentSmart.forEach { dl ->
                downloadController.clearSourceFlagOrRemove(dl.videoId, DownloadEntity.SOURCE_SMART)
            }
            return
        }

        val nowMs = System.currentTimeMillis()
        if (!force) {
            val lastSync = preferencesStore.getLastSmartDownloadSyncMs()
            if (lastSync > 0L && (nowMs - lastSync) < SMART_SYNC_INTERVAL_MS) {
                return
            }
            if (!isEligibleForAutoDownload(requireUnmetered = wifiOnly, minBatteryPct = 20)) {
                return
            }
        }

        val clampedLimit = limit.coerceIn(1, 500)
        val candidates = trackDao.getSmartDownloadCandidates((clampedLimit * 2).coerceIn(clampedLimit, 500))
        val incumbentIds = currentSmart.map { it.videoId }.distinct()
        val incumbentTracksById = incumbentIds
            .chunked(500)
            .flatMap { trackDao.getByIds(it) }
            .associateBy { it.videoId }
        val pinnedSongIds = cacheDao.getSpeedDialPinsSnapshot()
            .asSequence()
            .filter { it.key.startsWith("song:") }
            .map { it.key.removePrefix("song:") }
            .toSet()

        val plan = planSmartDownloadUpdates(
            currentSmart = currentSmart,
            candidateTracks = candidates,
            incumbentTracksById = incumbentTracksById,
            pinnedVideoIds = pinnedSongIds,
            limit = clampedLimit,
            nowMs = nowMs
        )

        plan.removeIds.forEach { videoId ->
            downloadController.clearSourceFlagOrRemove(videoId, DownloadEntity.SOURCE_SMART)
        }

        val existingById = cacheDao.getAllDownloadsSnapshot().associateBy { it.videoId }
        plan.addTracks.forEach { entity ->
            val existing = existingById[entity.videoId]
            val alreadyFlagged = existing != null && (existing.sourceFlags and DownloadEntity.SOURCE_SMART) != 0
            if (!alreadyFlagged || existing?.state != 2) {
                downloadController.downloadTrack(entity.toModel(), sourceFlag = DownloadEntity.SOURCE_SMART)
            }
        }
        preferencesStore.setLastSmartDownloadSyncMs(nowMs)
    }

    private suspend fun syncRecentlyPlayedDownloadsLocked(
        enabled: Boolean,
        limit: Int,
        wifiOnly: Boolean,
        force: Boolean
    ) {
        val currentRecent = cacheDao.getDownloadsBySource(DownloadEntity.SOURCE_RECENTLY_PLAYED)
        if (!enabled) {
            currentRecent.forEach { dl ->
                downloadController.clearSourceFlagOrRemove(
                    dl.videoId,
                    DownloadEntity.SOURCE_RECENTLY_PLAYED
                )
            }
            return
        }

        val nowMs = System.currentTimeMillis()
        if (!force) {
            val lastSync = preferencesStore.getLastRecentDownloadSyncMs()
            if (lastSync > 0L && (nowMs - lastSync) < RECENT_SYNC_INTERVAL_MS) {
                return
            }
            if (!isEligibleForAutoDownload(requireUnmetered = wifiOnly, minBatteryPct = 20)) {
                return
            }
        }

        val clampedLimit = limit.coerceIn(1, 200)
        val fetchLimit = (clampedLimit * 2).coerceAtMost(500)
        val stats24h = cacheDao.getListenStatsSince(nowMs - RECENT_PRIMARY_WINDOW_MS, fetchLimit)
        val stats7d = cacheDao.getListenStatsSince(nowMs - RECENT_FALLBACK_WINDOW_MS, fetchLimit)
        val statIds = (stats24h.map { it.videoId } + stats7d.map { it.videoId })
            .distinct()
            .take(500)
        val tracksById = if (statIds.isNotEmpty()) {
            trackDao.getByIds(statIds).associateBy { it.videoId }
        } else {
            emptyMap()
        }
        val fallbackHistory = if (tracksById.size < clampedLimit) {
            trackDao.getRecentlyPlayedCandidates(clampedLimit)
        } else {
            emptyList()
        }

        val candidates = selectRecentlyPlayedTracks(
            stats24h = stats24h,
            stats7d = stats7d,
            tracksById = tracksById,
            fallbackHistory = fallbackHistory,
            limit = clampedLimit
        )
        val desiredIds = candidates.map { it.videoId }.toSet()

        currentRecent
            .filter { it.videoId !in desiredIds }
            .forEach { dl ->
                downloadController.clearSourceFlagOrRemove(
                    dl.videoId,
                    DownloadEntity.SOURCE_RECENTLY_PLAYED
                )
            }

        val existingById = cacheDao.getAllDownloadsSnapshot().associateBy { it.videoId }
        candidates.forEach { entity ->
            val existing = existingById[entity.videoId]
            val alreadyFlagged =
                existing != null && (existing.sourceFlags and DownloadEntity.SOURCE_RECENTLY_PLAYED) != 0
            if (!alreadyFlagged || existing?.state != 2) {
                downloadController.downloadTrack(
                    entity.toModel(),
                    sourceFlag = DownloadEntity.SOURCE_RECENTLY_PLAYED
                )
            }
        }
        cacheDao.prunePlaySessionsBefore(nowMs - PLAY_SESSION_RETENTION_MS)
        preferencesStore.setLastRecentDownloadSyncMs(nowMs)
    }

    private fun isEligibleForAutoDownload(requireUnmetered: Boolean, minBatteryPct: Int): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm?.isPowerSaveMode == true) return false

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent != null) {
            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val pct = if (level >= 0) (level * 100) / scale else -1
            if (!isCharging && pct in 0 until minBatteryPct) {
                return false
            }
        }

        if (requireUnmetered) {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                return false
            }
        }
        return true
    }

    data class SmartDownloadPlan(
        val removeIds: List<String>,
        val addTracks: List<TrackEntity>
    )

    companion object {
        const val SMART_SYNC_INTERVAL_MS = 24L * 60L * 60_000L
        const val RECENT_SYNC_INTERVAL_MS = 24L * 60L * 60_000L
        const val RECENT_PRIMARY_WINDOW_MS = 24L * 60L * 60_000L
        const val RECENT_FALLBACK_WINDOW_MS = 7L * 24L * 60L * 60_000L
        const val PLAY_SESSION_RETENTION_MS = 14L * 24L * 60L * 60_000L
        const val RETENTION_WINDOW_MS = 7L * 24L * 60L * 60_000L
        const val MAX_ROTATION_FRACTION = 0.15
        const val HYSTERESIS_RATIO = 1.35
        const val HYSTERESIS_MIN_DELTA = 10.0

        fun selectRecentlyPlayedTracks(
            stats24h: List<TrackListenStat>,
            stats7d: List<TrackListenStat>,
            tracksById: Map<String, TrackEntity>,
            fallbackHistory: List<TrackEntity>,
            limit: Int
        ): List<TrackEntity> {
            val clampedLimit = limit.coerceIn(1, 200)
            val selected = ArrayList<TrackEntity>(clampedLimit)
            val seenIds = HashSet<String>(clampedLimit * 2)

            fun appendSorted(stats: List<TrackListenStat>) {
                if (selected.size >= clampedLimit) return
                stats
                    .sortedWith(
                        compareByDescending<TrackListenStat> { it.totalListenedMs }
                            .thenByDescending { it.lastPlayedAtMs }
                    )
                    .forEach { stat ->
                        if (selected.size >= clampedLimit) return@forEach
                        tracksById[stat.videoId]?.let { entity ->
                            if (seenIds.add(stat.videoId)) {
                                selected += entity
                            }
                        }
                    }
            }

            // Tier 1: last 24h first, ordered by longest total listening time.
            appendSorted(stats24h)
            // Tier 2: fill any spare capacity from the past 7 days by listen time.
            appendSorted(stats7d)
            // Tier 3: fall back to recency history if listen sessions have not filled the cap yet.
            if (selected.size < clampedLimit) {
                fallbackHistory.forEach { entity ->
                    if (selected.size >= clampedLimit) return@forEach
                    if (seenIds.add(entity.videoId)) {
                        selected += entity
                    }
                }
            }
            return selected
        }

        fun scoreSmartCandidate(
            entity: TrackEntity,
            isPinned: Boolean,
            nowMs: Long
        ): Double {
            val daysSince = ((nowMs - entity.historyTimestampMs).coerceAtLeast(0L)) / 86_400_000.0
            val pinBonus = if (isPinned) 1000.0 else 0.0
            val likeBonus = if (entity.isLiked) 25.0 else 0.0
            val libBonus = if (entity.inLibrary) 8.0 else 0.0
            val decayedPlays = entity.playCount * 10.0 / (1.0 + daysSince / 21.0)
            return pinBonus + likeBonus + libBonus + decayedPlays
        }

        fun planSmartDownloadUpdates(
            currentSmart: List<DownloadEntity>,
            candidateTracks: List<TrackEntity>,
            incumbentTracksById: Map<String, TrackEntity>,
            pinnedVideoIds: Set<String>,
            limit: Int,
            nowMs: Long
        ): SmartDownloadPlan {
            val clampedLimit = limit.coerceIn(1, 500)
            val candidateById = candidateTracks.associateBy { it.videoId }

            fun scoreOf(videoId: String): Double {
                val track = candidateById[videoId] ?: incumbentTracksById[videoId]
                return if (track != null) {
                    scoreSmartCandidate(track, videoId in pinnedVideoIds, nowMs)
                } else if (videoId in pinnedVideoIds) {
                    1000.0
                } else {
                    0.0
                }
            }

            val retained = currentSmart.toMutableList()
            val removeIds = mutableListOf<String>()

            if (retained.size > clampedLimit) {
                val trimOrder = retained.sortedWith(
                    compareBy<DownloadEntity> { it.videoId in pinnedVideoIds }
                        .thenBy { scoreOf(it.videoId) }
                        .thenBy { it.updatedAtMs }
                )
                val toTrim = trimOrder.take(retained.size - clampedLimit)
                val trimSet = toTrim.map { it.videoId }.toSet()
                removeIds += trimSet
                retained.removeAll { it.videoId in trimSet }
            }

            val retainedIds = retained.map { it.videoId }.toMutableSet()
            val sortedCandidates = candidateTracks
                .distinctBy { it.videoId }
                .sortedByDescending { scoreSmartCandidate(it, it.videoId in pinnedVideoIds, nowMs) }

            val addTracks = mutableListOf<TrackEntity>()
            val openSlots = (clampedLimit - retained.size).coerceAtLeast(0)
            if (openSlots > 0) {
                sortedCandidates
                    .filter { it.videoId !in retainedIds }
                    .take(openSlots)
                    .forEach { chosen ->
                        addTracks += chosen
                        retainedIds += chosen.videoId
                    }
            }

            // Sticky replacement: only swap completed, unpinned incumbents older than 7 days,
            // capped at 15% per sync, when challenger beats incumbent with strong hysteresis.
            val eligibleIncumbents = retained
                .filter { dl ->
                    dl.videoId !in pinnedVideoIds &&
                        dl.state == 2 &&
                        (nowMs - dl.updatedAtMs) >= RETENTION_WINDOW_MS
                }
                .sortedBy { scoreOf(it.videoId) }
                .toMutableList()

            val eligibleChallengers = sortedCandidates.filter { cand ->
                cand.videoId !in retainedIds &&
                    (cand.videoId in pinnedVideoIds || cand.playCount >= 2 || cand.isLiked)
            }

            val maxSwaps = maxOf(1, (clampedLimit * MAX_ROTATION_FRACTION).toInt())
            var swaps = 0
            for (challenger in eligibleChallengers) {
                if (swaps >= maxSwaps || eligibleIncumbents.isEmpty()) break
                val weakest = eligibleIncumbents.first()
                val weakestScore = scoreOf(weakest.videoId)
                val challengerScore = scoreSmartCandidate(challenger, challenger.videoId in pinnedVideoIds, nowMs)
                if (challengerScore >= weakestScore * HYSTERESIS_RATIO + HYSTERESIS_MIN_DELTA) {
                    eligibleIncumbents.removeAt(0)
                    removeIds += weakest.videoId
                    addTracks += challenger
                    swaps++
                } else {
                    break
                }
            }

            return SmartDownloadPlan(
                removeIds = removeIds.distinct(),
                addTracks = addTracks.distinctBy { it.videoId }
            )
        }
    }
}
