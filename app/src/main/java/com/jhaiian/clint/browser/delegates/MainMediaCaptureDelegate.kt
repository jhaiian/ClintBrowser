package com.jhaiian.clint.browser.delegates

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.downloads.DownloadConflictDialogRequest
import com.jhaiian.clint.downloads.DownloadFileHelper
import com.jhaiian.clint.downloads.DownloadItem
import com.jhaiian.clint.downloads.DownloadRequestSubmission
import com.jhaiian.clint.downloads.DownloadStatus
import com.jhaiian.clint.downloads.DownloadsActivity
import com.jhaiian.clint.mediacapture.DetectedMedia
import com.jhaiian.clint.mediacapture.MEDIA_CAPTURE_CONVERT_TS_TO_MP4_DEFAULT
import com.jhaiian.clint.mediacapture.MEDIA_CAPTURE_CONVERT_TS_TO_MP4_PREF
import com.jhaiian.clint.mediacapture.MediaCaptureDialog
import com.jhaiian.clint.mediacapture.MediaKind
import com.jhaiian.clint.mediacapture.download.StreamContainerFormat
import com.jhaiian.clint.mediacapture.download.StreamDownloadRequest
import com.jhaiian.clint.mediacapture.isMediaCaptureManifestBased
import com.jhaiian.clint.mediacapture.pickPairedAudio
import com.jhaiian.clint.mediacapture.pickSoleSubtitle
import com.jhaiian.clint.mediacapture.suggestMediaCaptureFilename
import com.jhaiian.clint.settings.downloads.DownloadSettingsKeys
import com.jhaiian.clint.ui.showClintSnackbar
import com.jhaiian.clint.ui.theme.ClintComposeTheme
import com.jhaiian.clint.util.formatFileSize

internal fun MainActivity.mountMediaCaptureDialog() {
    val tabId = tabManager.activeTab?.id ?: return
    val pageUrl = tabManager.activeTab?.webView?.url ?: ""
    val pageTitle = tabManager.activeTab?.title?.takeIf { it.isNotBlank() } ?: pageUrl
    val theme = prefs.getString("app_theme", "system") ?: "system"
    val hideStatusBar = prefs.getBoolean("hide_status_bar", false)
    val hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)

    overlayContent = {
        ClintComposeTheme(theme = theme) {
            MediaCaptureDialog(
                tabId = tabId,
                hideStatusBar = hideStatusBar,
                hideSystemNavigation = hideSystemNavigation,
                onDismiss = { overlayContent = null },
                onCopyLink = { text -> copyMediaCaptureLink(text) },
                onDownload = { media, allItems, estimatedBytes, containerExtension ->
                    showMediaCaptureDownloadDialog(media, pageUrl, pageTitle, allItems, estimatedBytes, containerExtension)
                }
            )
        }
    }
}

private fun MainActivity.copyMediaCaptureLink(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.media_capture_link_clip_label), text))
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, getString(R.string.media_capture_link_copied), Toast.LENGTH_SHORT).show()
    }
}

private fun MainActivity.showMediaCaptureDownloadDialog(
    media: DetectedMedia,
    pageUrl: String,
    pageTitle: String,
    allItems: List<DetectedMedia>,
    estimatedBytes: Long?,
    containerExtension: String?
) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val userAgent = android.webkit.WebSettings.getDefaultUserAgent(this)
    val isManifestBased = isMediaCaptureManifestBased(media)
    val isAudioPrimary = media.kind == MediaKind.AUDIO
    val convertTsToMp4 = prefs.getBoolean(MEDIA_CAPTURE_CONVERT_TS_TO_MP4_PREF, MEDIA_CAPTURE_CONVERT_TS_TO_MP4_DEFAULT) && !isAudioPrimary
    val pairedAudio = if (isManifestBased && !isAudioPrimary) pickPairedAudio(media, allItems) else null
    val realExtension = if (isManifestBased) {
        if (pairedAudio != null || containerExtension == null || containerExtension == "mp4" || convertTsToMp4) "mp4" else containerExtension
    } else null
    val filename = suggestMediaCaptureFilename(media, pageTitle, realExtension)
    val knownLengthBytes = media.sizeBytes?.takeIf { it > 0L }
    val fileSizeDisplayOverride = if (isManifestBased && knownLengthBytes == null) {
        estimatedBytes?.takeIf { it > 0L }?.let { getString(R.string.download_dialog_file_size_estimated, formatFileSize(it)) }
    } else null

    mountDownloadRequestDialog(
        url = media.url,
        onCopyLink = { copyMediaCaptureLink(media.url) },
        initialFilename = filename,
        contentLengthBytes = knownLengthBytes ?: estimatedBytes ?: -1L,
        fileSizeDisplayOverride = fileSizeDisplayOverride,
        fetchUrl = if (!isManifestBased && knownLengthBytes == null) media.url else null,
        fetchUserAgent = userAgent,
        checkStorage = true,
        showOptions = true,
        showStorageInfo = true,
        showSplitAndMultithreading = !isManifestBased,
        showConcurrentSegments = isManifestBased,
        initialConcurrentSegments = prefs.getInt(DownloadSettingsKeys.PREF_STREAM_CONCURRENT_SEGMENTS, DownloadSettingsKeys.DEFAULT_STREAM_CONCURRENT_SEGMENTS),
        initialLocationMode = prefs.getString(DownloadSettingsKeys.PREF_DOWNLOAD_LOCATION_MODE, DownloadSettingsKeys.MODE_DEFAULT) ?: DownloadSettingsKeys.MODE_DEFAULT,
        initialCustomUri = prefs.getString(DownloadSettingsKeys.PREF_DOWNLOAD_CUSTOM_URI, null)?.let { Uri.parse(it) },
        initialRetryEnabled = prefs.getBoolean(DownloadSettingsKeys.PREF_RETRY_ENABLED, DownloadSettingsKeys.DEFAULT_RETRY_ENABLED),
        initialUnmeteredOnly = prefs.getBoolean(DownloadSettingsKeys.PREF_UNMETERED_ONLY, DownloadSettingsKeys.DEFAULT_UNMETERED_ONLY),
        initialSplitParts = prefs.getInt(DownloadSettingsKeys.PREF_SPLIT_PARTS, DownloadSettingsKeys.DEFAULT_SPLIT_PARTS),
        initialMultithreadingParts = prefs.getInt(DownloadSettingsKeys.PREF_MULTITHREADING_PARTS, DownloadSettingsKeys.DEFAULT_MULTITHREADING_PARTS),
        initialSpeedLimitAmount = prefs.getInt(DownloadSettingsKeys.PREF_SPEED_LIMIT_AMOUNT, DownloadSettingsKeys.DEFAULT_SPEED_LIMIT_AMOUNT),
        initialSpeedLimitUnit = prefs.getString(DownloadSettingsKeys.PREF_SPEED_LIMIT_UNIT, com.jhaiian.clint.downloads.DEFAULT_SPEED_LIMIT_UNIT) ?: com.jhaiian.clint.downloads.DEFAULT_SPEED_LIMIT_UNIT,
        onSubmit = { submission, dismiss, onRename ->
            fun proceed() {
                if (isManifestBased) {
                    if (DownloadFileHelper.isCustomLocationAccessible(this, submission.locationMode, submission.customLocationUri)) {
                        showClintSnackbar(
                            message = getString(R.string.toast_downloading, submission.filename),
                            actionLabel = getString(R.string.download_started_view_action),
                            onAction = { DownloadsActivity.open(this) }
                        )
                    }
                    dismiss()
                    enqueueMediaCaptureStream(media, allItems, pageUrl, userAgent, submission, knownLengthBytes ?: estimatedBytes ?: 0L, convertTsToMp4)
                } else {
                    if (DownloadFileHelper.isCustomLocationAccessible(this, submission.locationMode, submission.customLocationUri)) {
                        showClintSnackbar(
                            message = getString(R.string.toast_downloading, submission.filename),
                            actionLabel = getString(R.string.download_started_view_action),
                            onAction = { DownloadsActivity.open(this) }
                        )
                    }
                    initiateDownload(
                        media.url, submission.filename,
                        mediaCaptureRequestHeader(media, "User-Agent") ?: userAgent,
                        mediaCaptureRequestHeader(media, "Referer") ?: pageUrl,
                        mediaCaptureCookies(media.url),
                        submission.retryEnabled, submission.unmeteredOnly, submission.splitParts, submission.multithreadingParts, submission.speedLimitBytesPerSec,
                        submission.locationMode, submission.customLocationUri, submission.categorizeEnabled, submission.scheduledStartAtMillis,
                        onDismiss = dismiss,
                        onRename = onRename
                    )
                }
            }

            val existing = findExistingMediaCaptureDownload(media.url)
            if (existing != null) {
                val isPending = existing.status in DownloadStatus.NOT_FINISHED
                uiState.conflictDialogRequest = DownloadConflictDialogRequest(
                    onAddDuplicate = { proceed() },
                    onOverride = {
                        ClintDownloadManager.remove(this, existing.id, deleteFile = true)
                        proceed()
                    },
                    onRename = onRename,
                    onUpdateLink = if (isPending) {
                        {
                            ClintDownloadManager.updateDownloadUrl(existing.id, media.url)
                            dismiss()
                            showClintSnackbar(message = getString(R.string.media_capture_duplicate_link_updated))
                        }
                    } else null
                )
            } else {
                proceed()
            }
        }
    )
}

private fun MainActivity.findExistingMediaCaptureDownload(mediaUrl: String): DownloadItem? =
    ClintDownloadManager.downloadsFlow.value.firstOrNull {
        it.status != DownloadStatus.FAILED &&
            (it.url == mediaUrl || it.streamVideoUrl == mediaUrl || it.streamAudioUrl == mediaUrl)
    }

private fun MainActivity.enqueueMediaCaptureStream(
    media: DetectedMedia,
    allItems: List<DetectedMedia>,
    pageUrl: String,
    userAgent: String,
    submission: DownloadRequestSubmission,
    estimatedTotalBytes: Long,
    convertTsToMp4: Boolean
) {
    val isDash = media.format.equals("DASH", true)
    val isAudioPrimary = media.kind == MediaKind.AUDIO
    val audio = if (!isAudioPrimary) pickPairedAudio(media, allItems) else null
    val subtitle = pickSoleSubtitle(media, allItems)
    val headers = media.requestHeaders.ifEmpty { audio?.requestHeaders ?: emptyMap() }

    val request = StreamDownloadRequest(
        format = if (isDash) StreamContainerFormat.DASH else StreamContainerFormat.HLS,
        videoUrl = if (!isAudioPrimary) media.url else null,
        audioUrl = audio?.url ?: (if (isAudioPrimary) media.url else null),
        subtitleUrl = subtitle?.url,
        pageUrl = pageUrl,
        referer = pageUrl,
        cookies = "",
        userAgent = userAgent,
        filename = submission.filename,
        headers = headers,
        estimatedTotalBytes = estimatedTotalBytes,
        speedLimitBytesPerSec = submission.speedLimitBytesPerSec,
        concurrentSegments = submission.concurrentSegments,
        noAudio = !isAudioPrimary && media.hasAudio == false,
        isLive = media.isLive,
        convertTsToMp4 = convertTsToMp4
    )
    ClintDownloadManager.enqueueStream(
        context = this,
        request = request,
        videoWidth = if (!isAudioPrimary) media.width else null,
        videoHeight = if (!isAudioPrimary) media.height else null,
        videoBandwidth = if (!isAudioPrimary) media.bandwidthBitsPerSec else null,
        audioBandwidth = audio?.bandwidthBitsPerSec,
        primaryIsAudio = isAudioPrimary,
        locationMode = submission.locationMode,
        customLocationUri = submission.customLocationUri,
        categorizeEnabled = submission.categorizeEnabled,
        videoRepresentationId = if (!isAudioPrimary) media.representationId else null,
        audioRepresentationId = audio?.representationId ?: (if (isAudioPrimary) media.representationId else null)
    )
}

private fun mediaCaptureRequestHeader(media: DetectedMedia, name: String): String? =
    media.requestHeaders.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.takeIf { it.isNotBlank() }

private fun mediaCaptureCookies(url: String): String =
    runCatching { android.webkit.CookieManager.getInstance().getCookie(url) }.getOrNull().orEmpty()
