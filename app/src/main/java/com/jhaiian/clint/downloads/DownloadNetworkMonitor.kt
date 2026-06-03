package com.jhaiian.clint.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.preference.PreferenceManager
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import java.util.concurrent.ConcurrentHashMap

internal object DownloadNetworkMonitor {

    internal val unmeteredPausedIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    internal val networkWaitingIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private var networkCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val ctx = ClintDownloadManager.appContext ?: return
            val toResume = networkWaitingIds.toList()
            networkWaitingIds.clear()
            toResume.forEach { id ->
                val item = synchronized(ClintDownloadManager.downloads) {
                    ClintDownloadManager.downloads.find { it.id == id }
                } ?: return@forEach
                if (item.status == DownloadStatus.PAUSED && item.waitingForNetwork) {
                    item.waitingForNetwork = false
                    ClintDownloadManager.resume(ctx, id)
                }
            }
            ClintDownloadManager.tryDequeueNext(ctx)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val ctx = ClintDownloadManager.appContext ?: return
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                val toResume = unmeteredPausedIds.toList()
                unmeteredPausedIds.clear()
                toResume.forEach { id ->
                    val item = synchronized(ClintDownloadManager.downloads) {
                        ClintDownloadManager.downloads.find { it.id == id }
                    } ?: return@forEach
                    if (item.status == DownloadStatus.PAUSED) {
                        item.waitingForUnmetered = false
                        ClintDownloadManager.resume(ctx, id)
                    }
                }
                ClintDownloadManager.tryDequeueNext(ctx)
            } else {
                pauseActiveForMetered(ctx)
            }
        }

        override fun onLost(network: Network) {
            val ctx = ClintDownloadManager.appContext ?: return
            pauseActiveForMetered(ctx)
        }
    }

    fun register(context: Context) {
        if (!networkCallbackRegistered) {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isUnmeteredOnlyEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(
            DownloadSettingsFragment.PREF_UNMETERED_ONLY,
            DownloadSettingsFragment.DEFAULT_UNMETERED_ONLY
        )
    }

    fun isNetworkUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun pauseActiveForMetered(context: Context) {
        val active = synchronized(ClintDownloadManager.downloads) {
            ClintDownloadManager.downloads.filter {
                it.unmeteredOnly && (
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.CONNECTING ||
                    it.status == DownloadStatus.ALLOCATING
                )
            }
        }
        active.forEach { item ->
            item.waitingForUnmetered = true
            unmeteredPausedIds.add(item.id)
            ClintDownloadManager.pause(context, item.id)
        }
    }

    fun onUnmeteredOnlyChanged(context: Context, enabled: Boolean) {
        val ctx = context.applicationContext
        if (!enabled) {
            val toResume = unmeteredPausedIds.toList()
            unmeteredPausedIds.clear()
            toResume.forEach { id ->
                val item = synchronized(ClintDownloadManager.downloads) {
                    ClintDownloadManager.downloads.find { it.id == id }
                } ?: return@forEach
                if (item.status == DownloadStatus.PAUSED) {
                    item.waitingForUnmetered = false
                    ClintDownloadManager.resume(ctx, id)
                }
            }
            ClintDownloadManager.tryDequeueNext(ctx)
        } else {
            if (!isNetworkUnmetered(ctx)) {
                pauseActiveForMetered(ctx)
            }
        }
    }
}
