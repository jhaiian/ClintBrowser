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
        android.net.Uri.parse(url).host?.let { host ->
            DohManager.preResolveDns(host, prefs)
        }
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
        host.let { DohManager.preResolveDns(it, prefs) }
        return false
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host ?: return super.shouldInterceptRequest(view, request)
        if (prefs.getBoolean("block_trackers", true)) {
            if (trackerHosts.any { host.contains(it) }) {
                return WebResourceResponse("text/plain", "UTF-8", null)
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }
}
