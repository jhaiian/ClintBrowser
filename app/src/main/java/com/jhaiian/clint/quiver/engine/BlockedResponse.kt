package com.jhaiian.clint.quiver.engine

import android.webkit.WebResourceResponse

// Produces WebResourceResponse objects that substitute for blocked network requests.
// Every response carries an explicit 200 OK status and Access-Control-Allow-Origin: *
// so pages cannot distinguish a blocked resource from one that loaded successfully
// but happened to be empty.
//
// Image responses use a valid 1x1 transparent GIF instead of empty bytes because
// an empty body is not a valid image: the browser fires img.onerror on decode
// failure even when the HTTP status is 200. Anti-adblock services like
// html-load.com / Freestar load a tiny image beacon from their CDN and listen for
// onerror to detect that ad requests are being blocked. Returning valid image data
// ensures img.onload fires instead, preventing this detection vector.
object BlockedResponse {

    // 1x1 transparent GIF — valid binary data returned for every blocked image.
    private val TRANSPARENT_GIF: ByteArray = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
        0x01, 0x00, 0x01, 0x00,
        0x80.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00,
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0x21, 0xf9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00,
        0x2c,
        0x00, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x01, 0x00,
        0x00,
        0x02, 0x02, 0x44, 0x01, 0x00,
        0x3b
    )

    private fun emptyStream() = "".byteInputStream()

    internal val BASE_HEADERS = mapOf(
        "Access-Control-Allow-Origin"  to "*",
        "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers" to "*",
        "Cache-Control"                to "no-store"
    )

    // resourceType is one of adblock-rust's request-type strings (see ResourceTypeDetector).
    fun forResourceType(resourceType: String): WebResourceResponse = when (resourceType) {
        "script" ->
            WebResourceResponse(
                "application/javascript", "UTF-8", 200, "OK",
                BASE_HEADERS + mapOf("Content-Type" to "application/javascript; charset=utf-8"),
                emptyStream()
            )
        "stylesheet" ->
            WebResourceResponse(
                "text/css", "UTF-8", 200, "OK",
                BASE_HEADERS + mapOf("Content-Type" to "text/css; charset=utf-8"),
                emptyStream()
            )
        "image" ->
            // Return valid GIF bytes. An empty body causes a decode error which fires
            // img.onerror — the primary signal used by html-load.com / Freestar beacon
            // detection to confirm that ad images are being blocked.
            WebResourceResponse(
                "image/gif", "binary", 200, "OK",
                BASE_HEADERS + mapOf("Content-Type" to "image/gif"),
                TRANSPARENT_GIF.inputStream()
            )
        "xhr" ->
            WebResourceResponse(
                "application/json", "UTF-8", 200, "OK",
                BASE_HEADERS + mapOf("Content-Type" to "application/json; charset=utf-8"),
                emptyStream()
            )
        "media" ->
            WebResourceResponse(
                "video/mp4", "binary", 200, "OK",
                BASE_HEADERS + mapOf("Content-Type" to "video/mp4"),
                emptyStream()
            )
        "font" ->
            WebResourceResponse(
                "font/woff2", "binary", 200, "OK",
                BASE_HEADERS + mapOf("Content-Type" to "font/woff2"),
                emptyStream()
            )
        else ->
            WebResourceResponse(
                "text/plain", "UTF-8", 200, "OK",
                BASE_HEADERS + mapOf("Content-Type" to "text/plain; charset=utf-8"),
                emptyStream()
            )
    }
}
