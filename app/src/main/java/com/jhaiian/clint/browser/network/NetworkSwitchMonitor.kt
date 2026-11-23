package com.jhaiian.clint.browser.network

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager

object NetworkSwitchMonitor {

    const val PREF_AUTO_RECOVERY = "network_auto_recovery"
    const val DEFAULT_AUTO_RECOVERY = true

    enum class Change { LOST, RESTORED, SWITCHED }

    enum class RestartRequest { NONE, AUTO, MANUAL }

    private const val DEBOUNCE_MS = 1500L
    private const val NO_NETWORK = ""

    private val TRANSPORTS = intArrayOf(
        NetworkCapabilities.TRANSPORT_WIFI,
        NetworkCapabilities.TRANSPORT_CELLULAR,
        NetworkCapabilities.TRANSPORT_VPN,
        NetworkCapabilities.TRANSPORT_ETHERNET,
        NetworkCapabilities.TRANSPORT_BLUETOOTH,
        NetworkCapabilities.TRANSPORT_WIFI_AWARE
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false
    private var prefs: SharedPreferences? = null

    private var network: Network? = null
    private var capabilities: NetworkCapabilities? = null
    private var linkProperties: LinkProperties? = null

    private var latest = NO_NETWORK
    private var settled: String? = null
    private var lastRoute: String? = null

    @Volatile
    var listener: ((Change) -> Unit)? = null

    @Volatile
    var restartRequest = RestartRequest.NONE

    @Volatile
    var engineRestarting = false

    fun isAutoRecoveryEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_AUTO_RECOVERY, DEFAULT_AUTO_RECOVERY)

    fun requestRestart(request: RestartRequest) {
        if (request == RestartRequest.AUTO && restartRequest == RestartRequest.MANUAL) return
        restartRequest = request
    }

    fun register(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        registered = true
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    this@NetworkSwitchMonitor.network = network
                    capabilities = null
                    linkProperties = null
                    refresh()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (network != this@NetworkSwitchMonitor.network) return
                    this@NetworkSwitchMonitor.capabilities = capabilities
                    refresh()
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    if (network != this@NetworkSwitchMonitor.network) return
                    this@NetworkSwitchMonitor.linkProperties = linkProperties
                    refresh()
                }

                override fun onLost(network: Network) {
                    if (network != this@NetworkSwitchMonitor.network) return
                    this@NetworkSwitchMonitor.network = null
                    capabilities = null
                    linkProperties = null
                    refresh()
                }
            }, mainHandler)
        }
    }

    private fun refresh() {
        latest = signature()
        mainHandler.removeCallbacks(settle)
        mainHandler.postDelayed(settle, DEBOUNCE_MS)
    }

    private fun signature(): String {
        val current = network ?: return NO_NETWORK
        val caps = capabilities
        val link = linkProperties
        val transports = if (caps == null) "" else TRANSPORTS.filter { caps.hasTransport(it) }.joinToString(",")
        val dns = link?.dnsServers?.map { it.hostAddress.orEmpty() }?.sorted()?.joinToString(",").orEmpty()
        return "${current.networkHandle}|$transports|${link?.interfaceName.orEmpty()}|$dns"
    }

    private val settle = Runnable {
        val now = latest
        val before = settled
        settled = now
        if (now != NO_NETWORK) {
            val previousRoute = lastRoute
            lastRoute = now
            if (before == null || now == before) return@Runnable
            val change = if (before == NO_NETWORK && (previousRoute == null || previousRoute == now)) Change.RESTORED else Change.SWITCHED
            dispatch(change)
        } else if (before != null && before != NO_NETWORK) {
            dispatch(Change.LOST)
        }
    }

    private fun dispatch(change: Change) {
        val enabled = prefs?.let { isAutoRecoveryEnabled(it) } ?: DEFAULT_AUTO_RECOVERY
        if (!enabled) return
        val target = listener
        if (target != null) {
            target(change)
        } else if (change == Change.SWITCHED) {
            requestRestart(RestartRequest.AUTO)
        }
    }
}
