package com.jhaiian.clint.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.content.FileProvider
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.ClintToast
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

internal fun MainActivity.setupImageLongPress(webView: WebView) {
    setupLinkLongPress(webView)
}

internal fun isStandaloneImagePage(url: String): Boolean {
    val lower = url.lowercase().substringBefore("?").substringBefore("#")
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".gif") ||
            lower.endsWith(".webp") || lower.endsWith(".bmp") ||
            lower.endsWith(".svg") || lower.endsWith(".avif")
}

internal fun MainActivity.showImageLongPressSheet(imageUrl: String, pageTitle: String, isStandalone: Boolean, referer: String = "") {
    val existing = supportFragmentManager.findFragmentByTag("image_long_press")
    if (existing != null && existing.isAdded) return
    ImageLongPressSheet.newInstance(imageUrl, pageTitle, isStandalone, referer)
        .show(supportFragmentManager, "image_long_press")
}

internal fun MainActivity.handleImageOpenInNewTab(imageUrl: String) {
    openNewTab(isIncognito = false, url = imageUrl)
}

internal fun MainActivity.handleImagePreview(imageUrl: String) {
    val existing = supportFragmentManager.findFragmentByTag("image_preview")
    if (existing != null && existing.isAdded) return
    ContentPreviewSheet.newInstanceForImage(imageUrl)
        .show(supportFragmentManager, "image_preview")
}

internal fun MainActivity.handleImageDownload(imageUrl: String, altText: String) {
    val userAgent = tabManager.activeTab?.webView?.settings?.userAgentString ?: buildUserAgent()
    val referer = tabManager.activeTab?.webView?.url ?: ""
    val cookies = CookieManager.getInstance().getCookie(imageUrl) ?: ""
    val filename = resolveImageFilename(imageUrl, altText)
    handleDownloadRequest(imageUrl, filename, userAgent, referer, cookies)
}

private fun resolveImageFilename(imageUrl: String, altText: String = ""): String {
    val knownExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif")

    fun extFromUrl(url: String): String {
        val seg = url.substringBefore("?").substringAfterLast(".")
            .lowercase().substringBefore("/")
        return if (seg in knownExts) seg else "jpg"
    }

    fun filenameFromUrl(url: String): String? {
        val path = url.substringBefore("?").substringAfterLast("/")
        val ext = path.substringAfterLast(".").lowercase()
        return if (ext in knownExts) path else null
    }

    fun sanitize(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(100)

    if (altText.isNotEmpty()) {
        val ext = filenameFromUrl(imageUrl)?.substringAfterLast(".")
            ?: run {
                try {
                    val uri = android.net.Uri.parse(imageUrl)
                    listOf("u", "url", "src", "img", "imgurl").firstNotNullOfOrNull { key ->
                        uri.getQueryParameter(key)?.let { param ->
                            java.net.URLDecoder.decode(param, "UTF-8").let { filenameFromUrl(it)?.substringAfterLast(".") }
                        }
                    }
                } catch (_: Exception) { null }
            }
            ?: extFromUrl(imageUrl)
        return "${sanitize(altText)}.$ext"
    }

    filenameFromUrl(imageUrl)?.let { return it }

    try {
        val uri = android.net.Uri.parse(imageUrl)
        for (key in listOf("u", "url", "src", "img", "imgurl")) {
            val param = uri.getQueryParameter(key) ?: continue
            val decoded = java.net.URLDecoder.decode(param, "UTF-8")
            filenameFromUrl(decoded)?.let { return it }
        }
    } catch (_: Exception) {}

    return "image_${System.currentTimeMillis()}.${extFromUrl(imageUrl)}"
}

internal fun MainActivity.handleImageCopy(imageUrl: String) {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    Thread {
        try {
            val request = Request.Builder().url(imageUrl).build()
            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes() ?: run {
                runOnUiThread { ClintToast.show(this, getString(R.string.image_copy_failed), R.drawable.ic_copy_24) }
                return@Thread
            }
            val ext = imageUrl.substringAfterLast(".").substringBefore("?").lowercase().let { e ->
                if (e in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) e else "jpg"
            }
            val mimeType = when (ext) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                else -> "image/jpeg"
            }
            val cacheDir = File(cacheDir, "image_cache").also { it.mkdirs() }
            val file = File(cacheDir, "copied_image.$ext")
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newUri(contentResolver, getString(R.string.image_copy), uri)
                clipboard.setPrimaryClip(clip)
                ClintToast.show(this, getString(R.string.image_copied), R.drawable.ic_copy_24)
            }
        } catch (_: Exception) {
            runOnUiThread { ClintToast.show(this, getString(R.string.image_copy_failed), R.drawable.ic_copy_24) }
        }
    }.start()
}

internal fun MainActivity.handleImageShare(imageUrl: String) {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    Thread {
        try {
            val request = Request.Builder().url(imageUrl).build()
            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes()
            if (bytes != null) {
                val ext = imageUrl.substringAfterLast(".").substringBefore("?").lowercase().let { e ->
                    if (e in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) e else "jpg"
                }
                val mimeType = when (ext) {
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "bmp" -> "image/bmp"
                    else -> "image/jpeg"
                }
                val cacheDir = File(cacheDir, "image_cache").also { it.mkdirs() }
                val file = File(cacheDir, "shared_image.$ext")
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                runOnUiThread {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.image_share)))
                }
            } else {
                shareImageUrl(imageUrl)
            }
        } catch (_: Exception) {
            runOnUiThread { shareImageUrl(imageUrl) }
        }
    }.start()
}

private fun MainActivity.shareImageUrl(imageUrl: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, imageUrl)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.image_share)))
}
