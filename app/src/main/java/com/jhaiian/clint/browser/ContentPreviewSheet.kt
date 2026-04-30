package com.jhaiian.clint.browser

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.core.widget.ImageViewCompat
import androidx.preference.PreferenceManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.FaviconCache

class ContentPreviewSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onPreviewOpenInNewTab(url: String)
    }

    private var previewWebView: WebView? = null
    private var listener: Listener? = null
    private var sheetBehavior: BottomSheetBehavior<View>? = null

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

        if (isPage) {
            titleView.text = host
            urlView.text = url
            faviconView.setImageResource(R.drawable.ic_globe_24)
            ImageViewCompat.setImageTintList(faviconView, iconTint)
            val faviconUrl = FaviconCache.faviconUrlFor(url)
            if (faviconUrl.isNotEmpty()) {
                FaviconCache.load(requireContext(), faviconUrl) { bmp ->
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

        wv.settings.apply {
            javaScriptEnabled = isPage
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = buildPreviewUserAgent(isDesktop)
        }

        if (isPage && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val trackerJs = requireContext().assets.open("JavaScript/link_touch_tracker.js").bufferedReader().use { it.readText() }
            WebViewCompat.addDocumentStartJavaScript(wv, trackerJs, setOf("*"))
        }

        if (isDesktop && isPage && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val js = requireContext().assets.open("JavaScript/desktop_mode.js").bufferedReader().use { it.readText() }
            WebViewCompat.addDocumentStartJavaScript(wv, js, setOf("*"))
        }

        applyPreviewDarkMode(wv)

        wv.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            sheetBehavior?.isDraggable = scrollY == 0
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
            override fun onPageFinished(view: WebView, pageUrl: String) {
                if (isPage && isAdded) {
                    val pageTitle = view.title
                    val pageHost = runCatching { java.net.URL(pageUrl).host }.getOrElse { "" }
                    if (!pageTitle.isNullOrEmpty()) titleView.text = pageTitle
                    if (pageHost.isNotEmpty()) urlView.text = pageHost
                }
            }
        }

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
                    WebView.HitTestResult.IMAGE_TYPE,
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
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
                        val linkTextJs = "(function() { return (window.__clintLastTouchedLinkText || ''); })()"
                        wv.evaluateJavascript(linkTextJs) { raw ->
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
                        true
                    }
                    else -> false
                }
            }
        }

        if (url.isNotEmpty()) {
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

    @Suppress("DEPRECATION")
    private fun applyPreviewDarkMode(webView: WebView) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val theme = prefs.getString("app_theme", "default") ?: "default"
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
        super.onDestroyView()
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_IS_PAGE = "is_page"
        private const val ARG_IS_DESKTOP = "is_desktop"

        fun newInstanceForImage(imageUrl: String): ContentPreviewSheet {
            return ContentPreviewSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, imageUrl)
                    putBoolean(ARG_IS_PAGE, false)
                    putBoolean(ARG_IS_DESKTOP, false)
                }
            }
        }

        fun newInstanceForPage(pageUrl: String, isDesktop: Boolean): ContentPreviewSheet {
            return ContentPreviewSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, pageUrl)
                    putBoolean(ARG_IS_PAGE, true)
                    putBoolean(ARG_IS_DESKTOP, isDesktop)
                }
            }
        }
    }
}
