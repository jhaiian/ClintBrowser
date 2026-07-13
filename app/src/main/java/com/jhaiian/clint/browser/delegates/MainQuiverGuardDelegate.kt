package com.jhaiian.clint.browser.delegates

import androidx.lifecycle.lifecycleScope
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.quiver.engine.BlockedRequestCounter
import com.jhaiian.clint.quiver.engine.QuiverGuardWebIntegration
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.settings.sitepermissions.SitePermissionManager
import com.jhaiian.clint.tabs.BrowserTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.withContext

// Subscribes to the per-tab blocked-request count so the UI badge stays in
// sync whenever the active tab changes or a new request is intercepted.
internal fun MainActivity.observeQuiverGuardCounter() {
    BlockedRequestCounter.activeTabCount
        .launchIn(lifecycleScope)
}

// Loads/memory-maps the compiled filter database. Can involve real disk I/O
// on first load, so it runs on Dispatchers.IO rather than blocking onCreate.
internal fun MainActivity.initializeQuiverGuardEngine() {
    lifecycleScope.launch(Dispatchers.IO) {
        QuiverGuardWebIntegration.initialize(this@initializeQuiverGuardEngine)
    }
}

// Called each time a new page starts loading in a tab. Determines whether
// Quiver Guard is both globally enabled and not excepted for the current host,
// resets the tab's blocked-request counter either way (a fresh navigation always
// starts a fresh count, whether or not this particular page ends up filtered),
// and - if not excepted - both registers the document-start script for this
// navigation's own reload of that mechanism and evaluates the same script
// immediately via evaluateJavascript. The latter is largely redundant with
// QuiverGuardWebIntegration.installEarly's eager registration (see its kdoc) on
// a normal load, but costs little given the script is idempotent, and gives an
// extra, earlier injection attempt on top of whatever document-start manages -
// worthwhile insurance in a codepath that's already had more than one subtle
// timing bug.
//
// The exception lookup (a SQLite query) and cosmetic script computation (a walk
// over the compiled filter database) are real I/O/CPU work, so both run on
// Dispatchers.IO via a coroutine rather than on the UI thread - only the final
// WebView calls happen after resuming on the main dispatcher. Any in-flight
// coroutine from a previous, now-superseded navigation for this tab is
// cancelled first: if it's still suspended on the IO work when cancelled, it
// never reaches the code that would apply its (stale) script to the new page.
internal fun MainActivity.onQuiverGuardPageStarted(tab: BrowserTab, url: String) {
    val isEnabled = prefs.getBoolean("quiver_guard_enabled", false)
    if (!isEnabled) return
    if (!url.startsWith("http://") && !url.startsWith("https://")) return

    quiverGuardJobs.remove(tab.id)?.cancel()
    quiverGuardJobs[tab.id] = lifecycleScope.launch {
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()

        // Sites added to the Quiver Guard exception list are skipped entirely.
        val isExcepted = withContext(Dispatchers.IO) {
            host != null && SitePermissionManager.getState(
                this@onQuiverGuardPageStarted, host, SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
            ) != null
        }

        // Every new navigation starts a fresh count, regardless of whether this
        // particular page turns out to be excepted - otherwise a count from a
        // previous, non-excepted page (or tab reuse) keeps showing in the menu
        // badge even once nothing is actually being blocked here anymore.
        BlockedRequestCounter.resetTab(tab.id)

        if (isExcepted) {
            // Any previously registered document-start handler for this tab
            // is removed so residual scripts from earlier pages are not applied.
            quiverGuardScriptHandlers.remove(tab.id)
            return@launch
        }

        val script = withContext(Dispatchers.IO) {
            QuiverGuardWebIntegration.buildDocumentStartScript(this@onQuiverGuardPageStarted, url, true)
        }

        if (script != null) {
            QuiverGuardWebIntegration.applyDocumentStartScript(
                tab.webView, quiverGuardScriptHandlers, tab.id, script
            )
            QuiverGuardWebIntegration.applyCosmeticFilterScript(tab.webView, script)
        }
    }
}

// Called once the page has finished loading, re-applying cosmetic filters via
// evaluateJavascript regardless of document-start support. On modern devices
// this is a deliberately redundant extra pass on top of onQuiverGuardPageStarted
// and the eager document-start registration (see QuiverGuardWebIntegration.
// installEarly's kdoc) - safe because the script is idempotent, and worthwhile
// insurance against anything a page's own late-running script might have
// inserted after the earlier passes ran. On devices without document-start
// support, this remains the only injection mechanism, same as before.
//
// As with onQuiverGuardPageStarted, the exception lookup and script computation
// run on Dispatchers.IO, and any in-flight job for this tab is cancelled before
// starting a new one.
internal fun MainActivity.onQuiverGuardPageFinished(tab: BrowserTab, url: String) {
    val isEnabled = prefs.getBoolean("quiver_guard_enabled", false)
    if (!isEnabled) return
    if (!url.startsWith("http://") && !url.startsWith("https://")) return

    quiverGuardJobs.remove(tab.id)?.cancel()
    quiverGuardJobs[tab.id] = lifecycleScope.launch {
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()
        val isExcepted = withContext(Dispatchers.IO) {
            host != null && SitePermissionManager.getState(
                this@onQuiverGuardPageFinished, host, SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
            ) != null
        }
        if (isExcepted) return@launch

        val script = withContext(Dispatchers.IO) {
            QuiverGuardWebIntegration.buildCosmeticFilterScript(this@onQuiverGuardPageFinished, url, true)
        } ?: return@launch

        QuiverGuardWebIntegration.applyCosmeticFilterScript(tab.webView, script)
    }
}

// Removes all tracking state for a tab when it is closed to prevent memory leaks.
// The in-flight coroutine (if any) is cancelled and script handlers must be
// explicitly removed to unregister the document-start injection from the WebView.
internal fun MainActivity.onQuiverGuardTabClosed(tab: BrowserTab) {
    quiverGuardJobs.remove(tab.id)?.cancel()
    quiverGuardScriptHandlers.remove(tab.id)
    BlockedRequestCounter.removeTab(tab.id)
}

// Responds to the user toggling Quiver Guard on or off. When disabled, all
// registered document-start handlers are cleared so no further injection occurs.
// When enabled, the engine is initialised and the current tab is reloaded so
// the new filter state takes effect immediately. Initialisation can involve
// loading and memory-mapping the compiled filter database from disk, so it
// runs on Dispatchers.IO; the reload is only triggered once that completes,
// back on the main dispatcher.
internal fun MainActivity.onQuiverGuardEnabled(enabled: Boolean) {
    if (!enabled) {
        quiverGuardScriptHandlers.clear()
        reloadActiveTabIfWeb()
        return
    }
    lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            QuiverGuardWebIntegration.initialize(this@onQuiverGuardEnabled)
        }
        reloadActiveTabIfWeb()
    }
}

private fun MainActivity.reloadActiveTabIfWeb() {
    tabManager.activeTab?.let { tab ->
        val url = tab.webView.url ?: return@let
        if (url.startsWith("http://") || url.startsWith("https://")) {
            tab.webView.reload()
        }
    }
}
