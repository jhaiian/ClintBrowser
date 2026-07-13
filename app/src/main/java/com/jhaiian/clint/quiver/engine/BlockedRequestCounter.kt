package com.jhaiian.clint.quiver.engine

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// Tracks how many network requests Quiver Guard has blocked, both globally and
// per open tab. The per-tab counters are kept in a ConcurrentHashMap so they
// can be updated from the IO thread (where shouldInterceptRequest runs) without
// holding a lock, while StateFlow updates are posted back to the main thread
// for safe UI observation.
object BlockedRequestCounter {

    private val mainHandler = Handler(Looper.getMainLooper())

    // One AtomicLong per tab ID so concurrent intercepts from different tabs
    // never contend on the same counter object.
    private val tabCounters = ConcurrentHashMap<String, AtomicLong>()

    private val _globalCount = MutableStateFlow(0L)
    val globalCount: StateFlow<Long> = _globalCount.asStateFlow()

    // Exposes the block count for whichever tab is currently active, updated
    // whenever the active tab changes or a new request is intercepted.
    private val _activeTabCount = MutableStateFlow(0L)
    val activeTabCount: StateFlow<Long> = _activeTabCount.asStateFlow()

    private var activeTabId: String? = null

    // Called from the WebViewClient's shouldInterceptRequest on a background thread.
    // The global counter is updated optimistically without a lock; a small race
    // window on the global value is acceptable because it is used only for display.
    fun increment(tabId: String) {
        val counter = tabCounters.getOrPut(tabId) { AtomicLong(0L) }
        counter.incrementAndGet()
        val newGlobal = _globalCount.value + 1L
        mainHandler.post {
            _globalCount.value = newGlobal
            if (tabId == activeTabId) {
                _activeTabCount.value = counter.get()
            }
        }
    }

    // Called when the user switches tabs so the badge reflects the new tab's count.
    fun setActiveTab(tabId: String?) {
        activeTabId = tabId
        val count = if (tabId != null) tabCounters[tabId]?.get() ?: 0L else 0L
        mainHandler.post { _activeTabCount.value = count }
    }

    // Called at the start of each page load to clear the per-tab counter.
    // Resetting on page start rather than page finish avoids showing stale
    // counts from the previous page while the new one is loading.
    fun resetTab(tabId: String) {
        tabCounters[tabId]?.set(0L)
        if (tabId == activeTabId) {
            mainHandler.post { _activeTabCount.value = 0L }
        }
    }

    // Called when a tab is closed to release the counter entry and clear the
    // active-tab display if the closed tab was the active one.
    fun removeTab(tabId: String) {
        tabCounters.remove(tabId)
        if (tabId == activeTabId) {
            mainHandler.post { _activeTabCount.value = 0L }
        }
    }

    fun getTabCount(tabId: String): Long = tabCounters[tabId]?.get() ?: 0L

    // Formats a count for display in a compact badge (e.g. "1.2k", "3M").
    fun formatCount(count: Long): String = when {
        count >= 1_000_000L -> "${count / 1_000_000L}M"
        count >= 1_000L -> "${count / 1_000L}k"
        else -> count.toString()
    }
}
