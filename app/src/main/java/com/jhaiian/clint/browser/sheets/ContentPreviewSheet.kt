package com.jhaiian.clint.browser.sheets
import com.jhaiian.clint.browser.delegates.*

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.webview.ClintWebViewClient
import com.jhaiian.clint.quiver.engine.BlockedRequestCounter
import com.jhaiian.clint.quiver.engine.QuiverGuardWebIntegration
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.settings.sitepermissions.SitePermissionManager
import com.jhaiian.clint.ui.FaviconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContentPreviewSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onPreviewOpenInNewTab(url: String)
    }

    private var previewWebView: WebView? = null
    private var listener: Listener? = null
    private var sheetBehavior: BottomSheetBehavior<View>? = null

    // Unique per-instance ID so Quiver Guard's blocked-request counter can
    // track this preview's traffic without colliding with real browser tabs.
    private val quiverGuardPreviewTabId = "preview-" + System.identityHashCode(this)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (prefs.getBoolean("hide_status_bar", false)) {
            @Suppress("DEPRECATION")
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(it)
                sheetBehavior = behavior
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                it.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
                it.requestLayout()
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.bottom_sheet_content_preview, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val url = arguments?.getString(ARG_URL) ?: ""
        val isPage = arguments?.getBoolean(ARG_IS_PAGE) ?: false
        val isDesktop = arguments?.getBoolean(ARG_IS_DESKTOP) ?: false
        val isReaderMode = arguments?.getBoolean(ARG_IS_READER_MODE) ?: false
        val readerHtml = arguments?.getString(ARG_READER_HTML) ?: ""
        val readerTitle = arguments?.getString(ARG_READER_TITLE) ?: ""

        val titleView = view.findViewById<TextView>(R.id.preview_title)
        val urlView = view.findViewById<TextView>(R.id.preview_url)
        val faviconView = view.findViewById<ImageView>(R.id.preview_favicon)
        val btnOpenInNewTab = view.findViewById<ImageButton>(R.id.btn_open_in_new_tab)
        val btnClose = view.findViewById<ImageButton>(R.id.btn_close_preview)

        val iconTint = ColorStateList.valueOf(
            MaterialColors.getColor(view, R.attr.clintIconTint)
        )
        ImageViewCompat.setImageTintList(faviconView, iconTint)
        ImageViewCompat.setImageTintList(btnOpenInNewTab, iconTint)
        ImageViewCompat.setImageTintList(btnClose, ColorStateList.valueOf(android.graphics.Color.WHITE))

        val host = runCatching { java.net.URL(url).host }.getOrElse { "" }

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val dataSaverEnabled = prefs.getBoolean("data_saver_enabled", false)
        val disableImages = dataSaverEnabled && prefs.getBoolean("data_saver_disable_images", false)
        val disableAutoplay = dataSaverEnabled && prefs.getBoolean("data_saver_disable_autoplay", true)
        val quiverGuardEnabled = prefs.getBoolean("quiver_guard_enabled", false)
        val effectiveCacheMode = resolveEffectiveCacheMode(prefs)

        if (isReaderMode) {
            titleView.text = readerTitle.ifEmpty { host }
            urlView.text = if (host.isNotEmpty()) host else url
            faviconView.setImageResource(R.drawable.ic_reader_mode_24)
            ImageViewCompat.setImageTintList(faviconView, iconTint)
            if (url.isNotEmpty()) {
                val faviconUrl = FaviconCache.faviconUrlFor(url)
                if (faviconUrl.isNotEmpty()) {
                    FaviconCache.load(requireContext(), faviconUrl, disableImages) { bmp ->
                        if (isAdded && bmp != null) {
                            faviconView.setImageBitmap(bmp)
                            ImageViewCompat.setImageTintList(faviconView, null)
                        }
                    }
                }
            }
        } else if (isPage) {
            titleView.text = host
            urlView.text = url
            faviconView.setImageResource(R.drawable.ic_globe_24)
            ImageViewCompat.setImageTintList(faviconView, iconTint)
            val faviconUrl = FaviconCache.faviconUrlFor(url)
            if (faviconUrl.isNotEmpty()) {
                FaviconCache.load(requireContext(), faviconUrl, disableImages) { bmp ->
                    if (isAdded && bmp != null) {
                        faviconView.setImageBitmap(bmp)
                        ImageViewCompat.setImageTintList(faviconView, null)
                    }
                }
            }
        } else {
            val filename = url.substringAfterLast("/").substringBefore("?").let { n ->
                if (n.isEmpty()) url else n
            }
            titleView.text = filename
            urlView.text = host
            faviconView.setImageResource(R.drawable.ic_globe_24)
            ImageViewCompat.setImageTintList(faviconView, iconTint)
        }

        val wv = view.findViewById<WebView>(R.id.preview_webview)
        previewWebView = wv

        // Quiver Guard: register the cosmetic-filter bootstrap script (and its JS
        // bridge) before this WebView's first navigation. addJavascriptInterface
        // and addDocumentStartJavaScript only take effect starting with the
        // navigation *after* they're called, so the exception-aware, reactive
        // registration further below is always one navigation too late for a
        // brand-new WebView - see QuiverGuardWebIntegration.installEarly's kdoc.
        // Every preview WebView is freshly constructed and torn down with this
        // sheet, so its one and only page load *is* that first navigation; without
        // this call cosmetic filtering never gets a chance to apply here, even
        // though it works fine on a real tab, which gets this same call from
        // MainActivity.createWebView.
        if (quiverGuardEnabled) {
            QuiverGuardWebIntegration.installEarly(requireContext(), wv)
        }

        wv.settings.apply {
            javaScriptEnabled = isPage
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = buildPreviewUserAgent(isDesktop)
            loadsImagesAutomatically = !disableImages
            mediaPlaybackRequiresUserGesture = disableAutoplay
            cacheMode = effectiveCacheMode
        }

        if (isPage && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val trackerJs = requireContext().assets.open("JavaScript/link_touch_tracker.js").bufferedReader().use { it.readText() }
            WebViewCompat.addDocumentStartJavaScript(wv, trackerJs, setOf("*"))
        }

        if (isDesktop && isPage && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val js = requireContext().assets.open("JavaScript/desktop_mode.js").bufferedReader().use { it.readText() }
            WebViewCompat.addDocumentStartJavaScript(wv, js, setOf("*"))
        }

        if (disableAutoplay && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val autoplayJs = requireContext().assets.open("JavaScript/disable_autoplay.js").bufferedReader().use { it.readText() }
            WebViewCompat.addDocumentStartJavaScript(wv, autoplayJs, setOf("*"))
        }

        // Quiver Guard: skip sites on the exception list, otherwise register the
        // cosmetic/scriptlet document-start script before the load below reaches
        // the network. The exception lookup and script build are real DB/CPU
        // work, so they run on Dispatchers.IO; registering the script itself
        // only touches the WebView and stays on the main dispatcher.
        if (isPage && quiverGuardEnabled && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            viewLifecycleOwner.lifecycleScope.launch {
                val isExcepted = withContext(Dispatchers.IO) {
                    host.isNotEmpty() && SitePermissionManager.getState(
                        requireContext(), host, SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
                    ) != null
                }
                if (isExcepted || !isAdded) return@launch

                val script = withContext(Dispatchers.IO) {
                    QuiverGuardWebIntegration.buildDocumentStartScript(requireContext(), url, true)
                }
                if (script != null && isAdded) {
                    WebViewCompat.addDocumentStartJavaScript(wv, script, setOf("*"))
                }
            }
        }

        if (!isReaderMode) applyPreviewDarkMode(wv)

        wv.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            sheetBehavior?.isDraggable = scrollY == 0
        }

        // ClintWebViewClient already implements the Quiver Guard network-blocking
        // check (including the exception-list lookup) in shouldInterceptRequest,
        // plus https-only upgrading, tracker-host blocking, and external-app intent
        // handling - reusing it here keeps this preview WebView's behavior
        // identical to a real tab instead of duplicating that logic.
        wv.webViewClient = ClintWebViewClient(
            prefs = prefs,
            isActive = { isAdded },
            onPageFinishedCallback = { pageUrl ->
                if (isPage) {
                    val pageTitle = wv.title
                    val pageHost = runCatching { java.net.URL(pageUrl).host }.getOrElse { "" }
                    if (!pageTitle.isNullOrEmpty()) titleView.text = pageTitle
                    if (pageHost.isNotEmpty()) urlView.text = pageHost
                }
                // Fallback cosmetic-filter injection for WebView versions that
                // don't support the document-start API; the document-start path
                // above already handles everything else.
                if (isPage && quiverGuardEnabled && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    applyQuiverGuardCosmeticFallback(wv, pageUrl)
                }
            },
            getDesktopHeaders = { if (isDesktop && isPage) buildDesktopHeaders(wv) else null },
            getTabId = { quiverGuardPreviewTabId }
        )

        if (isPage) {
            wv.webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String) {
                    if (isAdded && title.isNotEmpty()) titleView.text = title
                }
            }
        }

        if (!isPage) {
            wv.setOnLongClickListener {
                val result = wv.hitTestResult
                if (result.type == WebView.HitTestResult.IMAGE_TYPE ||
                    result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    val hitUrl = result.extra ?: url
                    val existing = parentFragmentManager.findFragmentByTag("image_long_press_preview")
                    if (existing == null) {
                val sheet = ImageLongPressSheet.newInstance(hitUrl, "", isStandaloneImage = false, isPreviewContext = true)
                        sheet.show(parentFragmentManager, "image_long_press_preview")
                    }
                    true
                } else {
                    false
                }
            }
        }

        if (isPage) {
            wv.setOnLongClickListener {
                val result = wv.hitTestResult
                when (result.type) {
                    WebView.HitTestResult.IMAGE_TYPE -> {
                        val hitUrl = result.extra ?: return@setOnLongClickListener false
                        val existing = parentFragmentManager.findFragmentByTag("image_long_press_preview")
                        if (existing == null && isAdded) {
                            ImageLongPressSheet.newInstance(hitUrl, "", isStandaloneImage = false, isPreviewContext = true)
                                .show(parentFragmentManager, "image_long_press_preview")
                        }
                        true
                    }
                    WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                        val linkUrl = result.extra ?: return@setOnLongClickListener false
                        showPreviewLinkLongPressSheet(wv, linkUrl)
                        true
                    }
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        // HitTestResult.extra returns the <img> src rather than the
                        // enclosing anchor's href for this hit type, so the href must
                        // be requested asynchronously via requestFocusNodeHref. This
                        // lets a linked icon inside the previewed page resolve to the
                        // link sheet instead of the image sheet.
                        val hrefHandler = Handler(Looper.getMainLooper()) { message ->
                            val linkUrl = message.data.getString("url")
                            if (!linkUrl.isNullOrEmpty()) showPreviewLinkLongPressSheet(wv, linkUrl)
                            true
                        }
                        wv.requestFocusNodeHref(hrefHandler.obtainMessage())
                        true
                    }
                    else -> false
                }
            }
        }

        if (isReaderMode) {
            wv.loadDataWithBaseURL(url.ifEmpty { null }, readerHtml, "text/html", "UTF-8", null)
        } else if (url.isNotEmpty()) {
            if (isDesktop && isPage) {
                wv.loadUrl(url, buildDesktopHeaders(wv))
            } else {
                wv.loadUrl(url)
            }
        }

        btnClose.setOnClickListener { dismiss() }

        btnOpenInNewTab.setOnClickListener {
            val currentUrl = if (isPage) previewWebView?.url?.takeIf { it.isNotEmpty() } ?: url else url
            dismiss()
            listener?.onPreviewOpenInNewTab(currentUrl)
        }
    }

    private fun showPreviewLinkLongPressSheet(webView: WebView, linkUrl: String) {
        val linkTextJs = "(function() { return (window.__clintLastTouchedLinkText || ''); })()"
        webView.evaluateJavascript(linkTextJs) { raw ->
            val linkText = raw?.removeSurrounding("\"")
                ?.replace("\\n", " ")
                ?.replace("\\t", " ")
                ?.trim() ?: ""
            if (isAdded) {
                val existing = parentFragmentManager.findFragmentByTag("preview_link_long_press")
                if (existing == null) {
                    PreviewLinkLongPressSheet.newInstance(linkUrl, linkText)
                        .show(parentFragmentManager, "preview_link_long_press")
                }
            }
        }
    }

    private fun buildPreviewUserAgent(isDesktop: Boolean): String {
        val defaultUA = WebSettings.getDefaultUserAgent(requireContext())
        val chromeVersion = Regex("Chrome/([\\d.]+)").find(defaultUA)?.groupValues?.get(1) ?: "134.0.0.0"
        val androidVersion = android.os.Build.VERSION.RELEASE
        return if (isDesktop) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
        } else {
            "Mozilla/5.0 (Linux; Android $androidVersion; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
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

    // Fallback for WebView versions without WebViewFeature.DOCUMENT_START_SCRIPT:
    // injects cosmetic/scriptlet filters via evaluateJavascript once the page has
    // finished loading. Less effective (filters apply after first paint) but the
    // only option available on older devices. Exception lookup and script build
    // run on Dispatchers.IO since both touch the DB/compiled filter database.
    private fun applyQuiverGuardCosmeticFallback(webView: WebView, pageUrl: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val pageHost = runCatching { android.net.Uri.parse(pageUrl).host }.getOrNull()
            val isExcepted = withContext(Dispatchers.IO) {
                pageHost != null && SitePermissionManager.getState(
                    requireContext(), pageHost, SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
                ) != null
            }
            if (isExcepted || !isAdded) return@launch

            val script = withContext(Dispatchers.IO) {
                QuiverGuardWebIntegration.buildCosmeticFilterScript(requireContext(), pageUrl, true)
            } ?: return@launch

            if (isAdded) QuiverGuardWebIntegration.applyCosmeticFilterScript(webView, script)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyPreviewDarkMode(webView: WebView) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val theme = prefs.getString("app_theme", "dark") ?: "dark"
        val enabled = when (theme) {
            "dark" -> true
            "light" -> false
            else -> prefs.getBoolean("force_dark_web", false)
        }
        val settings = webView.settings
        when {
            WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) ->
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, enabled)
            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) ->
                WebSettingsCompat.setForceDark(
                    settings,
                    if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
        }
    }

    override fun onDestroyView() {
        previewWebView?.destroy()
        previewWebView = null
        BlockedRequestCounter.removeTab(quiverGuardPreviewTabId)
        super.onDestroyView()
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_IS_PAGE = "is_page"
        private const val ARG_IS_DESKTOP = "is_desktop"
        private const val ARG_IS_READER_MODE = "is_reader_mode"
        private const val ARG_READER_HTML = "reader_html"
        private const val ARG_READER_TITLE = "reader_title"

        fun newInstanceForImage(imageUrl: String): ContentPreviewSheet {
            return ContentPreviewSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, imageUrl)
                    putBoolean(ARG_IS_PAGE, false)
                    putBoolean(ARG_IS_DESKTOP, false)
                    putBoolean(ARG_IS_READER_MODE, false)
                }
            }
        }

        fun newInstanceForPage(pageUrl: String, isDesktop: Boolean): ContentPreviewSheet {
            return ContentPreviewSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, pageUrl)
                    putBoolean(ARG_IS_PAGE, true)
                    putBoolean(ARG_IS_DESKTOP, isDesktop)
                    putBoolean(ARG_IS_READER_MODE, false)
                }
            }
        }

        fun newInstanceForReaderMode(pageUrl: String, pageTitle: String, html: String): ContentPreviewSheet {
            return ContentPreviewSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, pageUrl)
                    putString(ARG_READER_TITLE, pageTitle)
                    putString(ARG_READER_HTML, html)
                    putBoolean(ARG_IS_PAGE, false)
                    putBoolean(ARG_IS_DESKTOP, false)
                    putBoolean(ARG_IS_READER_MODE, true)
                }
            }
        }
    }
}
