package com.lin.music.core.playback.download

import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.DownloadEntity
import com.lin.music.core.data.database.TrackDao
import com.lin.music.core.data.datastore.PreferencesStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles YouTube Music style automated offline collections:
 * - Smart Downloads: most-played, liked, and library tracks up to the user's configured song cap
 * - Recently Played Downloads: most recent listening history tracks up to the configured cap
 */
@Singleton
class SmartDownloadManager @Inject constructor(
    private val trackDao: TrackDao,
    private val cacheDao: CacheDao,
    private val downloadController: DownloadController,
    private val preferencesStore: PreferencesStore
) {
    private val syncMutex = Mutex()

    suspend fun syncAll() {
        syncMutex.withLock {
            val prefs = preferencesStore.current()
            syncSmartDownloadsLocked(prefs.smartDownloadsEnabled, prefs.smartDownloadsLimit)
            syncRecentlyPlayedDownloadsLocked(
                prefs.recentlyPlayedDownloadsEnabled,
                prefs.recentlyPlayedDownloadsLimit
            )
        }
    }

    private suspend fun syncSmartDownloadsLocked(enabled: Boolean, limit: Int) {
        val currentSmart = cacheDao.getDownloadsBySource(DownloadEntity.SOURCE_SMART)
        if (!enabled) {
            currentSmart.forEach { dl ->
                downloadController.clearSourceFlagOrRemove(dl.videoId, DownloadEntity.SOURCE_SMART)
            }
            return
        }
        val candidates = trackDao.getSmartDownloadCandidates(limit.coerceIn(1, 500))
        val desiredIds = candidates.map { it.videoId }.toSet()

        currentSmart
            .filter { it.videoId !in desiredIds }
            .forEach { dl ->
                downloadController.clearSourceFlagOrRemove(dl.videoId, DownloadEntity.SOURCE_SMART)
            }

        val existingById = cacheDao.getAllDownloadsSnapshot().associateBy { it.videoId }
        candidates.forEach { entity ->
            val existing = existingById[entity.videoId]
            val alreadyFlagged = existing != null && (existing.sourceFlags and DownloadEntity.SOURCE_SMART) != 0
            if (!alreadyFlagged || existing?.state != 2) {
                downloadController.downloadTrack(entity.toModel(), sourceFlag = DownloadEntity.SOURCE_SMART)
            }
        }
    }

    private suspend fun syncRecentlyPlayedDownloadsLocked(enabled: Boolean, limit: Int) {
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
        val candidates = trackDao.getRecentlyPlayedCandidates(limit.coerceIn(1, 200))
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
    }
}
