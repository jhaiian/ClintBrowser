package com.jhaiian.clint.downloads

import java.io.File

enum class DownloadStatus {
    QUEUED, CONNECTING, ALLOCATING, DOWNLOADING, RETRYING, COPYING_TEMP, DELETING_TEMP, PAUSED, FAILED, COMPLETE;

    companion object {
        /** Statuses where a download is actively transferring or otherwise doing real I/O work. */
        val ACTIVELY_WORKING: Set<DownloadStatus> =
            setOf(CONNECTING, DOWNLOADING, ALLOCATING, COPYING_TEMP, DELETING_TEMP, RETRYING)

        /** Statuses where a download still represents an unfinished request for the same file, including queued and paused. */
        val NOT_FINISHED: Set<DownloadStatus> =
            setOf(QUEUED, CONNECTING, ALLOCATING, DOWNLOADING, RETRYING, COPYING_TEMP, DELETING_TEMP, PAUSED)
    }
}

data class DownloadItem(
    val id: Int,
    var url: String,
    var filename: String,
    val userAgent: String,
    val referer: String = "",
    val cookies: String = "",
    var bytesDownloaded: Long = 0L,
    var totalBytes: Long = -1L,
    var status: DownloadStatus = DownloadStatus.DOWNLOADING,
    var file: File? = null,
    var errorMessage: String? = null,
    var startedAt: Long = 0L,
    var speedBytesPerSec: Long = 0L,
    var resumable: Boolean = false,
    var copyProgress: Int = 0,
    var contentUri: String? = null,
    var retryAttempt: Int = 0,
    var retryDelaySec: Int = 0,
    var allocationProgress: Int = 0,
    var waitingForUnmetered: Boolean = false,
    var waitingForNetwork: Boolean = false,
    var waitingForSchedule: Boolean = false,
    /** Epoch millis the user picked via "Schedule This Download", or 0L if none was set. When set, this
     *  overrides [DownloadScheduleMonitor]'s global time-window setting for this download only. */
    var scheduledStartAtMillis: Long = 0L,
    /** True while paused specifically because [scheduledStartAtMillis] hasn't been reached yet. Unlike
     *  the other waitingFor* flags this is persisted, since the wait can span an app restart. */
    var waitingForCustomSchedule: Boolean = false,
    var activeElapsedMs: Long = 0L,
    @Transient var activeStartedAt: Long = 0L,
    @Transient var parallelRateLimited: Boolean = false,
    var completedAt: Long = 0L,
    var retryEnabled: Boolean = true,
    var lastErrorWasServerError: Boolean = false,
    var unmeteredOnly: Boolean = false,
    var splitParts: Int = 32,
    var multithreadingParts: Int = 4,
    /** Maximum aggregate transfer rate in bytes/sec, shared across all split parts. 0 means unlimited. Resolved from the user's chosen amount and unit (KB/MB) at enqueue time, so a later change to the measurement system doesn't retroactively alter an in-progress download. */
    var speedLimitBytesPerSec: Long = 0L,
    var locationMode: String = "default",
    var customLocationUri: String? = null,
    /** Bitmask (bit i = part i) of which parallel download parts are already fully written to disk. */
    var completedPartsMask: Long = 0L,
    /** "idx:bytes,idx:bytes" of already-written bytes for parts not yet complete, so a resume doesn't re-fetch them. */
    var partOffsets: String = ""
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else -1

    /**
     * Average throughput across the download's whole active transfer time so far, excluding time
     * spent paused. Unlike the instantaneous [speedBytesPerSec] reading, this stays stable through
     * momentary dips, which is what a time-remaining estimate should be based on.
     */
    fun averageSpeedBytesPerSec(): Long {
        val activeMs = activeElapsedMs + (if (activeStartedAt > 0L) System.currentTimeMillis() - activeStartedAt else 0L)
        return if (activeMs > 0L && bytesDownloaded > 0L) bytesDownloaded * 1000L / activeMs else 0L
    }
}
