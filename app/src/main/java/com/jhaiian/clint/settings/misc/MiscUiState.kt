package com.jhaiian.clint.settings.misc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MiscUiState(
    initialDefaultBrowserSummary: String,
    initialNetworkAutoRecovery: Boolean,
    val hideStatusBar: Boolean,
val hideSystemNavigation: Boolean
) {
    var defaultBrowserSummary by mutableStateOf(initialDefaultBrowserSummary)
    var networkAutoRecovery by mutableStateOf(initialNetworkAutoRecovery)
    var rerunSetupConfirmDialogOpen by mutableStateOf(false)
    var restartEngineConfirmDialogOpen by mutableStateOf(false)
}
