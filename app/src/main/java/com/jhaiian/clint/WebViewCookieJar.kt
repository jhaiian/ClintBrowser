package com.jhaiian.clint

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebViewCookieJar : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
    override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
}
