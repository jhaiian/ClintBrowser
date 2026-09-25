package com.jhaiian.clint.settings

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.jhaiian.clint.BuildConfig
import com.jhaiian.clint.R
import com.jhaiian.clint.crash.CrashHandler
import com.jhaiian.clint.crash.CrashReportItem
import com.jhaiian.clint.crash.CrashReportScreen
import com.jhaiian.clint.crash.CrashUiState
import com.jhaiian.clint.crash.MAX_CRASH_CLIP_CHARS
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.downloads.DEFAULT_SPEED_LIMIT_UNIT
import com.jhaiian.clint.downloads.DownloadScheduleMonitor
import com.jhaiian.clint.history.HistoryActivity
import com.jhaiian.clint.settings.about.AboutScreen
import com.jhaiian.clint.settings.browser.BrowserSettingsScreen
import com.jhaiian.clint.settings.browser.BrowserSettingsUiState
import com.jhaiian.clint.settings.datasaver.DataSaverScreen
import com.jhaiian.clint.settings.datasaver.DataSaverUiState
import com.jhaiian.clint.settings.desktopmode.DesktopModeActivity
import com.jhaiian.clint.settings.downloads.DownloadSettingsKeys
import com.jhaiian.clint.settings.downloads.DownloadSettingsScreen
import com.jhaiian.clint.settings.downloads.DownloadSettingsUiState
import com.jhaiian.clint.settings.lookandfeel.LookAndFeelScreen
import com.jhaiian.clint.settings.lookandfeel.LookAndFeelUiState
import com.jhaiian.clint.settings.misc.MiscScreen
import com.jhaiian.clint.settings.misc.MiscUiState
import com.jhaiian.clint.settings.privacy.PrivacySettingsScreen
import com.jhaiian.clint.settings.privacy.PrivacySettingsUiState
import com.jhaiian.clint.settings.quiverguardexception.QuiverGuardExceptionActivity
import com.jhaiian.clint.settings.site.SiteSettingsScreen
import com.jhaiian.clint.settings.site.SiteSettingsUiState
import com.jhaiian.clint.settings.sitepermissions.SitePermissionActivity
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.settings.update.UpdateSettingsScreen
import com.jhaiian.clint.settings.update.UpdateSettingsUiState
import com.jhaiian.clint.setup.SetupActivity
import com.jhaiian.clint.ui.DocumentViewer
import com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig
import com.jhaiian.clint.ui.listscreen.ConfirmDialogHost
import com.jhaiian.clint.util.DEFAULT_MEASUREMENT_SYSTEM
import com.jhaiian.clint.util.LocaleHelper
import com.jhaiian.clint.util.MEASUREMENT_SYSTEM_BINARY
import com.jhaiian.clint.util.MEASUREMENT_SYSTEM_DECIMAL
import com.jhaiian.clint.util.PREF_MEASUREMENT_SYSTEM
import com.jhaiian.clint.util.setMeasurementSystemDecimal
import kotlinx.coroutines.launch

private const val PREF_DATA_SAVER_ENABLED = "data_saver_enabled"
private const val PREF_DISABLE_IMAGES = "data_saver_disable_images"
private const val PREF_DISABLE_AUTOPLAY = "data_saver_disable_autoplay"
private const val DEFAULT_DATA_SAVER_ENABLED = false
private const val DEFAULT_DISABLE_IMAGES = true
private const val DEFAULT_DISABLE_AUTOPLAY = true

private const val PREF_BLOCK_THIRD_PARTY_COOKIES = "block_third_party_cookies"
private const val PREF_CUSTOM_USER_AGENT = "custom_user_agent"
private const val PREF_HTTPS_ONLY = "https_only"
private const val DEFAULT_BLOCK_THIRD_PARTY_COOKIES = true
private const val DEFAULT_CUSTOM_USER_AGENT = true
private const val DEFAULT_HTTPS_ONLY = true

private const val PREF_CHECK_UPDATE_ON_LAUNCH = "check_update_on_launch"
private const val PREF_SKIP_UPDATE_ON_METERED = "skip_update_on_metered"
private const val PREF_BETA_CHANNEL = "beta_channel"
private const val DEFAULT_CHECK_UPDATE_ON_LAUNCH = true
private const val DEFAULT_SKIP_UPDATE_ON_METERED = true
private const val DEFAULT_BETA_CHANNEL = false

@Composable
private fun OnResume(action: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentAction()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun LookAndFeelPane(activity: SettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    val uiState = remember {
        LookAndFeelUiState(
            initialTheme = prefs.getString("app_theme", "system") ?: "system",
            initialAccent = prefs.getString("accent_color", "material_you") ?: "material_you",
            initialIntensity = prefs.getString("surface_intensity", "soft_tint") ?: "soft_tint",
            initialLanguage = prefs.getString(LocaleHelper.PREF_APP_LANGUAGE, LocaleHelper.LANGUAGE_SYSTEM) ?: LocaleHelper.LANGUAGE_SYSTEM,
            initialScrollHideMode = prefs.getString("scroll_hide_mode", "off") ?: "off",
            initialAddressBarPosition = prefs.getString("address_bar_position", "top") ?: "top",
            initialMenuStyle = prefs.getString("menu_style", "popup") ?: "popup",
            initialTabMenuStyle = prefs.getString("tab_menu_style", "grid") ?: "grid",
            initialHideStatusBar = prefs.getBoolean("hide_status_bar", false),
            initialHideSystemNavigation = prefs.getBoolean("hide_system_navigation", false),
            initialExitConfirmation = prefs.getString("exit_confirmation", "toast") ?: "toast"
        )
    }
    var confirmDialog by remember { mutableStateOf<ConfirmDialogConfig?>(null) }

    OnResume {
        uiState.scrollHideMode = prefs.getString("scroll_hide_mode", "off") ?: "off"
        uiState.addressBarPosition = prefs.getString("address_bar_position", "top") ?: "top"
        uiState.menuStyle = prefs.getString("menu_style", "popup") ?: "popup"
        uiState.tabMenuStyle = prefs.getString("tab_menu_style", "grid") ?: "grid"
        uiState.hideStatusBar = prefs.getBoolean("hide_status_bar", false)
        uiState.hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)
        uiState.exitConfirmation = prefs.getString("exit_confirmation", "toast") ?: "toast"
        uiState.language = prefs.getString(LocaleHelper.PREF_APP_LANGUAGE, LocaleHelper.LANGUAGE_SYSTEM) ?: LocaleHelper.LANGUAGE_SYSTEM
    }

    fun applyScrollHideMode(mode: String) {
        prefs.edit()
            .putString("scroll_hide_mode", mode)
            .putString("scroll_hide_mode_${uiState.addressBarPosition}", mode)
            .apply()
        uiState.scrollHideMode = mode
    }

    fun selectScrollHideMode(slotKind: String) {
        val position = uiState.addressBarPosition
        val mode = if (slotKind == "navigation_bar" && position == "bottom") "search_bar" else slotKind
        if (mode == "off") {
            applyScrollHideMode(mode)
            uiState.openDialog = null
            return
        }
        confirmDialog = ConfirmDialogConfig(
            title = activity.getString(R.string.nested_scroll_warning_title),
            message = activity.getString(R.string.nested_scroll_warning_message),
            positiveLabel = activity.getString(R.string.action_enable_anyway),
            onPositive = {
                applyScrollHideMode(mode)
                uiState.openDialog = null
            },
            negativeLabel = activity.getString(R.string.action_cancel)
        )
    }

    fun selectAddressBarPosition(newPosition: String) {
        val current = uiState.addressBarPosition
        if (newPosition == current) {
            uiState.openDialog = null
            return
        }
        val currentMode = prefs.getString("scroll_hide_mode", "off") ?: "off"
        val savedModeForNew = prefs.getString("scroll_hide_mode_$newPosition", "off") ?: "off"
        val validModeForNew = when (newPosition) {
            "top", "bottom" -> if (savedModeForNew == "off" || savedModeForNew == "search_bar") savedModeForNew else "off"
            else -> savedModeForNew
        }
        prefs.edit()
            .putString("scroll_hide_mode_$current", currentMode)
            .putString("address_bar_position", newPosition)
            .putString("scroll_hide_mode", validModeForNew)
            .apply()
        uiState.addressBarPosition = newPosition
        uiState.scrollHideMode = validModeForNew
        uiState.openDialog = null

        confirmDialog = ConfirmDialogConfig(
            title = activity.getString(R.string.restart_required_title),
            message = activity.getString(R.string.restart_required_message),
            cancelable = false,
            positiveLabel = activity.getString(R.string.action_later),
            onPositive = { activity.scheduleRestartIfChanged() },
            negativeLabel = activity.getString(R.string.action_cancel),
            onNegative = {
                prefs.edit()
                    .putString("address_bar_position", current)
                    .putString("scroll_hide_mode", currentMode)
                    .apply()
                uiState.addressBarPosition = current
                uiState.scrollHideMode = currentMode
                activity.pendingRestart = false
            },
            neutralLabel = activity.getString(R.string.restart_required_confirm),
            onNeutral = { activity.restartApp() }
        )
    }

    fun onHideStatusBarRowClicked() {
        val newValue = !uiState.hideStatusBar
        confirmDialog = ConfirmDialogConfig(
            title = activity.getString(R.string.restart_required_title),
            message = activity.getString(R.string.restart_required_message),
            cancelable = false,
            positiveLabel = activity.getString(R.string.action_later),
            onPositive = {
                uiState.hideStatusBar = newValue
                activity.pendingHideStatusBar = newValue
                activity.scheduleRestartIfChanged()
            },
            negativeLabel = activity.getString(R.string.action_cancel),
            onNegative = { activity.pendingRestart = false },
            neutralLabel = activity.getString(R.string.restart_required_confirm),
            onNeutral = {
                uiState.hideStatusBar = newValue
                activity.pendingHideStatusBar = newValue
                activity.restartApp()
            }
        )
    }

    fun onHideSystemNavigationRowClicked() {
        val newValue = !uiState.hideSystemNavigation
        confirmDialog = ConfirmDialogConfig(
            title = activity.getString(R.string.restart_required_title),
            message = activity.getString(R.string.restart_required_message),
            cancelable = false,
            positiveLabel = activity.getString(R.string.action_later),
            onPositive = {
                uiState.hideSystemNavigation = newValue
                activity.pendingHideSystemNavigation = newValue
                activity.scheduleRestartIfChanged()
            },
            negativeLabel = activity.getString(R.string.action_cancel),
            onNegative = { activity.pendingRestart = false },
            neutralLabel = activity.getString(R.string.restart_required_confirm),
            onNeutral = {
                uiState.hideSystemNavigation = newValue
                activity.pendingHideSystemNavigation = newValue
                activity.restartApp()
            }
        )
    }

    LookAndFeelScreen(
        state = uiState,
        onThemeSelected = { newTheme -> uiState.openDialog = null; activity.captureAndRecreate(newTheme) },
        onAccentSelected = { newAccent -> uiState.openDialog = null; activity.captureAndApplyAccentColor(newAccent) },
        onIntensitySelected = { newIntensity -> uiState.openDialog = null; activity.captureAndApplySurfaceIntensity(newIntensity) },
        onLanguageSelected = { newLanguage -> uiState.openDialog = null; activity.captureAndApplyLanguage(newLanguage) },
        onAddressBarPositionSelected = ::selectAddressBarPosition,
        onMenuStyleSelected = { style ->
            prefs.edit().putString("menu_style", style).apply()
            uiState.menuStyle = style
            uiState.openDialog = null
        },
        onTabMenuStyleSelected = { style ->
            prefs.edit().putString("tab_menu_style", style).apply()
            uiState.tabMenuStyle = style
            uiState.openDialog = null
        },
        onScrollHideModeSelected = ::selectScrollHideMode,
        onHideStatusBarRowClicked = ::onHideStatusBarRowClicked,
        onHideSystemNavigationRowClicked = ::onHideSystemNavigationRowClicked,
        onCustomizeMenuRowClicked = {
            activity.startActivity(Intent(activity, com.jhaiian.clint.settings.menucustomization.MenuCustomizationActivity::class.java))
        },
        onExitConfirmationConfirmed = { value ->
            prefs.edit().putString("exit_confirmation", value).apply()
            uiState.exitConfirmation = value
            uiState.openDialog = null
        }
    )
    ConfirmDialogHost(confirmDialog, uiState.hideStatusBar, uiState.hideSystemNavigation) { confirmDialog = null }
}

@Composable
fun BrowserSettingsPane(activity: SettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    val uiState = remember {
        BrowserSettingsUiState(
            initialSearchEngine = prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo",
            initialCustomSearchEngineName = com.jhaiian.clint.browser.customSearchEngineName(prefs),
            initialCustomSearchEngineUrl = com.jhaiian.clint.browser.customSearchEngineUrlTemplate(prefs),
            initialSearchSuggestionsApi = prefs.getString("search_suggestions_api", "duckduckgo") ?: "duckduckgo",
            initialCustomSearchSuggestionsApiName = com.jhaiian.clint.browser.customSearchSuggestionsApiName(prefs),
            initialCustomSearchSuggestionsApiUrl = com.jhaiian.clint.browser.customSearchSuggestionsApiUrlTemplate(prefs),
            initialJavascriptEnabled = prefs.getBoolean("javascript_enabled", true),
            initialFramelessShortcut = prefs.getBoolean("shortcut_frameless_enabled", true),
            initialHideStatusBar = prefs.getBoolean("hide_status_bar", false),
            initialHideSystemNavigation = prefs.getBoolean("hide_system_navigation", false),
            initialIncognitoSearchHistory = prefs.getBoolean("incognito_search_history_enabled", false),
            initialCustomSelectMenus = prefs.getBoolean("custom_select_menus_enabled", true)
        )
    }
    var confirmDialog by remember { mutableStateOf<ConfirmDialogConfig?>(null) }

    OnResume {
        uiState.searchEngine = prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo"
        uiState.customSearchEngineName = com.jhaiian.clint.browser.customSearchEngineName(prefs)
        uiState.customSearchEngineUrl = com.jhaiian.clint.browser.customSearchEngineUrlTemplate(prefs)
        uiState.searchSuggestionsApi = prefs.getString("search_suggestions_api", "duckduckgo") ?: "duckduckgo"
        uiState.customSearchSuggestionsApiName = com.jhaiian.clint.browser.customSearchSuggestionsApiName(prefs)
        uiState.customSearchSuggestionsApiUrl = com.jhaiian.clint.browser.customSearchSuggestionsApiUrlTemplate(prefs)
        uiState.javascriptEnabled = prefs.getBoolean("javascript_enabled", true)
        uiState.framelessShortcut = prefs.getBoolean("shortcut_frameless_enabled", true)
        uiState.hideStatusBar = prefs.getBoolean("hide_status_bar", false)
        uiState.hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)
        uiState.incognitoSearchHistory = prefs.getBoolean("incognito_search_history_enabled", false)
        uiState.customSelectMenus = prefs.getBoolean("custom_select_menus_enabled", true)
    }

    fun confirmEngine(engine: String) {
        prefs.edit().putString("search_engine", engine).apply()
        uiState.searchEngine = engine
    }

    fun onSearchEngineConfirmed(selected: String) {
        val current = uiState.searchEngine
        uiState.searchEngineDialogOpen = false
        if (selected == "google" && current != "google") {
            confirmDialog = ConfirmDialogConfig(
                title = activity.getString(R.string.google_warning_title),
                message = activity.getString(R.string.google_warning_message),
                positiveLabel = activity.getString(R.string.use_google_anyway),
                onPositive = { confirmEngine("google") },
                negativeLabel = activity.getString(R.string.choose_another)
            )
        } else {
            confirmEngine(selected)
        }
    }

    fun onCustomSearchEngineSaved(name: String, url: String) {
        prefs.edit()
            .putString(com.jhaiian.clint.browser.CustomSearchEngineNameKey, name)
            .putString(com.jhaiian.clint.browser.CustomSearchEngineUrlKey, url)
            .apply()
        uiState.customSearchEngineName = name
        uiState.customSearchEngineUrl = url
    }

    fun onCustomSearchSuggestionsApiSaved(name: String, url: String) {
        prefs.edit()
            .putString(com.jhaiian.clint.browser.CustomSearchSuggestionsApiNameKey, name)
            .putString(com.jhaiian.clint.browser.CustomSearchSuggestionsApiUrlKey, url)
            .apply()
        uiState.customSearchSuggestionsApiName = name
        uiState.customSearchSuggestionsApiUrl = url
    }

    fun confirmSuggestionsApi(api: String) {
        prefs.edit().putString("search_suggestions_api", api).apply()
        uiState.searchSuggestionsApi = api
    }

    fun onSearchSuggestionsApiConfirmed(selected: String) {
        val current = uiState.searchSuggestionsApi
        uiState.searchSuggestionsApiDialogOpen = false
        if (selected == "google" && current != "google") {
            confirmDialog = ConfirmDialogConfig(
                title = activity.getString(R.string.google_warning_title),
                message = activity.getString(R.string.suggestions_google_warning_message),
                positiveLabel = activity.getString(R.string.use_google_anyway),
                onPositive = { confirmSuggestionsApi("google") },
                negativeLabel = activity.getString(R.string.choose_another)
            )
        } else {
            confirmSuggestionsApi(selected)
        }
    }

    fun onJavascriptRowClicked() {
        val current = prefs.getBoolean("javascript_enabled", true)
        if (current) {
            confirmDialog = ConfirmDialogConfig(
                title = activity.getString(R.string.js_warning_title),
                message = activity.getString(R.string.js_warning_message),
                positiveLabel = activity.getString(R.string.action_turn_off_anyway),
                onPositive = {
                    prefs.edit().putBoolean("javascript_enabled", false).apply()
                    uiState.javascriptEnabled = false
                },
                negativeLabel = activity.getString(R.string.action_cancel)
            )
        } else {
            prefs.edit().putBoolean("javascript_enabled", true).apply()
            uiState.javascriptEnabled = true
        }
    }

    fun onFramelessShortcutRowClicked() {
        val newValue = !uiState.framelessShortcut
        prefs.edit().putBoolean("shortcut_frameless_enabled", newValue).apply()
        uiState.framelessShortcut = newValue
    }

    fun onIncognitoSearchHistoryRowClicked() {
        val newValue = !uiState.incognitoSearchHistory
        prefs.edit().putBoolean("incognito_search_history_enabled", newValue).apply()
        uiState.incognitoSearchHistory = newValue
    }

    fun onCustomSelectMenusRowClicked() {
        val newValue = !uiState.customSelectMenus
        prefs.edit().putBoolean("custom_select_menus_enabled", newValue).apply()
        uiState.customSelectMenus = newValue
    }

    BrowserSettingsScreen(
        state = uiState,
        onSearchEngineConfirmed = ::onSearchEngineConfirmed,
        onCustomSearchEngineSaved = ::onCustomSearchEngineSaved,
        onSearchSuggestionsApiConfirmed = ::onSearchSuggestionsApiConfirmed,
        onCustomSearchSuggestionsApiSaved = ::onCustomSearchSuggestionsApiSaved,
        onJavascriptRowClicked = ::onJavascriptRowClicked,
        onFramelessShortcutRowClicked = ::onFramelessShortcutRowClicked,
        onWebsiteBlockerRowClicked = {
            activity.startActivity(android.content.Intent(activity, com.jhaiian.clint.blocker.WebsiteBlockerActivity::class.java))
        },
        onQuiverGuardRowClicked = {
            activity.startActivity(android.content.Intent(activity, com.jhaiian.clint.quiver.QuiverGuardActivity::class.java))
        },
        onIncognitoSearchHistoryRowClicked = ::onIncognitoSearchHistoryRowClicked,
        onUserScriptsRowClicked = {
            activity.startActivity(android.content.Intent(activity, com.jhaiian.clint.userscripts.UserScriptsActivity::class.java))
        },
        onCustomSelectMenusRowClicked = ::onCustomSelectMenusRowClicked
    )
    ConfirmDialogHost(confirmDialog, uiState.hideStatusBar, uiState.hideSystemNavigation) { confirmDialog = null }
}

@Composable
fun PrivacySettingsPane(activity: SettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    val uiState = remember {
        PrivacySettingsUiState(
            initialBlockThirdPartyCookies = prefs.getBoolean(PREF_BLOCK_THIRD_PARTY_COOKIES, DEFAULT_BLOCK_THIRD_PARTY_COOKIES),
            initialCustomUserAgent = prefs.getBoolean(PREF_CUSTOM_USER_AGENT, DEFAULT_CUSTOM_USER_AGENT),
            initialHttpsOnly = prefs.getBoolean(PREF_HTTPS_ONLY, DEFAULT_HTTPS_ONLY)
        )
    }

    fun toggle(prefKey: String, current: Boolean, apply: (Boolean) -> Unit) {
        val newValue = !current
        prefs.edit().putBoolean(prefKey, newValue).apply()
        apply(newValue)
    }

    PrivacySettingsScreen(
        state = uiState,
        onBlockThirdPartyCookiesClick = { toggle(PREF_BLOCK_THIRD_PARTY_COOKIES, uiState.blockThirdPartyCookies) { uiState.blockThirdPartyCookies = it } },
        onCustomUserAgentClick = { toggle(PREF_CUSTOM_USER_AGENT, uiState.customUserAgent) { uiState.customUserAgent = it } },
        onHttpsOnlyClick = { toggle(PREF_HTTPS_ONLY, uiState.httpsOnly) { uiState.httpsOnly = it } },
        onHistoryClick = { activity.startActivity(Intent(activity, HistoryActivity::class.java)) }
    )
}

@Composable
fun SiteSettingsPane(activity: SettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }

    fun behaviorFor(type: String): String =
        prefs.getString("site_perm_default_$type", SitePermissionActivity.PREF_VALUE_ASK) ?: SitePermissionActivity.PREF_VALUE_ASK

    fun desktopModeSaveState(): String =
        prefs.getString(DesktopModeActivity.PREF_DESKTOP_MODE_SAVE_STATE, DesktopModeActivity.VALUE_SAVE_STATE) ?: DesktopModeActivity.VALUE_SAVE_STATE

    val uiState = remember {
        SiteSettingsUiState(
            initialCameraBehavior = behaviorFor(SitePermissionDatabase.TYPE_CAMERA),
            initialMicBehavior = behaviorFor(SitePermissionDatabase.TYPE_MIC),
            initialLocationBehavior = behaviorFor(SitePermissionDatabase.TYPE_LOCATION),
            initialNotificationsBehavior = behaviorFor(SitePermissionDatabase.TYPE_NOTIFICATION),
            initialClipboardBehavior = behaviorFor(SitePermissionDatabase.TYPE_CLIPBOARD),
            initialDesktopModeSaveState = desktopModeSaveState()
        )
    }

    fun openPermission(type: String) {
        activity.startActivity(Intent(activity, SitePermissionActivity::class.java).putExtra(SitePermissionActivity.EXTRA_TYPE, type))
    }

    OnResume {
        uiState.cameraBehavior = behaviorFor(SitePermissionDatabase.TYPE_CAMERA)
        uiState.micBehavior = behaviorFor(SitePermissionDatabase.TYPE_MIC)
        uiState.locationBehavior = behaviorFor(SitePermissionDatabase.TYPE_LOCATION)
        uiState.notificationsBehavior = behaviorFor(SitePermissionDatabase.TYPE_NOTIFICATION)
        uiState.clipboardBehavior = behaviorFor(SitePermissionDatabase.TYPE_CLIPBOARD)
        uiState.desktopModeSaveState = desktopModeSaveState()
    }

    SiteSettingsScreen(
        state = uiState,
        onCameraClick = { openPermission(SitePermissionDatabase.TYPE_CAMERA) },
        onMicClick = { openPermission(SitePermissionDatabase.TYPE_MIC) },
        onLocationClick = { openPermission(SitePermissionDatabase.TYPE_LOCATION) },
        onNotificationsClick = { openPermission(SitePermissionDatabase.TYPE_NOTIFICATION) },
        onClipboardClick = { openPermission(SitePermissionDatabase.TYPE_CLIPBOARD) },
        onDesktopModeClick = { activity.startActivity(Intent(activity, DesktopModeActivity::class.java)) },
        onQuiverGuardClick = { activity.startActivity(Intent(activity, QuiverGuardExceptionActivity::class.java)) }
    )
}

@Composable
fun DataSaverPane(activity: SettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    val uiState = remember {
        DataSaverUiState(
            initialEnabled = prefs.getBoolean(PREF_DATA_SAVER_ENABLED, DEFAULT_DATA_SAVER_ENABLED),
            initialDisableImages = prefs.getBoolean(PREF_DISABLE_IMAGES, DEFAULT_DISABLE_IMAGES),
            initialDisableAutoplay = prefs.getBoolean(PREF_DISABLE_AUTOPLAY, DEFAULT_DISABLE_AUTOPLAY)
        )
    }
    fun toggle(prefKey: String, current: Boolean, apply: (Boolean) -> Unit) {
        val newValue = !current
        prefs.edit().putBoolean(prefKey, newValue).apply()
        apply(newValue)
    }

    DataSaverScreen(
        state = uiState,
        onEnabledClick = { toggle(PREF_DATA_SAVER_ENABLED, uiState.enabled) { uiState.enabled = it } },
        onDisableImagesClick = {
            if (uiState.enabled) toggle(PREF_DISABLE_IMAGES, uiState.disableImages) { uiState.disableImages = it }
        },
        onDisableAutoplayClick = {
            if (uiState.enabled) toggle(PREF_DISABLE_AUTOPLAY, uiState.disableAutoplay) { uiState.disableAutoplay = it }
        }
    )
}

@Composable
fun UpdateSettingsPane(activity: SettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    val uiState = remember {
        UpdateSettingsUiState(
            initialCheckOnLaunch = prefs.getBoolean(PREF_CHECK_UPDATE_ON_LAUNCH, DEFAULT_CHECK_UPDATE_ON_LAUNCH),
            initialSkipOnMetered = prefs.getBoolean(PREF_SKIP_UPDATE_ON_METERED, DEFAULT_SKIP_UPDATE_ON_METERED),
            initialBetaChannel = prefs.getBoolean(PREF_BETA_CHANNEL, DEFAULT_BETA_CHANNEL),
            hideStatusBar = prefs.getBoolean("hide_status_bar", false),
            hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)
        )
    }

    fun toggle(prefKey: String, current: Boolean, apply: (Boolean) -> Unit) {
        val newValue = !current
        prefs.edit().putBoolean(prefKey, newValue).apply()
        apply(newValue)
    }

    UpdateSettingsScreen(
        state = uiState,
        onCheckOnLaunchClick = { toggle(PREF_CHECK_UPDATE_ON_LAUNCH, uiState.checkOnLaunch) { uiState.checkOnLaunch = it } },
        onSkipOnMeteredClick = {
            if (uiState.checkOnLaunch) toggle(PREF_SKIP_UPDATE_ON_METERED, uiState.skipOnMetered) { uiState.skipOnMetered = it }
        },
        onCheckForUpdatesClick = { com.jhaiian.clint.update.UpdateChecker.check(activity, uiState.betaChannel, silent = false) },
        onViewChangelogClick = {
            DocumentViewer.show(activity, activity.getString(R.string.document_viewer_changelog_title), DocumentViewer.CHANGELOG_URL)
        },
        onBetaChannelClick = {
            if (uiState.betaChannel) {
                toggle(PREF_BETA_CHANNEL, true) { uiState.betaChannel = it }
            } else {
                uiState.betaConfirmDialogOpen = true
            }
        },
        onBetaConfirm = {
            prefs.edit().putBoolean(PREF_BETA_CHANNEL, true).apply()
            uiState.betaChannel = true
            uiState.betaConfirmDialogOpen = false
        }
    )
}

private fun defaultBrowserSummaryText(context: Context): String {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
    val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.loadLabel(context.packageManager)?.toString() ?: context.getString(R.string.default_browser_none)
}

@Composable
fun MiscPane(activity: SettingsActivity) {
    val uiState = remember {
        MiscUiState(
            initialDefaultBrowserSummary = defaultBrowserSummaryText(activity),
            hideStatusBar = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("hide_status_bar", false),
            hideSystemNavigation = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("hide_system_navigation", false)
        )
    }

    val browserRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        uiState.defaultBrowserSummary = defaultBrowserSummaryText(activity)
    }

    OnResume {
        uiState.defaultBrowserSummary = defaultBrowserSummaryText(activity)
    }

    fun openDefaultBrowserPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = activity.getSystemService(RoleManager::class.java)
                if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    browserRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                    return
                }
            } catch (_: Exception) {}
        }
        activity.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    fun rerunSetup() {
        PreferenceManager.getDefaultSharedPreferences(activity).edit().remove("setup_complete").apply()
        val intent = Intent(activity, SetupActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }

    MiscScreen(
        state = uiState,
        onDefaultBrowserClick = { openDefaultBrowserPicker() },
        onRerunSetupClick = { uiState.rerunSetupConfirmDialogOpen = true },
        onRerunSetupConfirm = {
            uiState.rerunSetupConfirmDialogOpen = false
            rerunSetup()
        }
    )
}

private const val CRASH_FILENAME_DATE_FORMAT = "yyyyMMdd_HHmmss"
private const val CRASH_DISPLAY_DATE_FORMAT = "MMM d, yyyy  HH:mm:ss"

private fun readCrashReportsFromDisk(context: Context): List<CrashReportItem> {
    val appContext = context.applicationContext
    val fileDateFmt = java.text.SimpleDateFormat(CRASH_FILENAME_DATE_FORMAT, java.util.Locale.US)
    val displayFmt = java.text.SimpleDateFormat(CRASH_DISPLAY_DATE_FORMAT, java.util.Locale.US)
    CrashHandler.deleteOldReports(appContext)
    return CrashHandler.getCrashFiles(appContext).map { file ->
        val nameWithoutExt = file.nameWithoutExtension.removePrefix("crash_")
        val date = runCatching { fileDateFmt.parse(nameWithoutExt) }.getOrNull()
        val title = date?.let { displayFmt.format(it) } ?: file.name
        CrashReportItem(file, title, file.readText())
    }
}

private fun buildCrashReportTemplate(context: Context): String {
    val deviceInfo = CrashHandler.buildDeviceInfo(context)
    return buildString {
        appendLine("**Device Information**")
        deviceInfo.lines().filter { it.isNotBlank() }.forEach { appendLine("- $it") }
        appendLine()
        appendLine("**Steps to Reproduce**")
        appendLine("1. ")
        appendLine("2. ")
        appendLine("3. ")
        appendLine()
        appendLine("**Expected Behavior**")
        appendLine("")
        appendLine()
        appendLine("**Actual Behavior**")
        appendLine("")
        appendLine()
        appendLine("**Crash Report** _(paste from Debug screen above)_")
        appendLine("```")
        appendLine("(paste here)")
        appendLine("```")
    }
}

@Composable
fun DebugPane(activity: SettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    val uiState = remember { CrashUiState(hideStatusBar = prefs.getBoolean("hide_status_bar", false), hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)) }
    val scope = rememberCoroutineScope()

    fun copyToClipboard(content: String) {
        val clipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val truncated = content.length > MAX_CRASH_CLIP_CHARS
        val clipped = if (truncated) content.take(MAX_CRASH_CLIP_CHARS) + "\n${activity.getString(R.string.crash_log_truncated)}" else content
        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Clint Crash Report", clipped))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val msg = if (truncated) activity.getString(R.string.crash_copied_truncated) else activity.getString(R.string.crash_copied)
            android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        uiState.reportTemplate = buildCrashReportTemplate(activity)
        uiState.isLoading = true
        val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readCrashReportsFromDisk(activity) }
        uiState.reports.clear()
        uiState.reports.addAll(items)
        uiState.isLoading = false
    }

    CrashReportScreen(
        state = uiState,
        onOpenReport = { uiState.detailReport = it },
        onCopyReport = { copyToClipboard(it.content) },
        onDeleteReport = { item ->
            uiState.detailReport = null
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { item.file.delete() }
                uiState.reports.remove(item)
            }
        },
        onClearAllClick = { uiState.clearAllConfirmOpen = true },
        onClearAllConfirm = {
            uiState.clearAllConfirmOpen = false
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CrashHandler.clearAllReports(activity.applicationContext) }
                uiState.reports.clear()
            }
        },
        onCopyTemplate = {
            val clipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Bug Report Template", uiState.reportTemplate))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                android.widget.Toast.makeText(activity, activity.getString(R.string.crash_template_copied), android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        onOpenGithub = {
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jhaiian/ClintBrowser/issues/new"))) }
        }
    )
}

@Composable
fun AboutPane(activity: SettingsActivity) {
    val versionInfo = remember { aboutVersionInfoText(activity) }
    val webViewInfo = remember { aboutWebViewInfoText(activity) }
    AboutScreen(
        versionInfo = versionInfo,
        webViewInfo = webViewInfo,
        onLinkClick = { url -> aboutOpenLink(activity, url) },
        onPrivacyPolicyClick = {
            DocumentViewer.show(activity, activity.getString(R.string.document_viewer_privacy_policy_title), DocumentViewer.PRIVACY_POLICY_URL)
        },
        onTermsClick = {
            DocumentViewer.show(activity, activity.getString(R.string.document_viewer_terms_title), DocumentViewer.TERMS_URL)
        },
        onAttributionClick = {
            DocumentViewer.show(activity, activity.getString(R.string.document_viewer_attribution_title), DocumentViewer.ATTRIBUTION_URL)
        }
    )
}

private fun aboutVersionInfoText(context: Context): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    return context.getString(R.string.about_version_info, packageInfo.versionName, versionCode, arch)
}

private fun aboutWebViewInfoText(context: Context): String {
    val webViewPackage = WebView.getCurrentWebViewPackage() ?: return context.getString(R.string.about_webview_unavailable)
    val appName = webViewPackage.applicationInfo?.let { context.packageManager.getApplicationLabel(it).toString() } ?: webViewPackage.packageName
    val version = webViewPackage.versionName ?: context.getString(R.string.about_webview_unavailable)
    return context.getString(R.string.about_webview_info, appName, webViewPackage.packageName, version)
}

private fun aboutOpenLink(context: Context, url: String) {
    val intent = if (url.startsWith("mailto:")) {
        Intent(Intent.ACTION_SENDTO, Uri.parse(url)).apply { putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name)) }
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
    runCatching { context.startActivity(intent) }
}

private fun showGrantAllFilesAccessRow(): Boolean =
    !BuildConfig.IS_FDROID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

private fun isAllFilesAccessGranted(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java)
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
fun DownloadSettingsPane(activity: SettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    val uiState = remember {
        DownloadSettingsUiState(
            initialDownloadManagerApp = prefs.getString(DownloadSettingsKeys.PREF_DOWNLOAD_MANAGER, DownloadSettingsKeys.DEFAULT_DOWNLOAD_MANAGER) ?: DownloadSettingsKeys.DEFAULT_DOWNLOAD_MANAGER,
            initialLocationMode = prefs.getString(DownloadSettingsKeys.PREF_DOWNLOAD_LOCATION_MODE, DownloadSettingsKeys.MODE_DEFAULT) ?: DownloadSettingsKeys.MODE_DEFAULT,
            initialCustomUri = prefs.getString(DownloadSettingsKeys.PREF_DOWNLOAD_CUSTOM_URI, null)?.let { Uri.parse(it) },
            initialCategorizeDownloads = prefs.getBoolean(DownloadSettingsKeys.PREF_CATEGORIZE_DOWNLOADS, DownloadSettingsKeys.DEFAULT_CATEGORIZE_DOWNLOADS),
            initialMeasurementSystemDecimal = prefs.getString(PREF_MEASUREMENT_SYSTEM, DEFAULT_MEASUREMENT_SYSTEM) == MEASUREMENT_SYSTEM_DECIMAL,
            initialUnmeteredOnly = prefs.getBoolean(DownloadSettingsKeys.PREF_UNMETERED_ONLY, DownloadSettingsKeys.DEFAULT_UNMETERED_ONLY),
            initialScheduleEnabled = prefs.getBoolean(DownloadSettingsKeys.PREF_SCHEDULE_ENABLED, DownloadSettingsKeys.DEFAULT_SCHEDULE_ENABLED),
            initialScheduleStartMinutes = prefs.getInt(DownloadSettingsKeys.PREF_SCHEDULE_START_MINUTES, DownloadSettingsKeys.DEFAULT_SCHEDULE_START_MINUTES),
            initialScheduleEndMinutes = prefs.getInt(DownloadSettingsKeys.PREF_SCHEDULE_END_MINUTES, DownloadSettingsKeys.DEFAULT_SCHEDULE_END_MINUTES),
            initialConcurrentDownloads = prefs.getInt(DownloadSettingsKeys.PREF_CONCURRENT_DOWNLOADS, DownloadSettingsKeys.DEFAULT_CONCURRENT_DOWNLOADS),
            initialSplitParts = prefs.getInt(DownloadSettingsKeys.PREF_SPLIT_PARTS, DownloadSettingsKeys.DEFAULT_SPLIT_PARTS),
            initialMultithreadingParts = prefs.getInt(DownloadSettingsKeys.PREF_MULTITHREADING_PARTS, DownloadSettingsKeys.DEFAULT_MULTITHREADING_PARTS),
            initialConcurrentSegments = prefs.getInt(DownloadSettingsKeys.PREF_STREAM_CONCURRENT_SEGMENTS, DownloadSettingsKeys.DEFAULT_STREAM_CONCURRENT_SEGMENTS),
            initialSpeedLimitAmount = prefs.getInt(DownloadSettingsKeys.PREF_SPEED_LIMIT_AMOUNT, DownloadSettingsKeys.DEFAULT_SPEED_LIMIT_AMOUNT),
            initialSpeedLimitUnit = prefs.getString(DownloadSettingsKeys.PREF_SPEED_LIMIT_UNIT, DEFAULT_SPEED_LIMIT_UNIT) ?: DEFAULT_SPEED_LIMIT_UNIT,
            initialRetryEnabled = prefs.getBoolean(DownloadSettingsKeys.PREF_RETRY_ENABLED, DownloadSettingsKeys.DEFAULT_RETRY_ENABLED),
            initialRetryUnrecoverable = prefs.getBoolean(DownloadSettingsKeys.PREF_RETRY_UNRECOVERABLE, DownloadSettingsKeys.DEFAULT_RETRY_UNRECOVERABLE),
            initialRetryCount = prefs.getInt(DownloadSettingsKeys.PREF_RETRY_COUNT, DownloadSettingsKeys.DEFAULT_RETRY_COUNT),
            initialRetryInterval = prefs.getInt(DownloadSettingsKeys.PREF_RETRY_INTERVAL, DownloadSettingsKeys.DEFAULT_RETRY_INTERVAL),
            initialIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(activity),
            initialShowGrantAllFilesAccessRow = showGrantAllFilesAccessRow(),
            initialAllFilesAccessGranted = isAllFilesAccessGranted(),
            initialPushNotifications = prefs.getBoolean(DownloadSettingsKeys.PREF_PUSH_NOTIFICATIONS, DownloadSettingsKeys.DEFAULT_PUSH_NOTIFICATIONS),
            initialKeepScreenOn = prefs.getBoolean(DownloadSettingsKeys.PREF_KEEP_SCREEN_ON, DownloadSettingsKeys.DEFAULT_KEEP_SCREEN_ON),
            initialHideStatusBar = prefs.getBoolean("hide_status_bar", false),
            initialHideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)
        )
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            prefs.edit().putString(DownloadSettingsKeys.PREF_DOWNLOAD_CUSTOM_URI, uri.toString()).apply()
            uiState.customUri = uri
        }
    }

    fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        folderPickerLauncher.launch(intent)
    }

    OnResume {

        uiState.ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(activity)
        if (uiState.showGrantAllFilesAccessRow) {
            uiState.allFilesAccessGranted = isAllFilesAccessGranted()
        }
        uiState.hideStatusBar = prefs.getBoolean("hide_status_bar", false)
        uiState.hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)
    }

    fun showSchedulePicker(currentMinutes: Int, onPicked: (Int) -> Unit) {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(activity)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (is24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(currentMinutes / 60)
            .setMinute(currentMinutes % 60)
            .setTitleText(R.string.download_schedule_picker_title)
            .build()
        picker.addOnPositiveButtonClickListener {
            onPicked(picker.hour * 60 + picker.minute)
        }
        picker.show(activity.supportFragmentManager, "schedule_time_picker")
    }

    DownloadSettingsScreen(
        state = uiState,
        onDownloadManagerSelected = { appId ->
            prefs.edit().putString(DownloadSettingsKeys.PREF_DOWNLOAD_MANAGER, appId).apply()
            uiState.downloadManagerApp = appId
            uiState.openDialog = null
        },
        onLocationModeSelected = { newMode ->
            prefs.edit().putString(DownloadSettingsKeys.PREF_DOWNLOAD_LOCATION_MODE, newMode).apply()
            uiState.locationMode = newMode
            if (newMode == DownloadSettingsKeys.MODE_CUSTOM) openFolderPicker()
        },
        onFolderRowClick = { openFolderPicker() },
        onCategorizeDownloadsClick = {
            val newValue = !uiState.categorizeDownloads
            prefs.edit().putBoolean(DownloadSettingsKeys.PREF_CATEGORIZE_DOWNLOADS, newValue).apply()
            uiState.categorizeDownloads = newValue
        },
        onMeasurementSystemSelected = { decimal ->
            val value = if (decimal) MEASUREMENT_SYSTEM_DECIMAL else MEASUREMENT_SYSTEM_BINARY
            prefs.edit().putString(PREF_MEASUREMENT_SYSTEM, value).apply()
            setMeasurementSystemDecimal(decimal)
            uiState.measurementSystemDecimal = decimal
            uiState.openDialog = null
        },
        onUnmeteredOnlyClick = {
            val newValue = !uiState.unmeteredOnly
            prefs.edit().putBoolean(DownloadSettingsKeys.PREF_UNMETERED_ONLY, newValue).apply()
            uiState.unmeteredOnly = newValue
            ClintDownloadManager.onUnmeteredOnlyChanged(activity, newValue)
        },
        onScheduleEnabledClick = {
            val newValue = !uiState.scheduleEnabled
            prefs.edit().putBoolean(DownloadSettingsKeys.PREF_SCHEDULE_ENABLED, newValue).apply()
            uiState.scheduleEnabled = newValue
            DownloadScheduleMonitor.onScheduleChanged(activity)
        },
        onScheduleStartClick = {
            showSchedulePicker(uiState.scheduleStartMinutes) { minutes ->
                prefs.edit().putInt(DownloadSettingsKeys.PREF_SCHEDULE_START_MINUTES, minutes).apply()
                uiState.scheduleStartMinutes = minutes
                DownloadScheduleMonitor.onScheduleChanged(activity)
            }
        },
        onScheduleEndClick = {
            showSchedulePicker(uiState.scheduleEndMinutes) { minutes ->
                prefs.edit().putInt(DownloadSettingsKeys.PREF_SCHEDULE_END_MINUTES, minutes).apply()
                uiState.scheduleEndMinutes = minutes
                DownloadScheduleMonitor.onScheduleChanged(activity)
            }
        },
        onConcurrentDownloadsChange = { value ->
            prefs.edit().putInt(DownloadSettingsKeys.PREF_CONCURRENT_DOWNLOADS, value).apply()
            uiState.concurrentDownloads = value
        },
        onSplitPartsChange = { value ->
            prefs.edit().putInt(DownloadSettingsKeys.PREF_SPLIT_PARTS, value).apply()
            uiState.splitParts = value
        },
        onMultithreadingPartsChange = { value ->
            prefs.edit().putInt(DownloadSettingsKeys.PREF_MULTITHREADING_PARTS, value).apply()
            uiState.multithreadingParts = value
        },
        onConcurrentSegmentsChange = { value ->
            prefs.edit().putInt(DownloadSettingsKeys.PREF_STREAM_CONCURRENT_SEGMENTS, value).apply()
            uiState.concurrentSegments = value
        },
        onSpeedLimitConfirm = { amount, unit ->
            prefs.edit().putInt(DownloadSettingsKeys.PREF_SPEED_LIMIT_AMOUNT, amount).putString(DownloadSettingsKeys.PREF_SPEED_LIMIT_UNIT, unit).apply()
            uiState.speedLimitAmount = amount
            uiState.speedLimitUnit = unit
            uiState.openDialog = null
        },
        onRetryEnabledClick = {
            val newValue = !uiState.retryEnabled
            prefs.edit().putBoolean(DownloadSettingsKeys.PREF_RETRY_ENABLED, newValue).apply()
            uiState.retryEnabled = newValue
        },
        onRetryUnrecoverableClick = {
            val newValue = !uiState.retryUnrecoverable
            prefs.edit().putBoolean(DownloadSettingsKeys.PREF_RETRY_UNRECOVERABLE, newValue).apply()
            uiState.retryUnrecoverable = newValue
        },
        onRetryCountConfirm = { value ->
            prefs.edit().putInt(DownloadSettingsKeys.PREF_RETRY_COUNT, value).apply()
            uiState.retryCount = value
            uiState.openDialog = null
        },
        onRetryIntervalConfirm = { value ->
            prefs.edit().putInt(DownloadSettingsKeys.PREF_RETRY_INTERVAL, value).apply()
            uiState.retryInterval = value
            uiState.openDialog = null
        },
        onIgnoreBatteryOptClick = {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        },
        onGrantAllFilesAccessClick = {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        },
        onPushNotificationsClick = {
            val newValue = !uiState.pushNotifications
            prefs.edit().putBoolean(DownloadSettingsKeys.PREF_PUSH_NOTIFICATIONS, newValue).apply()
            uiState.pushNotifications = newValue
        },
        onKeepScreenOnClick = {
            val newValue = !uiState.keepScreenOn
            prefs.edit().putBoolean(DownloadSettingsKeys.PREF_KEEP_SCREEN_ON, newValue).apply()
            uiState.keepScreenOn = newValue
        }
    )
}
