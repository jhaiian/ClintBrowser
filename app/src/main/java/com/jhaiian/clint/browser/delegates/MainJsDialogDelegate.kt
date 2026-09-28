package com.jhaiian.clint.browser.delegates

import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.dialogs.JsDialogRequest

internal fun MainActivity.isCustomJsDialogsEnabled(): Boolean =
    prefs.getBoolean("custom_js_dialogs_enabled", true)

internal fun MainActivity.onJsDialogRequest(request: JsDialogRequest) {
    uiState.jsDialogRequest?.cancel()
    uiState.jsDialogRequest = request
}
