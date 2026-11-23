package com.jhaiian.clint.settings.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BrowserSettingsUiState(
    initialDeleteInactiveTabs: String,
    initialSearchEngine: String,
    initialSearchSuggestionsApi: String,
    initialIncognitoSearchEngine: String,
    initialIncognitoSearchSuggestionsApi: String,
    initialJavascriptEnabled: Boolean,
    initialBackgroundPlayback: Boolean,
    initialHideStatusBar: Boolean,
    initialHideSystemNavigation: Boolean,
    initialIncognitoSearchHistory: Boolean,
    initialCustomSelectMenus: Boolean,
    initialCustomJsDialogs: Boolean,
    initialCustomHttpAuth: Boolean,
    initialCustomSslWarnings: Boolean,
    initialCustomDateTimePickers: Boolean,
    initialCustomColorPicker: Boolean,
    initialRememberBrowsingMode: Boolean
) {
    var deleteInactiveTabs by mutableStateOf(initialDeleteInactiveTabs)
    var searchEngine by mutableStateOf(initialSearchEngine)
    var searchSuggestionsApi by mutableStateOf(initialSearchSuggestionsApi)
    var incognitoSearchEngine by mutableStateOf(initialIncognitoSearchEngine)
    var incognitoSearchSuggestionsApi by mutableStateOf(initialIncognitoSearchSuggestionsApi)
    var javascriptEnabled by mutableStateOf(initialJavascriptEnabled)
    var backgroundPlayback by mutableStateOf(initialBackgroundPlayback)
    var hideStatusBar by mutableStateOf(initialHideStatusBar)
    var hideSystemNavigation by mutableStateOf(initialHideSystemNavigation)
    var incognitoSearchHistory by mutableStateOf(initialIncognitoSearchHistory)
    var customSelectMenus by mutableStateOf(initialCustomSelectMenus)
    var customJsDialogs by mutableStateOf(initialCustomJsDialogs)
    var customHttpAuth by mutableStateOf(initialCustomHttpAuth)
    var customSslWarnings by mutableStateOf(initialCustomSslWarnings)
    var customDateTimePickers by mutableStateOf(initialCustomDateTimePickers)
    var customColorPicker by mutableStateOf(initialCustomColorPicker)
    var rememberBrowsingMode by mutableStateOf(initialRememberBrowsingMode)
    var deleteInactiveTabsDialogOpen by mutableStateOf(false)
    var searchEngineDialogOpen by mutableStateOf(false)
    var searchSuggestionsApiDialogOpen by mutableStateOf(false)
    var incognitoSearchEngineDialogOpen by mutableStateOf(false)
    var incognitoSearchSuggestionsApiDialogOpen by mutableStateOf(false)
}
