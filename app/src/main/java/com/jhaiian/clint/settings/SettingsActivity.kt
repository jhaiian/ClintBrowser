package com.jhaiian.clint.settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.jhaiian.clint.BuildConfig
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.settings.backuprestore.BackupRestorePane
import com.jhaiian.clint.settings.main.MainSettingsScreen
import com.jhaiian.clint.settings.supportclint.SupportClintActivity
import com.jhaiian.clint.ui.DocumentViewer
import com.jhaiian.clint.ui.OverlayHostActivity
import com.jhaiian.clint.ui.theme.ClintComposeTheme
import com.jhaiian.clint.ui.theme.LocalClintColors

private const val DEST_LOOK_AND_FEEL = "look_and_feel"
private const val DEST_BROWSER = "browser"
private const val DEST_PRIVACY = "privacy"
private const val DEST_SITE_SETTINGS = "site_settings"
private const val DEST_DATA_SAVER = "data_saver"
private const val DEST_DOWNLOADS = "downloads"
private const val DEST_BACKUP_RESTORE = "backup_restore"
private const val DEST_UPDATES = "updates"
private const val DEST_MISC = "misc"
private const val DEST_DEBUG = "debug"
private const val DEST_ABOUT = "about"

class SettingsActivity : ClintActivity(), OverlayHostActivity {

    override var overlayContent by mutableStateOf<(@Composable () -> Unit)?>(null)

    var pendingRestart = false

    var pendingHideStatusBar: Boolean? = null
    var pendingHideSystemNavigation: Boolean? = null

    private var hideStatusBarAtLaunch = false
    private var hideSystemNavigationAtLaunch = false
    private var addressBarPositionAtLaunch = "top"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        hideStatusBarAtLaunch = prefs.getBoolean("hide_status_bar", false)
        hideSystemNavigationAtLaunch = prefs.getBoolean("hide_system_navigation", false)
        addressBarPositionAtLaunch = prefs.getString("address_bar_position", "top") ?: "top"
        val theme = prefs.getString("app_theme", "system") ?: "system"

        val initialDestination = when (intent.getStringExtra(EXTRA_OPEN_FRAGMENT)) {
            "data_saver" -> DEST_DATA_SAVER
            "download_settings" -> DEST_DOWNLOADS
            else -> null
        }

        setContent {
            ClintComposeTheme(theme = theme) {
                SettingsNavHost(activity = this, initialDestination = initialDestination)
                overlayContent?.invoke()
            }
        }
    }

    fun scheduleRestartIfChanged() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val effectiveHideStatusBar = pendingHideStatusBar ?: prefs.getBoolean("hide_status_bar", false)
        val effectiveHideSystemNavigation = pendingHideSystemNavigation ?: prefs.getBoolean("hide_system_navigation", false)
        val statusBarChanged = effectiveHideStatusBar != hideStatusBarAtLaunch
        val navigationChanged = effectiveHideSystemNavigation != hideSystemNavigationAtLaunch
        val positionChanged = (prefs.getString("address_bar_position", "top") ?: "top") != addressBarPositionAtLaunch
        pendingRestart = statusBarChanged || navigationChanged || positionChanged
    }

    fun restartApp() {
        pendingRestart = false
        val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
        var shouldCommit = false
        pendingHideStatusBar?.let { pending ->
            editor.putBoolean("hide_status_bar", pending)
            pendingHideStatusBar = null
            shouldCommit = true
        }
        pendingHideSystemNavigation?.let { pending ->
            editor.putBoolean("hide_system_navigation", pending)
            pendingHideSystemNavigation = null
            shouldCommit = true
        }
        if (shouldCommit) editor.apply()
        val restartIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(restartIntent)
    }

    override fun onStop() {
        super.onStop()
        if (pendingRestart) restartApp()
    }

    fun restartAppAfterRestore() {
        val restartIntent = packageManager.getLaunchIntentForPackage(packageName)
        restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (restartIntent != null) startActivity(restartIntent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    companion object {
        const val EXTRA_OPEN_FRAGMENT = "extra_open_fragment"
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun SettingsNavHost(activity: SettingsActivity, initialDestination: String?) {
    var selectedDestination by rememberSaveable { mutableStateOf(initialDestination) }
    var hasShownList by rememberSaveable { mutableStateOf(initialDestination == null) }
    val isTwoPane = calculateWindowSizeClass(activity).widthSizeClass != WindowWidthSizeClass.Compact

    if (isTwoPane) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(360.dp).fillMaxHeight()) {
                SettingsListPane(activity = activity) { destination -> selectedDestination = destination }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                SettingsDetailPane(
                    activity = activity,
                    destination = selectedDestination,
                    onBack = { selectedDestination = null }
                )
            }
        }
    } else {
        val handleBack: () -> Unit = {
            if (hasShownList) selectedDestination = null else activity.finish()
        }
        if (selectedDestination != null) {
            BackHandler(onBack = handleBack)
        }
        AnimatedContent(
            targetState = selectedDestination,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settings_pane"
        ) { destination ->
            if (destination == null) {
                SettingsListPane(activity = activity) { newDestination ->
                    hasShownList = true
                    selectedDestination = newDestination
                }
            } else {
                SettingsDetailPane(activity = activity, destination = destination, onBack = handleBack)
            }
        }
    }
}

@Composable
private fun SettingsListPane(activity: SettingsActivity, onNavigate: (String) -> Unit) {
    val versionName = remember {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
    }
    Column(Modifier.fillMaxSize()) {
        SettingsToolbar(title = stringResource(R.string.settings), onBack = { activity.finish() })
        Box(Modifier.weight(1f)) {
            MainSettingsScreen(
                versionName = versionName,
                onLookAndFeelClick = { onNavigate(DEST_LOOK_AND_FEEL) },
                onBrowserClick = { onNavigate(DEST_BROWSER) },
                onPrivacyClick = { onNavigate(DEST_PRIVACY) },
                onSiteSettingsClick = { onNavigate(DEST_SITE_SETTINGS) },
                onDataSaverClick = { onNavigate(DEST_DATA_SAVER) },
                onDownloadsClick = { onNavigate(DEST_DOWNLOADS) },
                onBackupRestoreClick = { onNavigate(DEST_BACKUP_RESTORE) },
                onUpdatesClick = {
                    if (BuildConfig.IS_FDROID) {
                        DocumentViewer.show(
                            activity,
                            activity.getString(R.string.document_viewer_changelog_title),
                            DocumentViewer.CHANGELOG_URL
                        )
                    } else {
                        onNavigate(DEST_UPDATES)
                    }
                },
                onMiscClick = { onNavigate(DEST_MISC) },
                onDebugClick = { onNavigate(DEST_DEBUG) },
                onAboutClick = { onNavigate(DEST_ABOUT) },
                onSupportClintClick = { activity.startActivity(Intent(activity, SupportClintActivity::class.java)) }
            )
        }
    }
}

@Composable
private fun SettingsDetailPane(activity: SettingsActivity, destination: String?, onBack: () -> Unit) {
    if (destination == null) {
        SettingsEmptyDetailPane()
        return
    }
    Column(Modifier.fillMaxSize()) {
        SettingsToolbar(title = stringResource(destinationTitleRes(destination)), onBack = onBack)
        Box(Modifier.weight(1f)) {
            when (destination) {
                DEST_LOOK_AND_FEEL -> LookAndFeelPane(activity)
                DEST_BROWSER -> BrowserSettingsPane(activity)
                DEST_PRIVACY -> PrivacySettingsPane(activity)
                DEST_SITE_SETTINGS -> SiteSettingsPane(activity)
                DEST_DATA_SAVER -> DataSaverPane(activity)
                DEST_DOWNLOADS -> DownloadSettingsPane(activity)
                DEST_BACKUP_RESTORE -> BackupRestorePane(activity)
                DEST_UPDATES -> UpdateSettingsPane(activity)
                DEST_MISC -> MiscPane(activity)
                DEST_DEBUG -> DebugPane(activity)
                DEST_ABOUT -> AboutPane(activity)
            }
        }
    }
}

private fun destinationTitleRes(destination: String): Int = when (destination) {
    DEST_LOOK_AND_FEEL -> R.string.look_and_feel
    DEST_BROWSER -> R.string.browser_settings
    DEST_PRIVACY -> R.string.privacy_settings
    DEST_SITE_SETTINGS -> R.string.site_settings
    DEST_DATA_SAVER -> R.string.data_saver_title
    DEST_DOWNLOADS -> R.string.download_settings_title
    DEST_BACKUP_RESTORE -> R.string.backup_restore_title
    DEST_UPDATES -> R.string.pref_updates_title
    DEST_MISC -> R.string.pref_misc_title
    DEST_DEBUG -> R.string.debug_title
    DEST_ABOUT -> R.string.about
    else -> R.string.settings
}

@Composable
private fun SettingsToolbar(title: String, onBack: () -> Unit) {
    val colors = LocalClintColors.current
    Surface(color = colors.surface, shadowElevation = 4.dp, modifier = Modifier.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = colors.onSurface)
            }
            Text(
                title,
                color = colors.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingsEmptyDetailPane() {
    val colors = LocalClintColors.current
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.Tune,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    stringResource(R.string.settings_select_category),
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
