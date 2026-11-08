package com.jhaiian.clint.browser.delegates

import com.jhaiian.clint.browser.MainActivity

private const val FIND_DEBOUNCE_MS = 250L

internal fun MainActivity.openFindBar() {
    if (uiState.searchOverlayOpen) closeSearchOverlay()
    uiState.findBarOpen = true
    uiState.findMatchOrdinal = 0
    uiState.findMatchCount = 0
    refreshFindMatches()
}

internal fun MainActivity.closeFindBar() {
    dismissFindBar()
    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
    imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
}

/** Clears find state and matches without touching the keyboard. */
internal fun MainActivity.dismissFindBar() {
    findHandler.removeCallbacksAndMessages(null)
    uiState.findBarOpen = false
    uiState.findMatchOrdinal = 0
    uiState.findMatchCount = 0
    tabManager.activeTab?.webView?.clearMatches()
}

internal fun MainActivity.onFindQueryChanged(query: String) {
    uiState.findQuery = query
    findHandler.removeCallbacksAndMessages(null)
    findHandler.postDelayed({ runFind(query) }, FIND_DEBOUNCE_MS)
}

internal fun MainActivity.onFindNext(backwards: Boolean) {
    tabManager.activeTab?.webView?.findNext(!backwards)
}

/**
 * Re-runs the current query on the active tab, e.g. after a page load or a
 * tab switch while the find bar is open.
 */
internal fun MainActivity.refreshFindMatches() {
    if (!uiState.findBarOpen) return
    val query = uiState.findQuery
    if (query.isBlank()) return
    uiState.findMatchOrdinal = 0
    uiState.findMatchCount = 0
    tabManager.activeTab?.webView?.findAllAsync(query)
}

private fun MainActivity.runFind(query: String) {
    uiState.findMatchOrdinal = 0
    uiState.findMatchCount = 0
    val webView = tabManager.activeTab?.webView ?: return
    if (query.isBlank()) webView.clearMatches() else webView.findAllAsync(query)
}
