package com.jhaiian.clint

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
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

    private val skipHeaders = setOf(
        "content-length", "connection", "keep-alive", "transfer-encoding",
        "accept-encoding", "host"
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

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val host = request.url.host
            ?: return super.shouldInterceptRequest(view, request)
        val scheme = request.url.scheme
            ?: return super.shouldInterceptRequest(view, request)

        if (scheme != "http" && scheme != "https") {
            return super.shouldInterceptRequest(view, request)
        }

        if (prefs.getBoolean("block_trackers", true)) {
            if (trackerHosts.any { host.contains(it) }) {
                return WebResourceResponse("text/plain", "UTF-8", null)
            }
        }

        val dohClient = DohManager.getClient(prefs)
            ?: return super.shouldInterceptRequest(view, request)

        if (!request.method.equals("GET", ignoreCase = true) &&
            !request.method.equals("HEAD", ignoreCase = true)) {
            return super.shouldInterceptRequest(view, request)
        }

        return try {
            val reqBuilder = Request.Builder()
                .url(request.url.toString())
                .method(request.method, null)

            request.requestHeaders.forEach { (key, value) ->
                if (key.lowercase() !in skipHeaders) {
                    runCatching { reqBuilder.addHeader(key, value) }
                }
            }

            val cookies = runCatching {
                CookieManager.getInstance().getCookie(request.url.toString())
            }.getOrNull()
            if (!cookies.isNullOrEmpty()) {
                reqBuilder.header("Cookie", cookies)
            }

            val call = dohClient.newCall(reqBuilder.build())
            val response = call.execute()

            val statusCode = response.code
            val statusMsg = response.message.ifEmpty { "OK" }
            val isRedirect = response.isRedirect
            val locationHeader = response.header("Location")

            val setCookieHeaders = response.headers.values("Set-Cookie")
            if (setCookieHeaders.isNotEmpty()) {
                val urlStr = request.url.toString()
                val cm = CookieManager.getInstance()
                setCookieHeaders.forEach { cookie ->
                    runCatching { cm.setCookie(urlStr, cookie) }
                }
                runCatching { cm.flush() }
            }

            if (isRedirect) {
                response.close()
                return null
            }

            val contentType = response.header("Content-Type") ?: "application/octet-stream"
            val mimeType = contentType.split(";")[0].trim().ifEmpty { "application/octet-stream" }
            val charset = Regex("[Cc]harset=([\\w-]+)")
                .find(contentType)?.groupValues?.get(1) ?: "UTF-8"

            val responseHeaders = mutableMapOf<String, String>()
            for (i in 0 until response.headers.size) {
                val name = response.headers.name(i).lowercase()
                if (name !in setOf("content-encoding", "transfer-encoding",
                        "connection", "set-cookie")) {
                    responseHeaders[response.headers.name(i)] = response.headers.value(i)
                }
            }

            val body = runCatching { response.body?.bytes() }.getOrNull() ?: ByteArray(0)
            response.close()

            WebResourceResponse(
                mimeType, charset,
                statusCode, statusMsg,
                responseHeaders,
                ByteArrayInputStream(body)
            )

        } catch (e: Exception) {
            val mode = prefs.getString("doh_mode", DohManager.MODE_OFF) ?: DohManager.MODE_OFF
            if (mode == DohManager.MODE_MAX) {
                val errorHtml = "<html><body style='background:#0D0114;color:#CE93D8;" +
                    "font-family:sans-serif;padding:24px'><h2>Secure DNS Failed</h2>" +
                    "<p>Could not connect to <b>$host</b> via your DoH provider.</p>" +
                    "<p>Max Protection is enabled. You can change this in Settings &gt; " +
                    "DNS over HTTPS.</p></body></html>"
                WebResourceResponse(
                    "text/html", "UTF-8", 521, "Secure DNS Failed",
                    mapOf("Content-Type" to "text/html"),
                    ByteArrayInputStream(errorHtml.toByteArray())
                )
            } else {
                null
            }
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }
}
