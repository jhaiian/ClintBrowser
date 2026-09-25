package com.jhaiian.clint.mediacapture.download

import android.webkit.CookieManager
import okhttp3.Request

enum class TrackKind { VIDEO, AUDIO }

enum class StreamContainerFormat { HLS, DASH }

data class HlsKeyInfo(
    val keyUri: String,
    val ivHex: String?,
    val method: String
)

data class SegmentSpec(
    val url: String,
    val byteRangeOffset: Long? = null,
    val byteRangeLength: Long? = null,
    val sequenceNumber: Long = 0L,
    val key: HlsKeyInfo? = null
)

data class ResolvedTrack(
    val kind: TrackKind,
    val initSegment: SegmentSpec?,
    val segments: List<SegmentSpec>,
    val durationSeconds: Double? = null,
    val containerHintExtension: String = "ts",
    val isLive: Boolean = false,
    val targetDurationSeconds: Double? = null
)

data class StreamDownloadRequest(
    val format: StreamContainerFormat,
    val videoUrl: String?,
    val audioUrl: String?,
    val subtitleUrl: String?,
    val pageUrl: String,
    val referer: String,
    val cookies: String,
    val userAgent: String,
    val filename: String,
    val headers: Map<String, String> = emptyMap(),
    val estimatedTotalBytes: Long = 0L,
    val speedLimitBytesPerSec: Long = 0L,
    val concurrentSegments: Int = 6,
    val noAudio: Boolean = false,
    val isLive: Boolean = false,
    val convertTsToMp4: Boolean = false
)

object StreamBackoff {
    suspend fun wait(attempt: Int, isCancelled: () -> Boolean) {
        if (isCancelled()) return
        val base = (250L shl attempt.coerceIn(0, 5)).coerceAtMost(8000L)
        val jitter = (0..(base / 4).coerceAtLeast(1)).random()
        kotlinx.coroutines.delay(base + jitter)
    }
}

object StreamRequestHeaders {
    fun apply(
        builder: Request.Builder,
        url: String,
        referer: String,
        cookies: String,
        userAgent: String,
        extraHeaders: Map<String, String>
    ) {
        if (userAgent.isNotBlank()) builder.header("User-Agent", userAgent)
        if (referer.isNotBlank()) builder.header("Referer", referer)
        val cookieHeader = cookies.ifBlank {
            runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull() ?: ""
        }
        if (cookieHeader.isNotBlank()) builder.header("Cookie", cookieHeader)
        extraHeaders.forEach { (key, value) -> runCatching { builder.header(key, value) } }
    }
}
