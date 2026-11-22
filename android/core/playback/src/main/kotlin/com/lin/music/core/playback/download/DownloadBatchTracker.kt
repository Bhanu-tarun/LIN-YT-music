package com.lin.music.core.playback.download

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class ActiveDownloadProgress(
    val id: String,
    val percentDownloaded: Float,
    val bytesDownloaded: Long
)

data class DownloadBatchSnapshot(
    val totalCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val remainingCount: Int,
    val progressPermille: Int,
    val progressPercent: Int,
    val isIndeterminate: Boolean,
    val isPausedForRequirements: Boolean,
    val waitingForWifi: Boolean
)

data class DownloadBatchSummary(
    val totalCount: Int,
    val completedCount: Int,
    val failedCount: Int
)

data class DownloadNotificationCopy(
    val title: String,
    val text: String,
    val subText: String?
)

/**
 * Coordinates grouped/batched download progress across [DownloadController] and [LinDownloadService]
 * so multi-song downloads render one stable notification with a numbered song counter and monotonic
 * progress bar rather than resetting on each individual track.
 */
@Singleton
class DownloadBatchTracker @Inject constructor() {

    private val batchIds = LinkedHashSet<String>()
    private val completedIds = HashSet<String>()
    private val failedIds = HashSet<String>()
    private var highWaterPermille = 0
    private var lastTotalCount = 0

    @Synchronized
    fun registerPending(ids: Collection<String>) {
        ids.forEach { id ->
            if (id.isNotBlank()) {
                batchIds.add(id)
                completedIds.remove(id)
                failedIds.remove(id)
            }
        }
    }

    @Synchronized
    fun markCompleted(id: String) {
        if (id.isBlank()) return
        batchIds.add(id)
        completedIds.add(id)
        failedIds.remove(id)
    }

    @Synchronized
    fun markFailed(id: String) {
        if (id.isBlank()) return
        batchIds.add(id)
        failedIds.add(id)
        completedIds.remove(id)
    }

    @Synchronized
    fun markRemoved(id: String) {
        if (id.isBlank()) return
        batchIds.remove(id)
        completedIds.remove(id)
        failedIds.remove(id)
        if (batchIds.isEmpty()) {
            highWaterPermille = 0
            lastTotalCount = 0
        }
    }

    @Synchronized
    fun clear() {
        batchIds.clear()
        completedIds.clear()
        failedIds.clear()
        highWaterPermille = 0
        lastTotalCount = 0
    }

    @Synchronized
    fun buildSnapshot(
        activeDownloads: List<ActiveDownloadProgress>,
        isPausedForRequirements: Boolean,
        waitingForWifi: Boolean
    ): DownloadBatchSnapshot {
        activeDownloads.forEach { active ->
            if (active.id.isNotBlank()) {
                batchIds.add(active.id)
                completedIds.remove(active.id)
                failedIds.remove(active.id)
            }
        }

        val total = batchIds.size
        if (total == 0) {
            return DownloadBatchSnapshot(
                totalCount = 0,
                completedCount = 0,
                failedCount = 0,
                remainingCount = 0,
                progressPermille = 0,
                progressPercent = 0,
                isIndeterminate = !isPausedForRequirements,
                isPausedForRequirements = isPausedForRequirements,
                waitingForWifi = waitingForWifi
            )
        }

        val completed = completedIds.count { it in batchIds }
        val failed = failedIds.count { it in batchIds }
        val finished = completed + failed
        val remaining = (total - finished).coerceAtLeast(0)

        var activeFractionSum = 0.0
        var hasKnownProgress = finished > 0
        activeDownloads.forEach { active ->
            if (active.id in batchIds && active.id !in completedIds && active.id !in failedIds) {
                if (active.percentDownloaded >= 0f) {
                    activeFractionSum += (active.percentDownloaded.toDouble() / 100.0).coerceIn(0.0, 1.0)
                    hasKnownProgress = true
                } else if (active.bytesDownloaded > 0L) {
                    hasKnownProgress = true
                }
            }
        }

        val rawPermille = (((finished + activeFractionSum) / total.toDouble()).coerceIn(0.0, 1.0) * 1000.0)
            .roundToInt()
            .coerceIn(0, 1000)

        if (total == lastTotalCount) {
            highWaterPermille = maxOf(highWaterPermille, rawPermille)
        } else {
            lastTotalCount = total
            highWaterPermille = rawPermille
        }

        val effectivePermille = if (remaining > 0) {
            highWaterPermille.coerceAtMost(999)
        } else {
            highWaterPermille
        }
        val progressPercent = (effectivePermille / 10).coerceIn(0, 100)
        val isIndeterminate = !hasKnownProgress && !isPausedForRequirements

        return DownloadBatchSnapshot(
            totalCount = total,
            completedCount = completed,
            failedCount = failed,
            remainingCount = remaining,
            progressPermille = effectivePermille,
            progressPercent = progressPercent,
            isIndeterminate = isIndeterminate,
            isPausedForRequirements = isPausedForRequirements,
            waitingForWifi = waitingForWifi
        )
    }

    @Synchronized
    fun consumeCompletedSummaryAndReset(): DownloadBatchSummary? {
        val total = batchIds.size
        val completed = completedIds.count { it in batchIds }
        val failed = failedIds.count { it in batchIds }
        clear()
        if (completed == 0 && failed == 0) return null
        return DownloadBatchSummary(
            totalCount = maxOf(total, completed + failed),
            completedCount = completed,
            failedCount = failed
        )
    }

    companion object {
        fun formatBatchNotification(snapshot: DownloadBatchSnapshot): DownloadNotificationCopy {
            val done = snapshot.completedCount
            val total = snapshot.totalCount
            val subText = if (total > 0) "$done / $total" else null

            val title = when {
                snapshot.isPausedForRequirements -> "Downloads paused · $done / $total"
                total <= 1 -> "Downloading 1 song"
                else -> "Downloading $done / $total songs"
            }

            val text = when {
                snapshot.isPausedForRequirements -> {
                    val reason = if (snapshot.waitingForWifi) "Waiting for Wi-Fi" else "Waiting for network"
                    "$reason · ${snapshot.remainingCount} left"
                }
                total <= 1 -> {
                    if (snapshot.isIndeterminate) "Starting download…" else "${snapshot.progressPercent}%"
                }
                snapshot.failedCount > 0 -> {
                    "${snapshot.progressPercent}% · ${snapshot.remainingCount} left · ${snapshot.failedCount} failed"
                }
                else -> {
                    "${snapshot.progressPercent}% · ${snapshot.remainingCount} left"
                }
            }

            return DownloadNotificationCopy(title = title, text = text, subText = subText)
        }

        fun formatCompletionNotification(summary: DownloadBatchSummary): DownloadNotificationCopy {
            val title = when {
                summary.failedCount == 0 && summary.completedCount == 1 -> "Downloaded 1 song"
                summary.failedCount == 0 -> "Downloaded ${summary.completedCount} songs"
                else -> "Downloaded ${summary.completedCount} / ${summary.totalCount} songs"
            }
            val text = if (summary.failedCount == 0) {
                "Ready for offline playback"
            } else {
                "${summary.failedCount} failed"
            }
            return DownloadNotificationCopy(
                title = title,
                text = text,
                subText = "${summary.completedCount} / ${summary.totalCount}"
            )
        }
    }
}
