package com.jhaiian.clint.network

import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object DohManager {

    const val MODE_OFF = "off"
    const val MODE_DEFAULT = "default"
    const val MODE_INCREASED = "increased"
    const val MODE_MAX = "max"

    const val PROVIDER_CLOUDFLARE = "cloudflare"
    const val PROVIDER_QUAD9 = "quad9"

    private const val CACHE_MAX_SIZE = 512

    @Volatile private var dnsClient: OkHttpClient? = null
    @Volatile private var cachedMode: String = MODE_OFF
    @Volatile private var cachedProvider: String = PROVIDER_CLOUDFLARE

    private val executor = ThreadPoolExecutor(
        2, 4,
        60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(64),
        ThreadPoolExecutor.DiscardPolicy()
    )

    private val resolvedCache: MutableSet<String> =
        Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

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
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun getOrBuild(prefs: SharedPreferences): OkHttpClient? {
        val mode = prefs.getString("doh_mode", MODE_OFF) ?: MODE_OFF
        if (mode == MODE_OFF) { dnsClient = null; return null }
        val provider = prefs.getString("doh_provider", PROVIDER_CLOUDFLARE) ?: PROVIDER_CLOUDFLARE
        if (dnsClient != null && cachedMode == mode && cachedProvider == provider) return dnsClient
        resolvedCache.clear()
        return runCatching { buildClient(provider) }.getOrNull()?.also {
            dnsClient = it
            cachedMode = mode
            cachedProvider = provider
        }
    }

    fun preResolveDns(host: String, prefs: SharedPreferences) {
        if (host in resolvedCache) return
        val client = getOrBuild(prefs) ?: return
        executor.submit {
            runCatching {
                client.dns.lookup(host)
                if (resolvedCache.size < CACHE_MAX_SIZE) {
                    resolvedCache.add(host)
                }
            }
        }
    }

    fun invalidate() {
        dnsClient = null
        resolvedCache.clear()
    }
}
