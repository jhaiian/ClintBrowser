package com.jhaiian.clint.quiver.engine

import androidx.webkit.ScriptHandler
import java.util.concurrent.ConcurrentHashMap

// Thread-safe store for the ScriptHandler objects returned by
// WebViewCompat.addDocumentStartJavaScript. Each handler represents a
// registered document-start script for one tab. When a new page loads or a
// tab is closed, the old handler must be explicitly removed to unregister the
// script from the WebView; failing to do so would cause stale cosmetic filters
// to continue injecting into pages where they no longer apply.
class ScriptHandlerStore {
    private val handlers = ConcurrentHashMap<String, ScriptHandler>()

    // Registers a new handler for the given tab, removing any previously
    // registered handler first. This is called at the start of each navigation
    // so the injected script always matches the current page's filter set.
    fun put(tabId: String, handler: ScriptHandler) {
        remove(tabId)
        handlers[tabId] = handler
    }

    // Removes and revokes the handler for the given tab. The ScriptHandler.remove()
    // call unregisters the script from the WebView so it no longer fires on
    // subsequent same-tab navigations.
    fun remove(tabId: String) {
        handlers.remove(tabId)?.remove()
    }

    // Removes and revokes all registered handlers. Called when Quiver Guard is
    // disabled so no further document-start injection occurs in any tab.
    fun clear() {
        val all = handlers.keys().toList()
        for (key in all) remove(key)
    }
}
