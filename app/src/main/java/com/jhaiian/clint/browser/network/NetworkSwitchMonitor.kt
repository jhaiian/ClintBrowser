package com.jhaiian.clint.browser.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Watches the default network (including VPNs that apply to this app) and reports a debounced
 * "network switched" event when the route the WebViews use actually changes: a different network
 * becomes default, a VPN comes up or goes away, or the default network's interface / DNS servers change.
 *
 * WebView keeps process-wide network state (pooled HTTP/2 + QUIC sessions, its host cache).
 * When a VPN is toggled, or a VPN app restarts its tunnel to switch servers, that state can stay
 * bound to the old route, and pages on the affected origins stop loading until the process is
 * restarted. Reloading does not help because the reload reuses the same stale state.
 */
class NetworkSwitchMonitor(
    context: Context,
    private val onSwitched: (Change) -> Unit
) {
    data class Snapshot(
        val networkId: String,
        val isVpn: Boolean,
        val interfaceName: String?,
        val dnsServers: List<String>
    )

    data class Change(val from: Snapshot?, val to: Snapshot?) {
        val vpnToggled: Boolean get() = (from?.isVpn ?: false) != (to?.isVpn ?: false)
    }

    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var registered = false

    /** Last snapshot that was reported (or the baseline taken at registration). */
    private var committed: Snapshot? = null
    private var baselineTaken = false
    private var pending: Snapshot? = null
    private var pendingLost = false

    private val caps = HashMap<Network, NetworkCapabilities>()
    private val links = HashMap<Network, LinkProperties>()
    private var current: Network? = null

    private val emit = Runnable {
        val next = if (pendingLost) null else pending
        val prev = committed
        if (!baselineTaken) {
            baselineTaken = true
            committed = next
            return@Runnable
        }
        // Losing the network and getting the very same one back is not a route change.
        if (next == null || next == prev) {
            if (next != null) committed = next
            return@Runnable
        }
        committed = next
        Log.i(TAG, "network switched: $prev -> $next")
        onSwitched(Change(prev, next))
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post { current = network; pendingLost = false; update() }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            main.post { caps[network] = networkCapabilities; if (network == current) update() }
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            main.post { links[network] = linkProperties; if (network == current) update() }
        }

        override fun onLost(network: Network) {
            main.post {
                caps.remove(network); links.remove(network)
                if (network == current) {
                    current = null
                    pendingLost = true
                    schedule()
                }
            }
        }
    }

    private fun update() {
        val network = current ?: return
        val c = caps[network] ?: cm?.getNetworkCapabilities(network)
        val l = links[network] ?: cm?.getLinkProperties(network)
        pending = Snapshot(
            networkId = network.toString(),
            isVpn = c?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            interfaceName = l?.interfaceName,
            dnsServers = l?.dnsServers?.map { it.hostAddress.orEmpty() }?.sorted().orEmpty()
        )
        schedule()
    }

    private fun schedule() {
        main.removeCallbacks(emit)
        main.postDelayed(emit, if (baselineTaken) DEBOUNCE_MS else BASELINE_MS)
    }

    fun start() {
        if (registered || cm == null) return
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onSuccess { registered = true }
            .onFailure { Log.w(TAG, "registerDefaultNetworkCallback failed", it) }
    }

    fun stop() {
        if (!registered || cm == null) return
        runCatching { cm.unregisterNetworkCallback(callback) }
        registered = false
        main.removeCallbacks(emit)
    }

    companion object {
        private const val TAG = "ClintNetSwitch"
        private const val DEBOUNCE_MS = 1500L
        private const val BASELINE_MS = 800L
    }
}
