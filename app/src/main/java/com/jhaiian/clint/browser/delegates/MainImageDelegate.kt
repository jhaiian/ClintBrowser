package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.sheets.*
import com.jhaiian.clint.browser.MainActivity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebView
import androidx.core.content.FileProvider
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.ClintToast
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private fun dataUriBytes(dataUri: String): ByteArray? {
    val commaIdx = dataUri.indexOf(",")
    if (commaIdx < 0) return null
    val header = dataUri.substring(0, commaIdx)
    val content = dataUri.substring(commaIdx + 1)
    return if (header.contains(";base64")) {
        try { android.util.Base64.decode(content, android.util.Base64.DEFAULT) } catch (_: Exception) { null }
    } else {
        try {
            java.net.URLDecoder.decode(content, "UTF-8").toByteArray(Charsets.UTF_8)
        } catch (_: Exception) { content.toByteArray(Charsets.UTF_8) }
    }
}

private fun dataUriMimeType(dataUri: String): String {
    val after = dataUri.removePrefix("data:")
    val end = after.indexOfFirst { it == ';' || it == ',' }
    return if (end > 0) after.substring(0, end).trim().lowercase() else "image/jpeg"
}

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
    if (imageUrl.startsWith("data:")) {
        val commaIdx = imageUrl.indexOf(",")
        if (commaIdx < 0) return
        val header = imageUrl.substring(0, commaIdx)
        val content = imageUrl.substring(commaIdx + 1)
        val mimeType = dataUriMimeType(imageUrl)
        val filename = resolveImageFilename(imageUrl, altText)
        val b64 = if (header.contains(";base64")) {
            content
        } else {
            try {
                val bytes = java.net.URLDecoder.decode(content, "UTF-8").toByteArray(Charsets.UTF_8)
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (_: Exception) { return }
        }
        showDownloadDialogForBlob(b64, filename, mimeType)
        return
    }
    val userAgent = tabManager.activeTab?.webView?.settings?.userAgentString ?: buildUserAgent()
    val referer = tabManager.activeTab?.webView?.url ?: ""
    val cookies = CookieManager.getInstance().getCookie(imageUrl) ?: ""
    val filename = resolveImageFilename(imageUrl, altText)
    showDownloadDialog(imageUrl, filename, userAgent, referer, cookies)
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

    if (imageUrl.startsWith("data:")) {
        val mimeType = dataUriMimeType(imageUrl)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
        return if (altText.isNotEmpty()) "${sanitize(altText)}.$ext" else "image_${System.currentTimeMillis()}.$ext"
    }

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
    if (imageUrl.startsWith("data:")) {
        Thread {
            val bytes = dataUriBytes(imageUrl) ?: run {
                runOnUiThread { ClintToast.show(this, getString(R.string.image_copy_failed), R.drawable.ic_copy_24) }
                return@Thread
            }
            val mimeType = dataUriMimeType(imageUrl)
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            try {
                val cacheDir = File(cacheDir, "image_cache").also { it.mkdirs() }
                val file = File(cacheDir, "copied_image.$ext")
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                runOnUiThread {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newUri(contentResolver, getString(R.string.image_copy), uri)
                    clipboard.setPrimaryClip(clip)
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                        ClintToast.show(this, getString(R.string.image_copied), R.drawable.ic_copy_24)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { ClintToast.show(this, getString(R.string.image_copy_failed), R.drawable.ic_copy_24) }
            }
        }.start()
        return
    }
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
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                    ClintToast.show(this, getString(R.string.image_copied), R.drawable.ic_copy_24)
                }
            }
        } catch (_: Exception) {
            runOnUiThread { ClintToast.show(this, getString(R.string.image_copy_failed), R.drawable.ic_copy_24) }
        }
    }.start()
}

internal fun MainActivity.handleImageShare(imageUrl: String) {
    if (imageUrl.startsWith("data:")) {
        Thread {
            val bytes = dataUriBytes(imageUrl)
            if (bytes != null) {
                val mimeType = dataUriMimeType(imageUrl)
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
                try {
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
                } catch (_: Exception) {
                    runOnUiThread { shareImageUrl(imageUrl) }
                }
            } else {
                runOnUiThread { shareImageUrl(imageUrl) }
            }
        }.start()
        return
    }
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
                runOnUiThread { shareImageUrl(imageUrl) }
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
