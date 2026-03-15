package com.jhaiian.clint

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.jhaiian.clint.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setupWebView()
        setupAddressBar()
        setupNavigationButtons()
        val intentUrl = intent?.data?.toString()
        if (!intentUrl.isNullOrEmpty()) loadUrl(intentUrl) else loadUrl(getSearchEngineHomeUrl())
    }

    private fun getSearchEngineHomeUrl(): String {
        return when (prefs.getString("search_engine", "duckduckgo")) {
            "brave" -> "https://search.brave.com"
            "google" -> "https://www.google.com"
            else -> "https://duckduckgo.com"
        }
    }

    private fun getSearchQueryUrl(query: String): String {
        val encoded = Uri.encode(query)
        return when (prefs.getString("search_engine", "duckduckgo")) {
            "brave" -> "https://search.brave.com/search?q=$encoded"
            "google" -> "https://www.google.com/search?q=$encoded"
            else -> "https://duckduckgo.com/?q=$encoded"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = binding.webView.settings
        settings.javaScriptEnabled = prefs.getBoolean("javascript_enabled", true)
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.safeBrowsingEnabled = false
        settings.userAgentString = buildUserAgent()
        if (prefs.getBoolean("block_third_party_cookies", true)) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, false)
        }
        binding.webView.webViewClient = ClintWebViewClient(prefs)
        binding.webView.webChromeClient = ClintWebChromeClient()
        binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimetype)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription(getString(R.string.downloading))
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(url, contentDisposition, mimetype)
            )
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(applicationContext, R.string.download_started, Toast.LENGTH_LONG).show()
        }
    }

    private fun buildUserAgent(): String {
        return if (prefs.getBoolean("custom_user_agent", true)) {
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        } else {
            WebSettings.getDefaultUserAgent(this)
        }
    }

    private fun setupAddressBar() {
        binding.addressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                navigateToInput()
                true
            } else false
        }
        binding.addressBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) updateAddressBar(binding.webView.url ?: "")
        }
    }

    private fun setupNavigationButtons() {
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }
        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }
        binding.btnRefresh.setOnClickListener {
            if (binding.progressBar.visibility == View.VISIBLE) {
                binding.webView.stopLoading()
                onPageFinished(binding.webView.url ?: "")
            } else {
                binding.webView.reload()
            }
        }
        binding.btnHome.setOnClickListener { loadUrl(getSearchEngineHomeUrl()) }
        binding.btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_settings -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        true
                    }
                    R.id.action_share -> {
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, binding.webView.url)
                        }
                        startActivity(Intent.createChooser(i, getString(R.string.share_url)))
                        true
                    }
                    R.id.action_open_external -> {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(binding.webView.url)))
                        }
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    fun loadUrl(input: String) {
        binding.webView.loadUrl(formatUrl(input))
        hideKeyboard()
    }

    private fun navigateToInput() {
        val input = binding.addressBar.text?.toString()?.trim() ?: ""
        if (input.isNotEmpty()) loadUrl(input)
        hideKeyboard()
    }

    private fun formatUrl(input: String): String {
        val t = input.trim()
        return when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> getSearchQueryUrl(t)
        }
    }

    fun updateAddressBar(url: String) {
        val display = url.removePrefix("https://").removePrefix("http://")
        if (!binding.addressBar.isFocused) binding.addressBar.setText(display)
        binding.lockIcon.setImageResource(
            if (url.startsWith("https://")) R.drawable.ic_lock_24
            else R.drawable.ic_lock_open_24
        )
    }

    fun onPageStarted(url: String) {
        updateAddressBar(url)
        binding.btnRefresh.setImageResource(R.drawable.ic_close_24)
        updateNavigationState()
    }

    fun onPageFinished(url: String) {
        updateAddressBar(url)
        binding.progressBar.visibility = View.GONE
        binding.btnRefresh.setImageResource(R.drawable.ic_refresh_24)
        updateNavigationState()
    }

    fun onProgressChanged(progress: Int) {
        binding.progressBar.progress = progress
        binding.progressBar.visibility = if (progress < 100) View.VISIBLE else View.GONE
    }

    private fun updateNavigationState() {
        binding.btnBack.alpha = if (binding.webView.canGoBack()) 1.0f else 0.38f
        binding.btnForward.alpha = if (binding.webView.canGoForward()) 1.0f else 0.38f
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.addressBar.windowToken, 0)
        binding.addressBar.clearFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
