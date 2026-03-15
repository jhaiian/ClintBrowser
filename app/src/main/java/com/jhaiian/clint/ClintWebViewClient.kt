package com.jhaiian.clint

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Request
import java.io.ByteArrayInputStream

class ClintWebViewClient(
    private val prefs: SharedPreferences,
    private val isActive: () -> Boolean = { true }
) : WebViewClient() {

    private val trackerHosts = setOf(
        "googletagmanager.com", "google-analytics.com", "analytics.google.com",
        "doubleclick.net", "googlesyndication.com", "adservice.google.com",
        "connect.facebook.net", "scorecardresearch.com", "quantserve.com",
        "amazon-adsystem.com", "ads.twitter.com", "static.ads-twitter.com",
        "pixel.facebook.com", "an.facebook.com", "stats.g.doubleclick.net",
        "pagead2.googlesyndication.com"
    )

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (isActive()) (view.context as? MainActivity)?.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        (view.context as? MainActivity)?.onTabUrlUpdated(view, url)
        if (isActive()) (view.context as? MainActivity)?.onPageFinished(url)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme ?: return true
        if (scheme != "http" && scheme != "https") {
            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
            return true
        }
        val host = request.url.host ?: return false
        if (prefs.getBoolean("block_trackers", true)) {
            if (trackerHosts.any { host.contains(it) }) return true
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host ?: return super.shouldInterceptRequest(view, request)

        if (prefs.getBoolean("block_trackers", true)) {
            if (trackerHosts.any { host.contains(it) }) {
                return WebResourceResponse("text/plain", "UTF-8", null)
            }
        }

        val dohClient = DohManager.getClient(prefs)
        if (dohClient != null && request.method.equals("GET", ignoreCase = true)) {
            try {
                val reqBuilder = Request.Builder().url(request.url.toString()).get()
                request.requestHeaders.forEach { (key, value) ->
                    if (!key.equals("Cookie", ignoreCase = true)) {
                        runCatching { reqBuilder.addHeader(key, value) }
                    }
                }
                val response = dohClient.newCall(reqBuilder.build()).execute()
                val contentType = response.header("Content-Type") ?: "application/octet-stream"
                val mimeType = contentType.split(";")[0].trim()
                val charset = Regex("charset=([\w-]+)").find(contentType)?.groupValues?.get(1) ?: "UTF-8"
                val responseHeaders = mutableMapOf<String, String>()
                for (i in 0 until response.headers.size) {
                    responseHeaders[response.headers.name(i)] = response.headers.value(i)
                }
                val body = response.body?.bytes() ?: ByteArray(0)
                return WebResourceResponse(
                    mimeType, charset, response.code,
                    response.message.ifEmpty { "OK" },
                    responseHeaders,
                    ByteArrayInputStream(body)
                )
            } catch (e: Exception) {
                return if (prefs.getString("doh_mode", "off") == DohManager.MODE_MAX) {
                    val errorHtml = "<html><body style='background:#0D0114;color:#CE93D8;font-family:sans-serif;padding:24px'>" +
                        "<h2>Secure DNS Failed</h2><p>Could not resolve <b>$host</b> using your DoH provider.</p>" +
                        "<p>Max Protection is enabled. Disable it in Settings → Privacy &amp; Security → DNS over HTTPS to use system DNS.</p></body></html>"
                    WebResourceResponse("text/html", "UTF-8", 521, "Secure DNS Failed",
                        mapOf("Content-Type" to "text/html"),
                        ByteArrayInputStream(errorHtml.toByteArray()))
                } else {
                    null
                }
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }
}
