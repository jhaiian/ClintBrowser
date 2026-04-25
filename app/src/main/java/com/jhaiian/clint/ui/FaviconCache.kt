package com.jhaiian.clint.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

object FaviconCache {

    private val memoryCache = HashMap<String, Bitmap>()
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun faviconUrlFor(pageUrl: String): String {
        return runCatching {
            val host = URL(pageUrl).host
            "https://$host/favicon.ico"
        }.getOrDefault("")
    }

    fun load(context: Context, faviconUrl: String, onResult: (Bitmap?) -> Unit) {
        if (faviconUrl.isEmpty()) {
            onResult(null)
            return
        }
        val key = keyFor(faviconUrl)
        memoryCache[key]?.let { onResult(it); return }
        val file = diskFile(context.applicationContext, key)
        if (file.exists()) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) {
                memoryCache[key] = bmp
                onResult(bmp)
                return
            }
        }
        executor.execute {
            val bmp = tryFetch(faviconUrl) ?: tryFetch(fallbackUrlFor(faviconUrl))
            if (bmp != null) {
                memoryCache[key] = bmp
                runCatching {
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            mainHandler.post { onResult(bmp) }
        }
    }

    fun loadMemoryOnly(faviconUrl: String, onResult: (Bitmap?) -> Unit) {
        if (faviconUrl.isEmpty()) {
            onResult(null)
            return
        }
        val key = keyFor(faviconUrl)
        memoryCache[key]?.let { onResult(it); return }
        executor.execute {
            val bmp = tryFetch(faviconUrl) ?: tryFetch(fallbackUrlFor(faviconUrl))
            if (bmp != null) memoryCache[key] = bmp
            mainHandler.post { onResult(bmp) }
        }
    }

    fun evict(context: Context, pageUrl: String) {
        val faviconUrl = faviconUrlFor(pageUrl)
        if (faviconUrl.isEmpty()) return
        val key = keyFor(faviconUrl)
        memoryCache.remove(key)
        diskFile(context.applicationContext, key).delete()
    }

    private fun tryFetch(url: String): Bitmap? {
        if (url.isEmpty()) return null
        return runCatching {
            val conn = URL(url).openConnection()
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            BitmapFactory.decodeStream(conn.getInputStream())
        }.getOrNull()
    }

    private fun fallbackUrlFor(faviconUrl: String): String {
        return runCatching {
            val host = URL(faviconUrl).host
            "https://icons.duckduckgo.com/ip3/$host.ico"
        }.getOrDefault("")
    }

    private fun keyFor(url: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun diskFile(context: Context, key: String): File {
        val dir = File(context.cacheDir, "favicons")
        dir.mkdirs()
        return File(dir, "$key.png")
    }
}
