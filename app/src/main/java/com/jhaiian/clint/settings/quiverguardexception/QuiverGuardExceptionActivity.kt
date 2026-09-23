package com.jhaiian.clint.settings.quiverguardexception

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.settings.sitepermissions.SitePermissionManager
import com.jhaiian.clint.ui.rememberMaxContentWidth
import com.jhaiian.clint.settings.site.AddSiteDialog
import com.jhaiian.clint.settings.site.SiteEntry
import com.jhaiian.clint.settings.site.SiteListDeleteConfirmDialog
import com.jhaiian.clint.settings.site.SiteListScreen
import com.jhaiian.clint.settings.site.SiteListUiState
import com.jhaiian.clint.ui.theme.ClintComposeTheme
import com.jhaiian.clint.ui.theme.LocalClintColors

class QuiverGuardExceptionActivity : ClintActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val type = SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "system") ?: "system"
        val hideStatusBar = prefs.getBoolean("hide_status_bar", false)
        val hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)

        val listState = SiteListUiState()
        fun reload() {
            listState.allItems = SitePermissionManager.getAllByType(this, type).map { SiteEntry(it.first, it.second, it.third) }
        }
        reload()

        setContent {
            ClintComposeTheme(theme = theme) {
                val colors = LocalClintColors.current
                val maxContentWidth = rememberMaxContentWidth(this)

                Box {
                    SiteListScreen(
                        state = listState,
                        maxContentWidth = maxContentWidth,
                        title = stringResource(R.string.site_settings_quiver_guard),
                        searchHint = stringResource(R.string.quiver_guard_exception_search_hint),
                        emptyText = stringResource(R.string.quiver_guard_exception_no_saved_sites),
                        stateLabel = { _ -> stringResource(R.string.quiver_guard_exception_state_label) to colors.primary },
                        onExit = { finish() },
                        onAddClick = { listState.addDialogOpen = true },
                        onDeleteClick = { listState.deleteConfirmOpen = true }
                    )

                    if (listState.addDialogOpen) {
                        AddSiteDialog(
                            title = stringResource(R.string.quiver_guard_exception_add_site),
                            hideStatusBar = hideStatusBar, hideSystemNavigation = hideSystemNavigation,
                            showStateChoice = false,
                            onConfirm = { origin, _ ->
                                SitePermissionManager.setState(this@QuiverGuardExceptionActivity, origin, type, SitePermissionDatabase.STATE_ALLOW)
                                reload()
                                listState.addDialogOpen = false
                            },
                            onDismiss = { listState.addDialogOpen = false }
                        )
                    }
                    if (listState.deleteConfirmOpen) {
                        SiteListDeleteConfirmDialog(
                            title = stringResource(R.string.quiver_guard_exception_delete_title),
                            message = stringResource(R.string.quiver_guard_exception_delete_message, listState.selectedOrigins.size),
                            hideStatusBar = hideStatusBar, hideSystemNavigation = hideSystemNavigation,
                            onConfirm = {
                                listState.selectedOrigins.forEach { origin -> SitePermissionManager.deleteEntry(this@QuiverGuardExceptionActivity, origin, type) }
                                listState.removeSelectedItems()
                                listState.deleteConfirmOpen = false
                                Toast.makeText(this@QuiverGuardExceptionActivity, getString(R.string.quiver_guard_exception_items_deleted), Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { listState.deleteConfirmOpen = false }
                        )
                    }
                }
            }
        }
    }
}
