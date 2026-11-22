package com.lin.music.core.playback.download

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.DownloadEntity
import com.lin.music.core.data.database.TrackDao
import com.lin.music.core.data.database.toEntity
import com.lin.music.core.model.Track
import com.lin.music.core.playback.R
import com.lin.music.core.playback.di.PlaybackModule
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class LinDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.lin_downloads_channel_name,
    R.string.lin_downloads_channel_description
) {
    @Inject lateinit var injectedDownloadManager: DownloadManager
    @Inject lateinit var cacheDao: CacheDao
    @Inject lateinit var batchTracker: DownloadBatchTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            when (download.state) {
                Download.STATE_COMPLETED -> batchTracker.markCompleted(download.request.id)
                Download.STATE_FAILED -> batchTracker.markFailed(download.request.id)
                Download.STATE_REMOVING -> batchTracker.markRemoved(download.request.id)
                else -> Unit
            }
            val mappedState = when (download.state) {
                Download.STATE_COMPLETED -> 2
                Download.STATE_FAILED -> 3
                Download.STATE_DOWNLOADING -> 1
                else -> 0
            }
            scope.launch {
                val existing = cacheDao.getDownload(download.request.id)
                val keptTimestamp = if (existing != null && existing.state == 2 && mappedState == 2) {
                    existing.updatedAtMs
                } else {
                    System.currentTimeMillis()
                }
                cacheDao.upsertDownload(
                    DownloadEntity(
                        videoId = download.request.id,
                        state = mappedState,
                        progressPercent = download.percentDownloaded.coerceAtLeast(0f),
                        bytesDownloaded = download.bytesDownloaded,
                        totalBytes = download.contentLength.coerceAtLeast(0L),
                        sourceFlags = existing?.sourceFlags ?: DownloadEntity.SOURCE_MANUAL,
                        updatedAtMs = keptTimestamp
                    )
                )
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            batchTracker.markRemoved(download.request.id)
            scope.launch {
                cacheDao.deleteDownload(download.request.id)
            }
        }

        override fun onIdle(downloadManager: DownloadManager) {
            // Media3 invokes onIdle both on completion and when downloads wait on unmet requirements.
            val hasPending = downloadManager.currentDownloads.any { it.state != Download.STATE_REMOVING }
            if (hasPending) return
            val summary = batchTracker.consumeCompletedSummaryAndReset() ?: return
            postCompletionNotification(summary)
        }
    }

    override fun onCreate() {
        super.onCreate()
        injectedDownloadManager.addListener(downloadListener)
    }

    override fun onDestroy() {
        injectedDownloadManager.removeListener(downloadListener)
        super.onDestroy()
    }

    override fun getDownloadManager(): DownloadManager = injectedDownloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        NotificationManagerCompat.from(this).cancel(SUMMARY_NOTIFICATION_ID)

        val activeDownloads = downloads.filter { it.state != Download.STATE_REMOVING }
        if (activeDownloads.isEmpty() && downloads.any { it.state == Download.STATE_REMOVING }) {
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Removing downloads")
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setLocalOnly(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setGroup(NOTIFICATION_GROUP)
                .setContentIntent(createLaunchPendingIntent())
                .build()
        }

        val snapshot = batchTracker.buildSnapshot(
            activeDownloads = activeDownloads.map { dl ->
                ActiveDownloadProgress(
                    id = dl.request.id,
                    percentDownloaded = dl.percentDownloaded,
                    bytesDownloaded = dl.bytesDownloaded
                )
            },
            isPausedForRequirements = notMetRequirements != 0,
            waitingForWifi = (notMetRequirements and Requirements.NETWORK_UNMETERED) != 0
        )
        val copy = DownloadBatchTracker.formatBatchNotification(snapshot)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(copy.title)
            .setContentText(copy.text)
            .apply { copy.subText?.let { setSubText(it) } }
            .setProgress(1000, snapshot.progressPermille, snapshot.isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setGroup(NOTIFICATION_GROUP)
            .setContentIntent(createLaunchPendingIntent())
            .build()
    }

    private fun postCompletionNotification(summary: DownloadBatchSummary) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val copy = DownloadBatchTracker.formatCompletionNotification(summary)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(copy.title)
            .setContentText(copy.text)
            .apply { copy.subText?.let { setSubText(it) } }
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setGroup(NOTIFICATION_GROUP)
            .setContentIntent(createLaunchPendingIntent())
            .build()
        NotificationManagerCompat.from(this).notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    private fun createLaunchPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "lin_downloads"
        const val NOTIFICATION_GROUP = "com.lin.music.DOWNLOADS"
        const val NOTIFICATION_ID = 4042
        const val JOB_ID = 4043
        const val SUMMARY_NOTIFICATION_ID = 4044
    }
}

@OptIn(UnstableApi::class)
@Singleton
class DownloadController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val cacheDao: CacheDao,
    private val downloadManager: DownloadManager,
    private val batchTracker: DownloadBatchTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateWifiOnlyRequirement(wifiOnly: Boolean) {
        val req = if (wifiOnly) Requirements(Requirements.NETWORK_UNMETERED) else Requirements(Requirements.NETWORK)
        if (downloadManager.requirements != req) {
            downloadManager.requirements = req
        }
    }

    fun downloadTrack(track: Track, sourceFlag: Int = DownloadEntity.SOURCE_MANUAL) {
        downloadAll(listOf(track), sourceFlag)
    }

    fun downloadAll(tracks: List<Track>, sourceFlag: Int = DownloadEntity.SOURCE_MANUAL) {
        scope.launch {
            val uniqueTracks = tracks
                .filter { it.videoId.isNotBlank() }
                .distinctBy { it.videoId }
            if (uniqueTracks.isEmpty()) return@launch

            val pendingIds = ArrayList<String>(uniqueTracks.size)
            for (track in uniqueTracks) {
                val existing = trackDao.getById(track.videoId)
                val entity = track.toEntity(
                    inHistory = existing?.inHistory == true,
                    historyTimestampMs = existing?.historyTimestampMs ?: 0L,
                    playCount = existing?.playCount ?: 0
                )
                trackDao.upsert(
                    entity.copy(
                        inLibrary = track.inLibrary || (existing?.inLibrary == true),
                        isLiked = track.isLiked || (existing?.isLiked == true),
                        albumId = entity.albumId ?: existing?.albumId,
                        albumTitle = entity.albumTitle ?: existing?.albumTitle,
                        feedbackTokenAdd = entity.feedbackTokenAdd ?: existing?.feedbackTokenAdd,
                        feedbackTokenRemove = entity.feedbackTokenRemove ?: existing?.feedbackTokenRemove
                    )
                )
                val existingDl = cacheDao.getDownload(track.videoId)
                val mergedFlags = (existingDl?.sourceFlags ?: 0) or sourceFlag
                if (existingDl != null && existingDl.state == 2) {
                    val nextUpdatedAt = if (
                        sourceFlag == DownloadEntity.SOURCE_SMART &&
                        (existingDl.sourceFlags and DownloadEntity.SOURCE_SMART) == 0
                    ) {
                        System.currentTimeMillis()
                    } else {
                        existingDl.updatedAtMs
                    }
                    cacheDao.upsertDownload(
                        existingDl.copy(
                            sourceFlags = mergedFlags,
                            updatedAtMs = nextUpdatedAt
                        )
                    )
                    continue
                }
                cacheDao.upsertDownload(
                    DownloadEntity(
                        videoId = track.videoId,
                        state = existingDl?.state ?: 0,
                        progressPercent = existingDl?.progressPercent ?: 0f,
                        bytesDownloaded = existingDl?.bytesDownloaded ?: 0L,
                        totalBytes = existingDl?.totalBytes ?: 0L,
                        sourceFlags = mergedFlags
                    )
                )
                pendingIds.add(track.videoId)
            }

            if (pendingIds.isEmpty()) return@launch

            // Register the whole batch before submitting requests so the notification starts at 0 / N.
            batchTracker.registerPending(pendingIds)
            NotificationManagerCompat.from(context).cancel(LinDownloadService.SUMMARY_NOTIFICATION_ID)

            for (videoId in pendingIds) {
                val req = DownloadRequest.Builder(
                    videoId,
                    Uri.parse("${PlaybackModule.STREAM_URI_PREFIX}$videoId")
                )
                    .setCustomCacheKey(videoId)
                    .build()
                sendDownloadCommandSafe(
                    sendService = {
                        DownloadService.sendAddDownload(context, LinDownloadService::class.java, req, false)
                    },
                    directFallback = {
                        downloadManager.addDownload(req)
                        downloadManager.resumeDownloads()
                    }
                )
            }
        }
    }

    suspend fun clearSourceFlagOrRemove(videoId: String, sourceFlag: Int) {
        val existing = cacheDao.getDownload(videoId) ?: return
        val remaining = existing.sourceFlags and sourceFlag.inv()
        if (remaining == 0) {
            batchTracker.markRemoved(videoId)
            cacheDao.deleteDownload(videoId)
            sendDownloadCommandSafe(
                sendService = {
                    DownloadService.sendRemoveDownload(context, LinDownloadService::class.java, videoId, false)
                },
                directFallback = {
                    downloadManager.removeDownload(videoId)
                }
            )
        } else {
            cacheDao.upsertDownload(
                existing.copy(
                    sourceFlags = remaining,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeDownload(videoId: String) {
        scope.launch {
            batchTracker.markRemoved(videoId)
            cacheDao.deleteDownload(videoId)
            sendDownloadCommandSafe(
                sendService = {
                    DownloadService.sendRemoveDownload(context, LinDownloadService::class.java, videoId, false)
                },
                directFallback = {
                    downloadManager.removeDownload(videoId)
                }
            )
        }
    }

    fun removeAllDownloads() {
        scope.launch {
            batchTracker.clear()
            NotificationManagerCompat.from(context).cancel(LinDownloadService.SUMMARY_NOTIFICATION_ID)
            cacheDao.clearAllDownloads()
            sendDownloadCommandSafe(
                sendService = {
                    DownloadService.sendRemoveAllDownloads(context, LinDownloadService::class.java, false)
                },
                directFallback = {
                    downloadManager.removeAllDownloads()
                }
            )
        }
    }

    private suspend fun sendDownloadCommandSafe(
        sendService: () -> Unit,
        directFallback: () -> Unit
    ) {
        val sent = runCatching { sendService() }.isSuccess
        if (!sent) {
            withContext(Dispatchers.Main.immediate) {
                runCatching { directFallback() }
            }
        }
    }
}
