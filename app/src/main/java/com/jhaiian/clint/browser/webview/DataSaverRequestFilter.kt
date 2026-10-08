package com.jhaiian.clint.browser.webview

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.jhaiian.clint.settings.datasaver.DataSaverSiteException
import com.jhaiian.clint.settings.datasaver.DataSaverMode
import java.io.ByteArrayInputStream

internal object DataSaverRequestFilter {

    private val fontExtensions = listOf(".woff2", ".woff", ".ttf", ".otf", ".eot")

    private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif", ".bmp", ".svg", ".ico", ".heic")

    private val videoExtensions = listOf(".mp4", ".m4v", ".webm", ".mkv", ".mov", ".ogv", ".3gp", ".f4v", ".m3u8", ".mpd")

    private val videoSegmentExtensions = listOf(".m4s")

    private val scriptExtensions = listOf(".js", ".mjs", ".cjs")

    private val scriptPathSuffixes = listOf("/gtag/js", "/gtm.js", "/analytics.js", "/adsbygoogle.js")

    private val styleExtensions = listOf(".css")

    private val iconFontHints = listOf(
        "icon", "awesome", "glyph", "symbol", "material", "fa-", "fa5", "fa6", "dashicons", "bootstrap"
    )

    private val allowedFrameHosts = listOf(
        "hcaptcha.com",
        "recaptcha.net",
        "challenges.cloudflare.com",
        "stripe.com",
        "paypal.com",
        "accounts.google.com",
        "appleid.apple.com",
        "login.microsoftonline.com",
        "login.live.com"
    )

    private val allowedFramePathHints = listOf("/recaptcha/", "/turnstile/")

    private val adFrameHostHints = listOf(
        "doubleclick", "googlesyndication", "googleadservices", "adservice", "adsystem", "adnxs",
        "taboola", "outbrain", "popads", "popcash", "propeller", "exoclick", "juicyads", "trafficjunky",
        "adsterra", "clickadu", "hilltopads", "revcontent", "mgid", "criteo", "pubmatic", "rubiconproject"
    )

    private val playerHostHints = listOf(
        "embed", "player", "stream", "vidsrc", "vidplay", "vidcloud", "vidmoly", "vidhide", "vidguard",
        "vidlink", "vidfast", "videasy", "filemoon", "streamwish", "streamtape", "doodstream", "dood.",
        "mixdrop", "voe.", "upstream", "primesrc", "2embed", "multiembed", "autoembed", "superembed",
        "moviesapi", "rabbitstream", "megacloud", "youtube.com", "youtube-nocookie.com", "youtu.be",
        "vimeo.com", "dailymotion.com", "twitch.tv", "streamable.com", "jwplatform.com", "jwpsrv.com",
        "brightcove", "wistia"
    )

    private val playerPathHints = listOf(
        "/embed", "/player", "/e/", "/v/", "/play", "/watch", "/stream", "/video", "/iframe", "/tv/", "/movie/"
    )

    fun intercept(context: Context, prefs: SharedPreferences, request: WebResourceRequest, pageUrl: String?): WebResourceResponse? {
        if (!DataSaverMode.isActive(prefs)) return null
        val blockImages = prefs.getBoolean("data_saver_disable_images", false)
        val blockFonts = prefs.getBoolean("data_saver_block_fonts", true)
        val blockFrames = prefs.getBoolean("data_saver_block_frames", true)
        val blockVideo = prefs.getBoolean("data_saver_block_video", false)
        val blockScripts = prefs.getBoolean("data_saver_block_scripts", false)
        val blockCss = prefs.getBoolean("data_saver_block_css", false)
        if (!blockImages && !blockFonts && !blockFrames && !blockVideo && !blockScripts && !blockCss) return null
        if (request.isForMainFrame) return null
        if (DataSaverSiteException.isExceptedUrl(context, pageUrl)) return null
        val url = request.url ?: return null
        val scheme = url.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null

        if (blockVideo) {
            val path = url.path?.lowercase().orEmpty()
            if (videoExtensions.any { path.endsWith(it) }) {
                return emptyResponse("video/mp4", 404, "Blocked")
            }
            if (videoSegmentExtensions.any { path.endsWith(it) }) {
                return emptyResponse("video/mp4", 404, "Blocked")
            }
        }

        if (blockScripts && isScript(request, url)) {
            return emptyResponse("application/javascript", 200, "OK")
        }
        if (blockCss && isStyleSheet(request, url)) {
            return emptyResponse("text/css", 200, "OK")
        }
        if (blockImages && isImage(request, url)) {
            return emptyResponse("image/gif", 404, "Blocked")
        }
        if (blockFonts && isHeavyFont(url)) {
            return emptyResponse("font/woff2", 404, "Blocked")
        }
        if (blockFrames && isThirdPartyFrame(request, url, pageUrl)) {
            return emptyResponse("text/html", 200, "OK")
        }
        return null
    }

    private fun emptyResponse(mimeType: String, status: Int, reason: String): WebResourceResponse =
        WebResourceResponse(mimeType, "utf-8", status, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun headerValue(request: WebResourceRequest, name: String): String? {
        val headers = request.requestHeaders ?: return null
        for ((key, value) in headers) {
            if (key.equals(name, ignoreCase = true)) return value
        }
        return null
    }

    private fun isImage(request: WebResourceRequest, url: Uri): Boolean {
        val accept = headerValue(request, "Accept")
        if (accept != null && accept.startsWith("image/", ignoreCase = true)) return true
        if (isNavigationRequest(request)) return false
        val path = url.path?.lowercase() ?: return false
        return imageExtensions.any { path.endsWith(it) }
    }

    private fun isScript(request: WebResourceRequest, url: Uri): Boolean {
        if (isNavigationRequest(request)) return false
        val dest = headerValue(request, "Sec-Fetch-Dest")
        if (dest != null) {
            if (dest.equals("script", ignoreCase = true) || dest.equals("worker", ignoreCase = true) || dest.equals("sharedworker", ignoreCase = true)) return true
            if (dest.isNotEmpty() && !dest.equals("empty", ignoreCase = true)) return false
        }
        val path = url.path?.lowercase() ?: return false
        if (scriptExtensions.any { path.endsWith(it) }) return true
        return scriptPathSuffixes.any { path.endsWith(it) }
    }

    private fun isStyleSheet(request: WebResourceRequest, url: Uri): Boolean {
        if (isNavigationRequest(request)) return false
        val dest = headerValue(request, "Sec-Fetch-Dest")
        if (dest != null && dest.equals("style", ignoreCase = true)) return true
        val accept = headerValue(request, "Accept")
        if (accept != null && accept.startsWith("text/css", ignoreCase = true)) return true
        val path = url.path?.lowercase() ?: return false
        return styleExtensions.any { path.endsWith(it) }
    }

    private fun isHeavyFont(url: Uri): Boolean {
        val path = url.path?.lowercase() ?: return false
        if (fontExtensions.none { path.endsWith(it) }) return false
        return iconFontHints.none { path.contains(it) }
    }

    private fun isNavigationRequest(request: WebResourceRequest): Boolean {
        val headers = request.requestHeaders ?: return false
        var accept = ""
        for ((key, value) in headers) {
            if (key.equals("Upgrade-Insecure-Requests", ignoreCase = true) && value == "1") return true
            if (key.equals("Accept", ignoreCase = true)) accept = value
        }
        return accept.startsWith("text/html,application/xhtml+xml", ignoreCase = true)
    }

    private fun isThirdPartyFrame(request: WebResourceRequest, url: Uri, pageUrl: String?): Boolean {
        if (!isNavigationRequest(request)) return false
        val frameHost = url.host?.lowercase() ?: return false
        val pageHost = pageUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }?.lowercase() ?: return false
        if (baseDomain(frameHost) == baseDomain(pageHost)) return false
        if (allowedFrameHosts.any { frameHost == it || frameHost.endsWith(".$it") }) return false
        val path = url.path?.lowercase().orEmpty()
        if (allowedFramePathHints.any { path.contains(it) }) return false
        if (adFrameHostHints.any { frameHost.contains(it) }) return true
        if (looksLikeVideoPlayer(frameHost, path)) return false
        return true
    }

    private fun looksLikeVideoPlayer(host: String, path: String): Boolean {
        if (playerHostHints.any { host.contains(it) }) return true
        return playerPathHints.any { path.startsWith(it) || path.contains(it) }
    }

    private fun baseDomain(host: String): String {
        val parts = host.trimEnd('.').split('.')
        return if (parts.size <= 2) parts.joinToString(".") else parts.takeLast(2).joinToString(".")
    }
}
