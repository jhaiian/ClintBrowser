package com.jhaiian.clint.mediacapture

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhaiian.clint.R
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.downloads.formatElapsed
import com.jhaiian.clint.mediacapture.download.HlsPlaylistFetcher
import com.jhaiian.clint.mediacapture.download.SegmentSpec
import com.jhaiian.clint.mediacapture.download.StreamRequestHeaders
import com.jhaiian.clint.mediacapture.download.TrackKind
import com.jhaiian.clint.ui.ClintDialogStatusBarEffect
import com.jhaiian.clint.ui.listscreen.ListMenuItem
import com.jhaiian.clint.ui.listscreen.PopupShape
import com.jhaiian.clint.ui.theme.LocalClintColors
import com.jhaiian.clint.util.formatFileSize
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCaptureDialog(
    tabId: String,
    pageUrl: String,
    hideStatusBar: Boolean,
    hideSystemNavigation: Boolean,
    onDismiss: () -> Unit,
    onCopyLink: (String) -> Unit,
    onDownload: (DetectedMedia, List<DetectedMedia>, Long?, String?) -> Unit
) {
    val colors = LocalClintColors.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val detectedItems by MediaCaptureStore.observe(tabId).collectAsState()

    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val maxSheetHeight = if (isPortrait) {
        (configuration.screenHeightDp.dp * 0.6f).coerceAtLeast(360.dp)
    } else {
        (configuration.screenHeightDp.dp * 0.6f).coerceAtLeast(280.dp)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.popupBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.divider) }
    ) {
        ClintDialogStatusBarEffect(hideStatusBar, hideSystemNavigation)
        Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight)) {
            MediaCaptureHeader(detectedItems.size, onDismiss)
            HorizontalDivider(color = colors.popupStroke)
            if (detectedItems.isEmpty()) {
                MediaCaptureEmptyState()
            } else {
                val video = detectedItems.filter { it.kind == MediaKind.VIDEO }
                val audio = detectedItems.filter { it.kind == MediaKind.AUDIO }
                val subtitles = detectedItems.filter { it.kind == MediaKind.SUBTITLE }
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(bottom = 8.dp)) {
                    if (video.isNotEmpty()) {
                        item(key = "header_video") { MediaCaptureSectionHeader(stringResource(R.string.media_capture_section_video)) }
                        items(video, key = { it.id }) { media ->
                            MediaCaptureRow(media, pageUrl, onCopyLink) { estimatedBytes, containerExt -> onDownload(media, detectedItems, estimatedBytes, containerExt) }
                        }
                    }
                    if (audio.isNotEmpty()) {
                        item(key = "header_audio") { MediaCaptureSectionHeader(stringResource(R.string.media_capture_section_audio)) }
                        items(audio, key = { it.id }) { media ->
                            MediaCaptureRow(media, pageUrl, onCopyLink) { estimatedBytes, containerExt -> onDownload(media, detectedItems, estimatedBytes, containerExt) }
                        }
                    }
                    if (subtitles.isNotEmpty()) {
                        item(key = "header_subtitles") { MediaCaptureSectionHeader(stringResource(R.string.media_capture_section_subtitles)) }
                        items(subtitles, key = { it.id }) { media ->
                            MediaCaptureRow(media, pageUrl, onCopyLink) { estimatedBytes, containerExt -> onDownload(media, detectedItems, estimatedBytes, containerExt) }
                        }
                    }
                }
            }
        }
    }
}

fun pickPairedAudio(media: DetectedMedia, all: List<DetectedMedia>): DetectedMedia? {
    if (media.kind == MediaKind.VIDEO && media.hasAudio != false) return null
    val group = media.groupUrl ?: return null
    val sameGroup = all.filter { it.kind == MediaKind.AUDIO && it.groupUrl == group }
    if (sameGroup.isEmpty()) return null
    val ref = media.audioGroupRef
    val scoped = if (ref != null) sameGroup.filter { it.mediaGroupId == ref } else sameGroup
    val candidates = scoped.ifEmpty { sameGroup }
    return candidates.firstOrNull { it.isDefaultTrack } ?: candidates.first()
}

fun pickSoleSubtitle(media: DetectedMedia, all: List<DetectedMedia>): DetectedMedia? {
    val group = media.groupUrl ?: return null
    val subtitles = all.filter { it.kind == MediaKind.SUBTITLE && it.groupUrl == group }
    return subtitles.singleOrNull()
}

fun suggestMediaCaptureFilename(media: DetectedMedia, pageTitle: String, containerExtension: String? = null): String {
    val fromUrl = runCatching { Uri.parse(media.url).lastPathSegment }.getOrNull()
    if (!fromUrl.isNullOrBlank() && fromUrl.contains('.') && !fromUrl.endsWith(".m3u8") && !fromUrl.endsWith(".mpd")) {
        return fromUrl
    }
    val ext = when {
        media.format.equals("HLS", true) || media.format.equals("DASH", true) -> containerExtension ?: "mp4"
        else -> media.format.lowercase()
    }
    return "${sanitizeMediaCaptureFilenameBase(pageTitle)}.$ext"
}

private fun sanitizeMediaCaptureFilenameBase(title: String): String {
    val cleaned = title.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().take(80).trim()
    return cleaned.ifBlank { "media" }
}

fun isMediaCaptureManifestBased(media: DetectedMedia): Boolean =
    media.kind != MediaKind.SUBTITLE && media.groupUrl != null &&
        (media.format.equals("HLS", true) || media.format.equals("DASH", true))

@Composable
private fun MediaCaptureHeader(count: Int, onDismiss: () -> Unit) {
    val colors = LocalClintColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.media_capture_dialog_title),
                color = colors.popupText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (count > 0) {
                    stringResource(R.string.media_capture_count_label, count)
                } else {
                    stringResource(R.string.media_capture_scanning)
                },
                color = colors.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.media_capture_close),
                tint = colors.iconTint
            )
        }
    }
}

@Composable
private fun MediaCaptureEmptyState() {
    val colors = LocalClintColors.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.VideoLibrary,
            contentDescription = null,
            tint = colors.secondaryText.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        )
        Text(
            stringResource(R.string.media_capture_empty_state),
            color = colors.secondaryText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun MediaCaptureSectionHeader(text: String) {
    Text(
        text,
        color = LocalClintColors.current.secondaryText,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

private fun mediaKindIcon(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.VIDEO -> Icons.Filled.VideoFile
    MediaKind.AUDIO -> Icons.Filled.AudioFile
    MediaKind.SUBTITLE -> Icons.Filled.Subtitles
}

private fun formatBitrate(bitsPerSec: Long): String {
    val mbps = bitsPerSec / 1_000_000.0
    return if (mbps >= 1.0) {
        String.format(Locale.US, "%.1f Mbps", mbps)
    } else {
        String.format(Locale.US, "%.0f Kbps", bitsPerSec / 1000.0)
    }
}

@Composable
private fun mediaCaptureTitle(media: DetectedMedia): String {
    media.label?.takeIf { it.isNotBlank() }?.let { return it }
    if (media.width != null && media.height != null) return "${media.width}\u00D7${media.height}"
    val name = remember(media.url) { runCatching { Uri.parse(media.url).lastPathSegment }.getOrNull() }
    return name?.takeIf { it.isNotBlank() } ?: media.format
}

private fun isHlsManifestVariant(media: DetectedMedia): Boolean =
    media.kind != MediaKind.SUBTITLE && !media.isLive && media.format.equals("HLS", true) && media.groupUrl != null

private data class HlsSegmentEstimate(val segmentCount: Int, val estimatedBytes: Long?, val containerExtension: String?)

private fun fetchSegmentContentLength(
    url: String,
    pageUrl: String,
    userAgent: String,
    extraHeaders: Map<String, String>
): Long? = runCatching {
    val builder = okhttp3.Request.Builder().url(url).head()
    StreamRequestHeaders.apply(builder, url, pageUrl, "", userAgent, extraHeaders)
    ClintDownloadManager.httpClient.newCall(builder.build()).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
    }
}.getOrNull()

private fun sumByteRangeLengths(segments: List<SegmentSpec>): Long? {
    if (segments.isEmpty()) return null
    var total = 0L
    for (seg in segments) {
        val length = seg.byteRangeLength ?: return null
        total += length
    }
    return total
}

private fun sampleAverageContentLengthPerUrl(
    urls: List<String>,
    pageUrl: String,
    userAgent: String,
    extraHeaders: Map<String, String>
): Long? {
    if (urls.isEmpty()) return null
    val sampleCount = minOf(3, urls.size)
    val step = maxOf(1, urls.size / sampleCount)
    val indices = (0 until urls.size step step).take(sampleCount)
    var totalBytes = 0L
    var counted = 0
    for (idx in indices) {
        val size = fetchSegmentContentLength(urls[idx], pageUrl, userAgent, extraHeaders) ?: continue
        totalBytes += size
        counted++
    }
    return if (counted > 0) totalBytes / counted else null
}

private suspend fun fetchHlsSegmentEstimate(
    media: DetectedMedia,
    pageUrl: String,
    context: android.content.Context
): HlsSegmentEstimate? = withContext(Dispatchers.IO) {
    runCatching {
        val userAgent = android.webkit.WebSettings.getDefaultUserAgent(context)
        val kind = if (media.kind == MediaKind.AUDIO) TrackKind.AUDIO else TrackKind.VIDEO
        val track = HlsPlaylistFetcher.fetch(media.url, pageUrl, "", userAgent, kind, media.requestHeaders)
            ?: return@runCatching null
        val segmentCount = track.segments.size + if (track.initSegment != null) 1 else 0
        val duration = track.durationSeconds ?: media.durationSeconds
        val bandwidth = media.bandwidthBitsPerSec
        val byteRangeTotal = sumByteRangeLengths(track.segments)?.let { total ->
            total + (track.initSegment?.byteRangeLength ?: 0L)
        }
        val distinctUrls = track.segments.map { it.url }.distinct()
        val singleFileTotal = if (distinctUrls.size == 1 && byteRangeTotal != null) {
            fetchSegmentContentLength(distinctUrls[0], pageUrl, userAgent, media.requestHeaders)
        } else null
        val estimatedBytes = singleFileTotal ?: byteRangeTotal ?: if (duration != null && duration > 0.0 && bandwidth != null && bandwidth > 0L) {
            ((duration * bandwidth) / 8.0).toLong()
        } else {
            if (distinctUrls.size < track.segments.size) {
                sampleAverageContentLengthPerUrl(distinctUrls, pageUrl, userAgent, media.requestHeaders)
                    ?.let { it * distinctUrls.size }
            } else {
                sampleAverageContentLengthPerUrl(track.segments.map { it.url }, pageUrl, userAgent, media.requestHeaders)
                    ?.let { it * segmentCount }
            }
        }
        HlsSegmentEstimate(segmentCount, estimatedBytes, track.containerHintExtension)
    }.getOrNull()
}

@Composable
private fun hlsSegmentEstimate(media: DetectedMedia, pageUrl: String): HlsSegmentEstimate? {
    val context = LocalContext.current
    val state = produceState<HlsSegmentEstimate?>(initialValue = null, media.id, pageUrl) {
        value = fetchHlsSegmentEstimate(media, pageUrl, context)
    }
    return state.value
}

@Composable
private fun mediaCaptureSubtitle(media: DetectedMedia, estimate: HlsSegmentEstimate?): String {
    val parts = mutableListOf<String>()
    parts += media.format
    media.bandwidthBitsPerSec?.takeIf { it > 0L }?.let { parts += formatBitrate(it) }
    media.durationSeconds?.takeIf { it > 0.0 }?.let { parts += formatElapsed(it.toLong()) }
    if (estimate != null) {
        parts += stringResource(R.string.media_capture_segments_count, estimate.segmentCount)
        estimate.estimatedBytes?.takeIf { it > 0L }?.let {
            parts += stringResource(R.string.media_capture_estimated_size, formatFileSize(it))
        }
    }
    media.sizeBytes?.takeIf { it > 0L }?.let { parts += formatFileSize(it) }
    if (media.isLive) parts += stringResource(R.string.media_capture_live_badge)
    if (media.kind == MediaKind.VIDEO && media.hasAudio == false) {
        parts += stringResource(R.string.media_capture_no_audio_badge)
    }
    return parts.joinToString("  \u2022  ")
}

private fun startExternalStream(context: android.content.Context, media: DetectedMedia) {
    val mime = media.mimeType ?: when (media.kind) {
        MediaKind.AUDIO -> "audio/*"
        MediaKind.SUBTITLE -> "text/*"
        MediaKind.VIDEO -> "video/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(media.url), mime)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, media.url)) }
}

private fun shareDownloadLink(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.link_share))) }
}

@Composable
private fun MediaCaptureRow(media: DetectedMedia, pageUrl: String, onCopyLink: (String) -> Unit, onDownload: (Long?, String?) -> Unit) {
    val colors = LocalClintColors.current
    val context = LocalContext.current
    val estimate = if (isHlsManifestVariant(media)) hlsSegmentEstimate(media, pageUrl) else null
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardBackground)
            .clickable(onClick = { onDownload(estimate?.estimatedBytes, estimate?.containerExtension) })
            .padding(start = 14.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                mediaKindIcon(media.kind),
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(22.dp)
            )
            if (media.kind == MediaKind.VIDEO && media.hasAudio == false) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(colors.cardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = stringResource(R.string.media_capture_no_audio_badge),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                mediaCaptureTitle(media),
                color = colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis
            )
            Text(
                mediaCaptureSubtitle(media, estimate),
                color = colors.secondaryText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.media_capture_more_options),
                    tint = colors.iconTint.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = PopupShape,
                containerColor = colors.popupBackground,
                border = BorderStroke(1.dp, colors.popupStroke)
            ) {
                ListMenuItem(Icons.Filled.PlayCircle, stringResource(R.string.media_capture_menu_stream), checked = false) {
                    menuExpanded = false
                    startExternalStream(context, media)
                }
                ListMenuItem(Icons.Filled.Download, stringResource(R.string.media_capture_download), checked = false) {
                    menuExpanded = false
                    onDownload(estimate?.estimatedBytes, estimate?.containerExtension)
                }
                ListMenuItem(Icons.Filled.ContentCopy, stringResource(R.string.media_capture_menu_copy_link), checked = false) {
                    menuExpanded = false
                    onCopyLink(media.url)
                }
                ListMenuItem(Icons.Filled.Share, stringResource(R.string.media_capture_menu_share_link), checked = false) {
                    menuExpanded = false
                    shareDownloadLink(context, media.url)
                }
            }
        }
    }
}
