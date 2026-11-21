package com.lin.music.core.playback.download

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.scheduler.Requirements
import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.DownloadEntity
import com.lin.music.core.data.database.TrackDao
import com.lin.music.core.data.database.toEntity
import com.lin.music.core.model.Track
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
    android.R.string.untitled,
    0
) {
    @Inject lateinit var injectedDownloadManager: DownloadManager
    @Inject lateinit var cacheDao: CacheDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var notificationHelper: DownloadNotificationHelper? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
        injectedDownloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                val mappedState = when (download.state) {
                    Download.STATE_COMPLETED -> 2
                    Download.STATE_FAILED -> 3
                    Download.STATE_DOWNLOADING -> 1
                    else -> 0
                }
                scope.launch {
                    val existing = cacheDao.getDownload(download.request.id)
                    cacheDao.upsertDownload(
                        DownloadEntity(
                            videoId = download.request.id,
                            state = mappedState,
                            progressPercent = download.percentDownloaded.coerceAtLeast(0f),
                            bytesDownloaded = download.bytesDownloaded,
                            totalBytes = download.contentLength.coerceAtLeast(0L),
                            sourceFlags = existing?.sourceFlags ?: DownloadEntity.SOURCE_MANUAL
                        )
                    )
                }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                scope.launch {
                    cacheDao.deleteDownload(download.request.id)
                }
            }
        })
    }

    override fun getDownloadManager(): DownloadManager = injectedDownloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val helper = notificationHelper ?: DownloadNotificationHelper(this, CHANNEL_ID).also {
            notificationHelper = it
        }
        return helper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }

    companion object {
        const val CHANNEL_ID = "lin_downloads"
        const val NOTIFICATION_ID = 4042
        const val JOB_ID = 4043
    }
}

@OptIn(UnstableApi::class)
@Singleton
class DownloadController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val cacheDao: CacheDao,
    private val downloadManager: DownloadManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateWifiOnlyRequirement(wifiOnly: Boolean) {
        val req = if (wifiOnly) Requirements(Requirements.NETWORK_UNMETERED) else Requirements(Requirements.NETWORK)
        if (downloadManager.requirements != req) {
            downloadManager.requirements = req
        }
    }

    fun downloadTrack(track: Track, sourceFlag: Int = DownloadEntity.SOURCE_MANUAL) {
        scope.launch {
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
                cacheDao.upsertDownload(
                    existingDl.copy(
                        sourceFlags = mergedFlags,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
                return@launch
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
            val req = DownloadRequest.Builder(
                track.videoId,
                Uri.parse("${PlaybackModule.STREAM_URI_PREFIX}${track.videoId}")
            )
                .setCustomCacheKey(track.videoId)
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

    fun downloadAll(tracks: List<Track>, sourceFlag: Int = DownloadEntity.SOURCE_MANUAL) {
        tracks.forEach { downloadTrack(it, sourceFlag) }
    }

    suspend fun clearSourceFlagOrRemove(videoId: String, sourceFlag: Int) {
        val existing = cacheDao.getDownload(videoId) ?: return
        val remaining = existing.sourceFlags and sourceFlag.inv()
        if (remaining == 0) {
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
