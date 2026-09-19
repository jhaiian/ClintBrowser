package com.jhaiian.clint.browser.sheets
import com.jhaiian.clint.ui.theme.SystemDarkState
import androidx.compose.material.icons.automirrored.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Public

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.webview.ClintWebViewClient
import com.jhaiian.clint.browser.webview.loadJsAsset
import com.jhaiian.clint.quiver.engine.BlockedRequestCounter
import com.jhaiian.clint.quiver.engine.QuiverGuardWebIntegration
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.settings.sitepermissions.SitePermissionManager
import com.jhaiian.clint.ui.ClintDialogStatusBarEffect
import com.jhaiian.clint.ui.FaviconCache
import com.jhaiian.clint.ui.theme.LocalClintColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContentPreviewRequest(
    val url: String,
    val isPage: Boolean,
    val isDesktop: Boolean = false,
    val isReaderMode: Boolean = false,
    val readerHtml: String = "",
    val readerTitle: String = ""
) {
    companion object {
        fun forImage(imageUrl: String) = ContentPreviewRequest(url = imageUrl, isPage = false)
        fun forPage(pageUrl: String, isDesktop: Boolean) = ContentPreviewRequest(url = pageUrl, isPage = true, isDesktop = isDesktop)
        fun forReaderMode(pageUrl: String, pageTitle: String, html: String) =
            ContentPreviewRequest(url = pageUrl, isPage = false, isReaderMode = true, readerHtml = html, readerTitle = pageTitle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentPreviewSheet(request: ContentPreviewRequest, activity: MainActivity, onDismiss: () -> Unit) {
    val colors = LocalClintColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hideStatusBar = remember { PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("hide_status_bar", false) }
    val hideSystemNavigation = remember { PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("hide_system_navigation", false) }

    val host = remember(request.url) { runCatching { java.net.URL(request.url).host }.getOrElse { "" } }

    var titleText by remember { mutableStateOf(if (request.isReaderMode) request.readerTitle.ifEmpty { host } else if (request.isPage) host else request.url.substringAfterLast("/").substringBefore("?").ifEmpty { request.url }) }
    var urlText by remember { mutableStateOf(if (request.isReaderMode) host.ifEmpty { request.url } else if (request.isPage) request.url else host) }
    var favicon by remember { mutableStateOf<Bitmap?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewScrollY by remember { mutableIntStateOf(0) }

    var nestedImageRequest by remember { mutableStateOf<ImageLongPressRequest?>(null) }
    var nestedPreviewLinkRequest by remember { mutableStateOf<PreviewLinkLongPressRequest?>(null) }

    val quiverGuardPreviewTabId = remember { "preview-" + System.identityHashCode(request) }

    androidx.compose.runtime.LaunchedEffect(request.url, request.isReaderMode, request.isPage) {
        if ((request.isReaderMode || request.isPage) && request.url.isNotEmpty()) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val disableImages = prefs.getBoolean("data_saver_enabled", false) && prefs.getBoolean("data_saver_disable_images", true)
            val faviconUrl = FaviconCache.faviconUrlFor(request.url)
            if (faviconUrl.isNotEmpty()) {
                FaviconCache.load(context, faviconUrl, disableImages) { bmp -> if (bmp != null) favicon = bmp }
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            BlockedRequestCounter.removeTab(quiverGuardPreviewTabId)
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> !(target == SheetValue.Hidden && webViewScrollY > 0) }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = {}
    ) {
        ClintDialogStatusBarEffect(hideStatusBar, hideSystemNavigation)
        Column(Modifier.fillMaxSize()) {
            Surface(color = colors.popupBackground) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (favicon != null) {
                        Image(favicon!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            if (request.isReaderMode) androidx.compose.material.icons.Icons.AutoMirrored.Filled.ChromeReaderMode else androidx.compose.material.icons.Icons.Filled.Public,
                            contentDescription = null,
                            tint = colors.iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(titleText, color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(urlText, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1)
                    }
                    IconButton(onClick = {
                        val currentUrl = if (request.isPage) webViewRef?.url?.takeIf { it.isNotEmpty() } ?: request.url else request.url
                        onDismiss()
                        activity.onPreviewOpenInNewTab(currentUrl)
                    }, modifier = Modifier.size(44.dp)) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.link_open_in_new_tab), tint = colors.iconTint)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = stringResource(R.string.close_tab), tint = Color.White)
                    }
                }
            }
            HorizontalDivider(color = colors.divider, thickness = 1.dp)

            Box(Modifier.weight(1f)) {
                PreviewWebView(
                    request = request,
                    activity = activity,
                    quiverGuardPreviewTabId = quiverGuardPreviewTabId,
                    scope = scope,
                    onWebViewCreated = { webViewRef = it },
                    onScrollYChanged = { webViewScrollY = it },
                    onTitleChanged = { titleText = it },
                    onUrlChanged = { urlText = it },
                    onImageLongPress = { nestedImageRequest = it },
                    onPreviewLinkLongPress = { nestedPreviewLinkRequest = it }
                )
            }
        }
    }

    nestedImageRequest?.let { req ->
        ImageLongPressSheet(request = req, activity = activity, onDismiss = { nestedImageRequest = null })
    }
    nestedPreviewLinkRequest?.let { req ->
        PreviewLinkLongPressSheet(request = req, activity = activity, onDismiss = { nestedPreviewLinkRequest = null })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PreviewWebView(
    request: ContentPreviewRequest,
    activity: MainActivity,
    quiverGuardPreviewTabId: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onWebViewCreated: (WebView) -> Unit,
    onScrollYChanged: (Int) -> Unit,
    onTitleChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onImageLongPress: (ImageLongPressRequest) -> Unit,
    onPreviewLinkLongPress: (PreviewLinkLongPressRequest) -> Unit
) {

    val systemDark = SystemDarkState.isDark
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            AndroidView(
                modifier = Modifier.fillParentMaxSize(),
                update = { wv -> if (!request.isReaderMode) applyPreviewDarkMode(wv.context, wv, systemDark) },
                factory = { ctx ->
                    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                    val dataSaverEnabled = prefs.getBoolean("data_saver_enabled", false)
                    val disableImages = dataSaverEnabled && prefs.getBoolean("data_saver_disable_images", true)
                    val disableAutoplay = dataSaverEnabled && prefs.getBoolean("data_saver_disable_autoplay", true)
                    val quiverGuardEnabled = prefs.getBoolean("quiver_guard_enabled", false)

                    val wv = WebView(ctx)
                    onWebViewCreated(wv)

                    if (quiverGuardEnabled) QuiverGuardWebIntegration.installEarly(ctx, wv)

                    wv.settings.apply {
                        javaScriptEnabled = request.isPage
                        builtInZoomControls = true
                        displayZoomControls = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        userAgentString = buildPreviewUserAgent(ctx, request.isDesktop)
                        loadsImagesAutomatically = !disableImages
                        mediaPlaybackRequiresUserGesture = disableAutoplay
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    if (request.isPage && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(wv, activity.loadJsAsset("link_touch_tracker.js"), setOf("*"))
                    }
                    if (request.isDesktop && request.isPage && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(wv, activity.loadJsAsset("desktop_mode.js"), setOf("*"))
                    }
                    if (disableAutoplay && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(wv, activity.loadJsAsset("disable_autoplay.js"), setOf("*"))
                    }

                    if (!request.isReaderMode) applyPreviewDarkMode(ctx, wv, systemDark)

                    wv.setOnScrollChangeListener { _, _, scrollY, _, _ -> onScrollYChanged(scrollY) }
                    wv.setOnTouchListener { view, _ -> view.parent?.requestDisallowInterceptTouchEvent(true); false }

                    wv.webViewClient = ClintWebViewClient(
                        prefs = prefs,
                        isActive = { true },
                        onPageFinishedCallback = { pageUrl ->
                            if (request.isPage) {
                                val pageTitle = wv.title
                                val pageHost = runCatching { java.net.URL(pageUrl).host }.getOrElse { "" }
                                if (!pageTitle.isNullOrEmpty()) onTitleChanged(pageTitle)
                                if (pageHost.isNotEmpty()) onUrlChanged(pageHost)
                            }

                            if (request.isPage && quiverGuardEnabled && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                scope.launch {
                                    val pageHost = runCatching { android.net.Uri.parse(pageUrl).host }.getOrNull()
                                    val isExcepted = withContext(Dispatchers.IO) {
                                        pageHost != null && SitePermissionManager.getState(ctx, pageHost, SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION) != null
                                    }
                                    if (isExcepted) return@launch
                                    val script = withContext(Dispatchers.IO) {
                                        QuiverGuardWebIntegration.buildCosmeticFilterScript(ctx, pageUrl, true)
                                    } ?: return@launch
                                    QuiverGuardWebIntegration.applyCosmeticFilterScript(wv, script)
                                }
                            }
                        },
                        getDesktopHeaders = { if (request.isDesktop && request.isPage) buildDesktopHeaders(wv) else null },
                        getTabId = { quiverGuardPreviewTabId }
                    )

                    if (request.isPage) {
                        wv.webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView, title: String) {
                                if (title.isNotEmpty()) onTitleChanged(title)
                            }
                        }
                        wv.setOnLongClickListener {
                            val result = wv.hitTestResult
                            when (result.type) {
                                WebView.HitTestResult.IMAGE_TYPE -> {
                                    val hitUrl = result.extra ?: return@setOnLongClickListener false
                                    onImageLongPress(ImageLongPressRequest(hitUrl, "", isStandalone = false, isPreviewContext = true))
                                    true
                                }
                                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                    val linkUrl = result.extra ?: return@setOnLongClickListener false
                                    showPreviewLinkFromWebView(wv, linkUrl, onPreviewLinkLongPress)
                                    true
                                }
                                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {

                                    val hrefHandler = Handler(Looper.getMainLooper()) { message ->
                                        val linkUrl = message.data.getString("url")
                                        if (!linkUrl.isNullOrEmpty()) showPreviewLinkFromWebView(wv, linkUrl, onPreviewLinkLongPress)
                                        true
                                    }
                                    wv.requestFocusNodeHref(hrefHandler.obtainMessage())
                                    true
                                }
                                else -> false
                            }
                        }
                    } else {
                        wv.setOnLongClickListener {
                            val result = wv.hitTestResult
                            if (result.type == WebView.HitTestResult.IMAGE_TYPE || result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                val hitUrl = result.extra ?: request.url
                                onImageLongPress(ImageLongPressRequest(hitUrl, "", isStandalone = false, isPreviewContext = true))
                                true
                            } else {
                                false
                            }
                        }
                    }

                    if (request.isReaderMode) {
                        wv.loadDataWithBaseURL(request.url.ifEmpty { null }, request.readerHtml, "text/html", "UTF-8", null)
                    } else if (request.url.isNotEmpty()) {
                        if (request.isDesktop && request.isPage) {
                            wv.loadUrl(request.url, buildDesktopHeaders(wv))
                        } else {
                            wv.loadUrl(request.url)
                        }
                    }

                    wv
                }
            )
        }
    }
    }
}

private fun showPreviewLinkFromWebView(webView: WebView, linkUrl: String, onPreviewLinkLongPress: (PreviewLinkLongPressRequest) -> Unit) {
    val linkTextJs = "(function() { return (window.__clintLastTouchedLinkText || ''); })()"
    webView.evaluateJavascript(linkTextJs) { raw ->
        val linkText = raw?.removeSurrounding("\"")
            ?.replace("\\n", " ")
            ?.replace("\\t", " ")
            ?.trim() ?: ""
        onPreviewLinkLongPress(PreviewLinkLongPressRequest(linkUrl, linkText))
    }
}

private fun buildPreviewUserAgent(context: android.content.Context, isDesktop: Boolean): String {
    val defaultUA = WebSettings.getDefaultUserAgent(context)
    val chromeVersion = Regex("Chrome/([\\d.]+)").find(defaultUA)?.groupValues?.get(1) ?: "134.0.0.0"
    val androidVersion = android.os.Build.VERSION.RELEASE
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    return when {
        isDesktop -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
        prefs.getBoolean("custom_user_agent", true) ->
            "Mozilla/5.0 (Linux; Android $androidVersion; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
        else -> defaultUA
    }
}

private fun buildDesktopHeaders(webView: WebView): Map<String, String> {
    val defaultUA = WebSettings.getDefaultUserAgent(webView.context)
    val majorVersion = Regex("Chrome/(\\d+)").find(defaultUA)?.groupValues?.get(1) ?: "134"
    val secChUa = "\"Chromium\";v=\"$majorVersion\", \"Not-A.Brand\";v=\"24\", \"Google Chrome\";v=\"$majorVersion\""
    return mapOf(
        "Sec-CH-UA" to secChUa,
        "Sec-CH-UA-Mobile" to "?0",
        "Sec-CH-UA-Platform" to "\"Windows\""
    )
}

@Suppress("DEPRECATION")
private fun applyPreviewDarkMode(context: android.content.Context, webView: WebView, systemDark: Boolean) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val theme = prefs.getString("app_theme", "dark") ?: "dark"
    val enabled = when (theme) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val settings = webView.settings
    when {
        WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) ->
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, enabled)
        WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) ->
            WebSettingsCompat.setForceDark(settings, if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
    }
}
