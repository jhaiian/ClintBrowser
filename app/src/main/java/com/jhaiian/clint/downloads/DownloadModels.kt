package com.jhaiian.clint.downloads

import java.io.File

enum class DownloadStatus {
    QUEUED, CONNECTING, ALLOCATING, DOWNLOADING, RETRYING, MOVING, PAUSED, FAILED, COMPLETE
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
    var moveProgress: Int = 0,
    var contentUri: String? = null,
    var retryAttempt: Int = 0,
    var retryDelaySec: Int = 0,
    var allocationProgress: Int = 0,
    var waitingForUnmetered: Boolean = false,
    var waitingForNetwork: Boolean = false,
    var activeElapsedMs: Long = 0L,
    @Transient var activeStartedAt: Long = 0L,
    @Transient var parallelRateLimited: Boolean = false,
    var completedAt: Long = 0L,
    var retryEnabled: Boolean = true,
    var lastErrorWasServerError: Boolean = false,
    var unmeteredOnly: Boolean = false,
    var splitParts: Int = 32,
    var multithreadingParts: Int = 4,
    var locationMode: String = "default",
    var customLocationUri: String? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else -1
}
