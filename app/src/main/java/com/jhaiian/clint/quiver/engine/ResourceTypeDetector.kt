package com.jhaiian.clint.quiver.engine

import android.webkit.WebResourceRequest

// Infers the resource type of an intercepted WebView request so the engine can
// evaluate type-specific filter rules (e.g. $script, $image). The WebView API
// does not expose the resource type directly, so it is derived in priority order
// from the scheme, Sec-Fetch-Dest header, Sec-Fetch-Mode header, Accept header,
// and finally the URL file extension as a last resort.
//
// Return values are adblock-rust's own request-type strings (see the `cpt_match_type`
// table in adblock-rust's request.rs) rather than a custom enum, since they're passed
// straight through to Request::new() over JNI.
object ResourceTypeDetector {

    private val SCRIPT_EXTENSIONS = setOf("js", "mjs", "jsx", "ts")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp", "avif")
    private val STYLESHEET_EXTENSIONS = setOf("css", "less", "scss")
    private val FONT_EXTENSIONS = setOf("woff", "woff2", "ttf", "otf", "eot")
    private val MEDIA_EXTENSIONS = setOf("mp4", "webm", "mp3", "ogg", "flac", "wav", "avi", "mov", "m4v", "m3u8", "mkv", "opus")

    fun detect(request: WebResourceRequest): String {
        val scheme = request.url.scheme?.lowercase() ?: ""

        // WebSocket requests are identifiable by scheme before inspecting headers.
        if (scheme == "ws" || scheme == "wss") return "websocket"

        val accept = request.requestHeaders?.get("Accept") ?: ""
        val destination = request.requestHeaders?.get("Sec-Fetch-Dest") ?: ""
        val mode = request.requestHeaders?.get("Sec-Fetch-Mode") ?: ""

        // Sec-Fetch-Dest is the most reliable signal because it is set by the browser
        // itself and not by page scripts. Check it before Accept or the URL extension.
        when (destination.lowercase()) {
            "script" -> return "script"
            "style" -> return "stylesheet"
            "image", "img" -> return "image"
            "font" -> return "font"
            "media", "video", "audio" -> return "media"
            "worker", "sharedworker", "serviceworker" -> return "script"
            "iframe", "frame" -> return "sub_frame"
            "document" -> return if (request.isForMainFrame) "document" else "sub_frame"
            "object", "embed" -> return "object"
            "track" -> return "media"
            "manifest" -> return "other"
        }

        // Sec-Fetch-Mode provides additional context for requests that share a
        // destination value (e.g. CORS vs navigate).
        when (mode.lowercase()) {
            "cors", "no-cors" -> {
                if (accept.contains("application/json") || accept.contains("text/plain")) return "xhr"
            }
            "websocket" -> return "websocket"
            "navigate" -> return if (request.isForMainFrame) "document" else "sub_frame"
        }

        if (accept.isNotEmpty()) {
            return when {
                accept.contains("text/html") && request.isForMainFrame -> "document"
                accept.contains("text/html") -> "sub_frame"
                accept.contains("text/css") -> "stylesheet"
                accept.contains("image/") -> "image"
                accept.contains("application/javascript") || accept.contains("text/javascript") -> "script"
                accept.contains("font/") || accept.contains("application/font") -> "font"
                accept.contains("audio/") || accept.contains("video/") -> "media"
                else -> "other"
            }
        }

        // File extension is the least reliable signal because URLs can omit extensions
        // or use query strings, but it covers a useful set of static asset requests.
        val path = request.url.path?.lowercase() ?: ""
        val ext = path.substringAfterLast('.', "").substringBefore('?').substringBefore('#')
        return when {
            ext in SCRIPT_EXTENSIONS -> "script"
            ext in IMAGE_EXTENSIONS -> "image"
            ext in STYLESHEET_EXTENSIONS -> "stylesheet"
            ext in FONT_EXTENSIONS -> "font"
            ext in MEDIA_EXTENSIONS -> "media"
            // When all signals are exhausted, default to XHR as a reasonable fallback
            // for fetch-like requests that do not match any of the above patterns.
            else -> "xhr"
        }
    }
}
