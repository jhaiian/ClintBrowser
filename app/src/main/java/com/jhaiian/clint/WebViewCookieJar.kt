package com.jhaiian.clint

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebViewCookieJar : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cookieManager = CookieManager.getInstance()
        cookies.forEach { cookie ->
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieString = CookieManager.getInstance().getCookie(url.toString())
            ?: return emptyList()
        return cookieString.split(";")
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                Cookie.Builder()
                    .name(trimmed.substringBefore("=").trim())
                    .value(trimmed.substringAfter("=", "").trim())
                    .domain(url.host)
                    .build()
            }
    }
}
