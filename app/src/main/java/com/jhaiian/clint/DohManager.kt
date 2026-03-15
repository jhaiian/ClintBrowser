package com.jhaiian.clint

import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object DohManager {

    const val MODE_OFF = "off"
    const val MODE_DEFAULT = "default"
    const val MODE_INCREASED = "increased"
    const val MODE_MAX = "max"

    const val PROVIDER_CLOUDFLARE = "cloudflare"
    const val PROVIDER_QUAD9 = "quad9"

    @Volatile private var cachedClient: OkHttpClient? = null
    @Volatile private var cachedMode: String = MODE_OFF
    @Volatile private var cachedProvider: String = PROVIDER_CLOUDFLARE

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun getClient(prefs: SharedPreferences): OkHttpClient? {
        val mode = prefs.getString("doh_mode", MODE_OFF) ?: MODE_OFF
        if (mode == MODE_OFF) { cachedClient = null; return null }
        val provider = prefs.getString("doh_provider", PROVIDER_CLOUDFLARE) ?: PROVIDER_CLOUDFLARE
        if (cachedClient != null && cachedMode == mode && cachedProvider == provider) {
            return cachedClient
        }
        return buildClient(provider).also {
            cachedClient = it
            cachedMode = mode
            cachedProvider = provider
        }
    }

    fun invalidate() { cachedClient = null }

    private fun buildClient(provider: String): OkHttpClient {
        val (dohUrl, bootstrapIps) = when (provider) {
            PROVIDER_QUAD9 -> Pair(
                "https://dns.quad9.net/dns-query",
                listOf(InetAddress.getByName("9.9.9.9"), InetAddress.getByName("149.112.112.112"))
            )
            else -> Pair(
                "https://cloudflare-dns.com/dns-query",
                listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
            )
        }
        val dns = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(dohUrl.toHttpUrl())
            .bootstrapDnsHosts(bootstrapIps)
            .build()
        return OkHttpClient.Builder()
            .dns(dns)
            .cookieJar(WebViewCookieJar())
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
