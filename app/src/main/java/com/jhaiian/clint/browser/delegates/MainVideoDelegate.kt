package com.jhaiian.clint.browser.delegates

import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.webkit.WebView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.media.BackgroundPlayback
import com.jhaiian.clint.browser.video.VIDEO_TRACK_AUTO
import com.jhaiian.clint.browser.video.VIDEO_TRACK_OFF
import com.jhaiian.clint.browser.video.VideoCandidate
import com.jhaiian.clint.browser.video.VideoFit
import com.jhaiian.clint.browser.video.VideoPipAction
import com.jhaiian.clint.browser.video.VideoPlayerController
import com.jhaiian.clint.browser.video.VideoPlayerSettings
import com.jhaiian.clint.browser.video.VideoTrackOption
import com.jhaiian.clint.browser.video.canEnterVideoPip
import com.jhaiian.clint.browser.video.registerVideoPipReceiver
import com.jhaiian.clint.browser.video.setVideoBrightness
import com.jhaiian.clint.browser.video.toggleVideoRotation
import com.jhaiian.clint.browser.video.videoBrightness
import com.jhaiian.clint.browser.video.videoPipParams
import com.jhaiian.clint.browser.webview.loadJsAsset
import com.jhaiian.clint.mediacapture.DetectedMedia
import com.jhaiian.clint.mediacapture.MediaCaptureStore
import com.jhaiian.clint.mediacapture.MediaKind
import com.jhaiian.clint.mediacapture.VideoPreviewActivity
import com.jhaiian.clint.mediacapture.audioTrackName
import com.jhaiian.clint.mediacapture.compatibleAudioTracks
import com.jhaiian.clint.mediacapture.trackLanguageName
import com.jhaiian.clint.tabs.BrowserTab
import org.json.JSONArray
import org.json.JSONObject

private val CANDIDATE_ID = Regex("[a-z0-9-]{1,48}")
private const val CAPTURE_PREFIX = "cap:"
private const val SUBTITLE_PREFIX = "sub:"

internal fun MainActivity.addVideoTakeoverScript(tab: BrowserTab) {
    removeVideoTakeoverScript(tab)
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    if (!VideoPlayerSettings.isEnabled(this)) return
    val code = loadJsAsset("video_takeover.js")
        .replace("var TAKE_SITE = true;", "var TAKE_SITE = ${VideoPlayerSettings.isSiteFullscreen(this)};")
    videoTakeoverScriptHandlers[tab.id] = WebViewCompat.addDocumentStartJavaScript(tab.webView, code, setOf("*"))
}

internal fun MainActivity.removeVideoTakeoverScript(tab: BrowserTab) {
    videoTakeoverScriptHandlers.remove(tab.id)?.remove()
}

internal fun MainActivity.applyVideoTakeoverSettings() {
    uiState.video.floatingButton = VideoPlayerSettings.isFloatingButton(this)
    val key = "${VideoPlayerSettings.isEnabled(this)}|${VideoPlayerSettings.isSiteFullscreen(this)}"
    if (key == appliedVideoTakeoverKey) return
    appliedVideoTakeoverKey = key
    tabManager.tabs.forEach { addVideoTakeoverScript(it) }
    if (!VideoPlayerSettings.isEnabled(this)) uiState.video.candidate = null
}

private fun MainActivity.isActiveWebView(webView: WebView): Boolean =
    tabManager.activeTab?.webView === webView

internal fun MainActivity.onVideoCandidates(webView: WebView, json: String?) {
    if (!isActiveWebView(webView)) return
    val list = runCatching { JSONArray(json ?: "[]") }.getOrNull() ?: JSONArray()
    val location = IntArray(2).also { webView.getLocationInWindow(it) }
    val viewRight = location[0] + webView.width
    val viewBottom = location[1] + webView.height
    var best: VideoCandidate? = null
    var bestArea = 0L
    for (i in 0 until list.length()) {
        val item = list.optJSONObject(i) ?: continue
        val id = item.optString("id")
        val rect = item.optJSONArray("r") ?: continue
        if (!CANDIDATE_ID.matches(id) || rect.length() != 4) continue
        val candidate = VideoCandidate(
            tabManager.activeTab?.id,
            id,
            location[0] + rect.optInt(0),
            location[1] + rect.optInt(1),
            location[0] + rect.optInt(2),
            location[1] + rect.optInt(3)
        )
        val visibleWidth = (minOf(candidate.right, viewRight) - maxOf(candidate.left, location[0])).coerceAtLeast(0)
        val visibleHeight = (minOf(candidate.bottom, viewBottom) - maxOf(candidate.top, location[1])).coerceAtLeast(0)
        val area = visibleWidth.toLong() * visibleHeight
        if (area > bestArea) {
            best = candidate
            bestArea = area
        }
    }
    uiState.video.candidate = best
}

internal fun MainActivity.onVideoState(webView: WebView, json: String?) {
    if (!isActiveWebView(webView) || !uiState.isFullscreen) return
    val o = runCatching { JSONObject(json ?: return) }.getOrNull() ?: return
    val video = uiState.video
    val wasActive = video.active
    val wasPaused = video.paused
    video.paused = o.optBoolean("p", true)
    video.positionMs = (o.optDouble("t", 0.0) * 1000).toLong()
    val duration = o.optDouble("d", -1.0)
    video.durationMs = if (duration > 0) (duration * 1000).toLong() else -1L
    video.bufferedMs = (o.optDouble("b", 0.0) * 1000).toLong()
    video.rate = o.optDouble("r", 1.0).toFloat()
    video.videoWidth = o.optInt("w")
    video.videoHeight = o.optInt("h")
    video.active = true
    video.subtitles = pageSubtitleOptions(o.optJSONArray("tt"))
    updateCaptureTrackOptions()
    if (video.paused) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (!wasActive) {
        video.candidate = null
        fullscreenWebView = webView
        video.fit = VideoPlayerSettings.fit(this)
        if (o.optString("f") != video.fit.key) videoCommand("fit", "'${video.fit.key}'")
        if (video.pseudo && !video.inPip && video.videoWidth > 0 && video.videoHeight > 0) {
            requestedOrientation = if (video.videoHeight > video.videoWidth) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
        if (video.pipPending) {
            video.pipPending = false
            enterVideoPip()
        }
    } else if (video.inPip && wasPaused != video.paused) {
        runCatching { setPictureInPictureParams(videoPipParams(video)) }
    }
}

internal fun MainActivity.onVideoDetached(webView: WebView) {
    if (!isActiveWebView(webView)) return
    uiState.video.active = false
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (uiState.video.pseudo) exitFullscreen()
}

internal fun MainActivity.onPageFullscreenHidden() {
    val video = uiState.video
    val pipActive = video.inPip || android.os.SystemClock.elapsedRealtime() - video.pipRequestedAt < 3000
    if (fullscreenView != null && video.active && pipActive) {
        fullscreenCallback = null
        fullscreenView?.let { fullscreenContainerView.removeView(it) }
        fullscreenView = null
        video.pseudo = true
        runVideoScript("pin()")
        if (video.resumeAfterPip) {
            video.resumeAfterPip = false
            window.decorView.postDelayed({ if (uiState.video.pseudo) setVideoPaused(false) }, 400)
        }
        return
    }
    exitFullscreen()
}

internal fun MainActivity.refreshVideoCandidates(webView: WebView) {
    uiState.video.candidate = null
    webView.evaluateJavascript("window.__clintVt&&window.__clintVt.refresh()", null)
}

internal fun MainActivity.onVideoTakeoverEnded() {
    (fullscreenWebView ?: tabManager.activeTab?.webView)
        ?.evaluateJavascript("window.__clintVt&&window.__clintVt.release()", null)
    fullscreenWebView = null
    uiState.video.reset()
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
}

internal fun MainActivity.isVideoFullscreen(): Boolean = fullscreenView != null || uiState.video.pseudo

private fun MainActivity.runVideoScript(call: String) {
    (fullscreenWebView ?: tabManager.activeTab?.webView)
        ?.evaluateJavascript("window.__clintVt&&window.__clintVt.$call", null)
}

internal fun MainActivity.videoCommand(name: String, arg: String = "0") {
    runVideoScript("cmd('$name',$arg)")
}

internal fun MainActivity.setVideoPaused(paused: Boolean) {
    uiState.video.paused = paused
    if (uiState.video.inPip) runCatching { setPictureInPictureParams(videoPipParams(uiState.video)) }
    videoCommand(if (paused) "pause" else "play")
}

internal fun MainActivity.handleVideoTakeoverPlayback(tab: BrowserTab, action: String): Boolean {
    if (!uiState.video.active || tabManager.activeTab?.id != tab.id) return false
    when (action) {
        "play" -> setVideoPaused(false)
        "pause" -> setVideoPaused(true)
        else -> return false
    }
    return true
}

internal fun MainActivity.seekVideo(positionMs: Long) {
    val target = positionMs.coerceAtLeast(0L)
    uiState.video.positionMs = target
    videoCommand("seek", (target / 1000.0).toString())
}

internal fun MainActivity.skipVideo(seconds: Int) {
    val video = uiState.video
    val target = video.positionMs + seconds * 1000L
    video.positionMs = if (video.durationMs > 0) target.coerceIn(0L, video.durationMs) else target.coerceAtLeast(0L)
    videoCommand("skip", seconds.toString())
}

internal fun MainActivity.setVideoRate(rate: Float) {
    uiState.video.rate = rate
    videoCommand("rate", rate.toString())
}

internal fun MainActivity.setVideoFit(fit: VideoFit) {
    uiState.video.fit = fit
    VideoPlayerSettings.saveFit(this, fit)
    videoCommand("fit", "'${fit.key}'")
}

internal fun MainActivity.enterVideoTakeover(pip: Boolean) {
    val candidate = uiState.video.candidate ?: return
    val webView = tabManager.activeTab?.webView ?: return
    if (isVideoFullscreen()) return
    val video = uiState.video
    video.pseudo = true
    video.pipPending = pip
    fullscreenWebView = webView
    uiState.isFullscreen = true
    updateMainContentInsets()
    val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (!pip) {
        requestedOrientation = if (candidate.bottom - candidate.top > candidate.right - candidate.left) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    webView.evaluateJavascript("window.__clintVt&&window.__clintVt.enter('${candidate.id}')", null)
    window.decorView.postDelayed({
        if (uiState.video.pseudo && !uiState.video.active) exitFullscreen()
    }, 3000)
}

internal fun MainActivity.enterVideoPip() {
    if (!canEnterVideoPip()) return
    uiState.video.pipRequestedAt = android.os.SystemClock.elapsedRealtime()
    uiState.video.resumeAfterPip = uiState.video.active && !uiState.video.paused
    runCatching { enterPictureInPictureMode(videoPipParams(uiState.video)) }
}

internal fun MainActivity.onVideoPipModeChanged(inPip: Boolean) {
    uiState.video.inPip = inPip
    if (inPip) {
        if (videoPipReceiver == null) {
            videoPipReceiver = registerVideoPipReceiver { action ->
                when (action) {
                    VideoPipAction.PLAY -> setVideoPaused(false)
                    VideoPipAction.PAUSE -> setVideoPaused(true)
                    VideoPipAction.REWIND -> skipVideo(-VideoPlayerSettings.skipSeconds(this))
                    VideoPipAction.FORWARD -> skipVideo(VideoPlayerSettings.skipSeconds(this))
                }
                runCatching { setPictureInPictureParams(videoPipParams(uiState.video)) }
            }
        }
        return
    }
    releaseVideoPipReceiver()
    scheduleVideoPipDismissCheck()
}

internal fun MainActivity.scheduleVideoPipDismissCheck() {
    window.decorView.postDelayed({
        if (isVideoFullscreen() && !isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            setVideoPaused(true)
            exitFullscreen()
        }
    }, 500)
}

internal fun MainActivity.releaseVideoPipReceiver() {
    videoPipReceiver?.let { runCatching { unregisterReceiver(it) } }
    videoPipReceiver = null
}

internal fun MainActivity.onVideoUserLeaveHint() {
    val video = uiState.video
    if (uiState.isFullscreen && video.active && !video.paused && VideoPlayerSettings.isAutoPip(this)) enterVideoPip()
}

internal fun MainActivity.playVideoInBackground() {
    moveTaskToBack(true)
}

internal fun MainActivity.videoPlayerController(): VideoPlayerController = object : VideoPlayerController {
    override val title: String get() = tabManager.activeTab?.webView?.title.orEmpty()
    override val canPip: Boolean = canEnterVideoPip()
    override val canBackground: Boolean get() = BackgroundPlayback.isEnabled(prefs)
    override fun exit() = exitFullscreen()
    override fun setPaused(paused: Boolean) = setVideoPaused(paused)
    override fun seek(positionMs: Long) = seekVideo(positionMs)
    override fun skip(seconds: Int) = skipVideo(seconds)
    override fun setRate(rate: Float) = setVideoRate(rate)
    override fun setFit(fit: VideoFit) = setVideoFit(fit)
    override fun enterPip() = enterVideoPip()
    override fun playInBackground() = playVideoInBackground()
    override fun toggleRotation() = toggleVideoRotation()
    override fun brightness(): Float = videoBrightness()
    override fun setBrightness(value: Float) = setVideoBrightness(value)
    override fun selectQuality(id: String) {
        if (id.startsWith(CAPTURE_PREFIX)) handOffToNativePlayer(id.removePrefix(CAPTURE_PREFIX), null, autoQuality = false)
    }
    override fun selectAudio(id: String) {
        if (!id.startsWith(CAPTURE_PREFIX)) return
        val video = captureVideoGroup().firstOrNull() ?: return
        handOffToNativePlayer(video.id, id.removePrefix(CAPTURE_PREFIX), autoQuality = true)
    }
    override fun selectSubtitle(id: String) {
        val index = if (id == VIDEO_TRACK_OFF) -1 else id.removePrefix(SUBTITLE_PREFIX).toIntOrNull() ?: return
        uiState.video.subtitles = uiState.video.subtitles.map { it.copy(selected = it.id == id) }
        videoCommand("sub", index.toString())
    }
}

private fun MainActivity.pageSubtitleOptions(list: JSONArray?): List<VideoTrackOption> {
    if (list == null || list.length() == 0) return emptyList()
    val items = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
    val off = VideoTrackOption(VIDEO_TRACK_OFF, getString(R.string.video_player_off), items.none { it.optBoolean("on") })
    return listOf(off) + items.mapIndexed { n, item ->
        VideoTrackOption(
            SUBTITLE_PREFIX + item.optInt("i"),
            trackLanguageName(item.optString("l"), item.optString("g")) ?: getString(R.string.video_player_track, n + 1),
            item.optBoolean("on")
        )
    }
}

private fun MainActivity.captureVideoGroup(): List<DetectedMedia> {
    val tabId = tabManager.activeTab?.id ?: return emptyList()
    val pageSeconds = uiState.video.durationMs / 1000.0
    val videos = MediaCaptureStore.observe(tabId).value.filter { media ->
        val seconds = media.durationSeconds
        media.kind == MediaKind.VIDEO && (seconds == null || seconds <= 0.0 || pageSeconds <= 0.0 || kotlin.math.abs(seconds - pageSeconds) < 3.0)
    }
    return videos.groupBy { it.groupUrl.orEmpty() }.values.maxByOrNull { it.size }.orEmpty()
        .sortedWith(compareByDescending<DetectedMedia> { it.height ?: 0 }.thenByDescending { it.bandwidthBitsPerSec ?: 0L })
}

private fun MainActivity.captureQualityLabel(media: DetectedMedia): String =
    media.height?.takeIf { it > 0 }?.let { getString(R.string.video_player_quality_value, it) }
        ?: media.bandwidthBitsPerSec?.takeIf { it > 0 }?.let { getString(R.string.video_player_bitrate, (it / 1000).toInt()) }
        ?: media.label?.takeIf { it.isNotBlank() }
        ?: media.format

private fun MainActivity.updateCaptureTrackOptions() {
    val video = uiState.video
    val tabId = tabManager.activeTab?.id ?: return
    val group = captureVideoGroup()
    val auto = getString(R.string.video_player_auto)
    video.qualities = if (group.size < 2) emptyList() else {
        listOf(VideoTrackOption(VIDEO_TRACK_AUTO, auto, true)) +
            group.map { VideoTrackOption(CAPTURE_PREFIX + it.id, captureQualityLabel(it), false) }
    }
    val audios = group.firstOrNull()?.let { compatibleAudioTracks(it, MediaCaptureStore.observe(tabId).value) }.orEmpty()
    video.audioTracks = if (audios.size < 2) emptyList() else {
        listOf(VideoTrackOption(VIDEO_TRACK_AUTO, auto, true)) +
            audios.mapIndexed { n, audio -> VideoTrackOption(CAPTURE_PREFIX + audio.id, audioTrackName(audio) ?: getString(R.string.video_player_track, n + 1), false) }
    }
}

private fun MainActivity.handOffToNativePlayer(videoId: String, audioId: String?, autoQuality: Boolean) {
    val tabId = tabManager.activeTab?.id ?: return
    val position = uiState.video.positionMs
    setVideoPaused(true)
    exitFullscreen()
    VideoPreviewActivity.start(this, tabId, videoId, audioId, position, autoQuality)
}
