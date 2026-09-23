package com.jhaiian.clint.quiver

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig
import com.jhaiian.clint.ui.listscreen.ConfirmDialogHost
import com.jhaiian.clint.ui.rememberMaxContentWidth
import com.jhaiian.clint.ui.theme.ClintComposeTheme

class ManualFilterActivity : ClintActivity() {

    private lateinit var db: ManualFilterDatabase
    private lateinit var uiState: ManualFilterUiState
    private var deleteConfirm by mutableStateOf<ConfirmDialogConfig?>(null)

    private fun reload() {
        uiState.rules = db.getAllRules()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        onBackPressedDispatcher.addCallback(this) {
            when {
                uiState.isSearchMode -> { uiState.isSearchMode = false; uiState.searchQuery = "" }
                uiState.isInSelectionMode -> uiState.exitSelectionMode()
                else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        }

        db = ManualFilterDatabase(this)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "system") ?: "system"
        val hideStatusBar = prefs.getBoolean("hide_status_bar", false)
        val hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)

        uiState = ManualFilterUiState()
        uiState.isEnabled = ManualFilterState.isEnabled(this)
        reload()

        setContent {
            ClintComposeTheme(theme = theme) {
                val maxContentWidth = rememberMaxContentWidth(this)
                Box {
                    ManualFilterScreen(
                        state = uiState,
                        maxContentWidth = maxContentWidth,
                        onExit = { finish() },
                        onToggleEnabled = { enabled ->
                            uiState.isEnabled = enabled
                            ManualFilterState.setEnabled(this@ManualFilterActivity, enabled)
                        },
                        onAddClick = { uiState.ruleDialogMode = ManualFilterRuleDialogMode.Add },
                        onEditClick = { rule -> uiState.ruleDialogMode = ManualFilterRuleDialogMode.Edit(rule) },
                        onDeleteClick = { rule -> showDeleteConfirm(rule) },
                        onDeleteSelectedClick = { showDeleteSelectedConfirm() }
                    )

                    uiState.ruleDialogMode?.let { mode ->
                        ManualFilterRuleDialog(
                            mode = mode,
                            hideStatusBar = hideStatusBar, hideSystemNavigation = hideSystemNavigation,
                            onConfirm = { text ->
                                when (mode) {
                                    is ManualFilterRuleDialogMode.Add -> {
                                        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                                        if (lines.isNotEmpty()) db.addRules(lines)
                                    }
                                    is ManualFilterRuleDialogMode.Edit -> db.updateRuleText(mode.rule.id, text.trim())
                                }
                                reload()
                                uiState.ruleDialogMode = null
                            },
                            onDismiss = { uiState.ruleDialogMode = null }
                        )
                    }

                    ConfirmDialogHost(deleteConfirm, hideStatusBar, hideSystemNavigation) { deleteConfirm = null }
                }
            }
        }
    }

    private fun showDeleteConfirm(rule: ManualFilterRule) {
        deleteConfirm = ConfirmDialogConfig(
            title = getString(R.string.quiver_guard_manual_filter_delete_confirm_title),
            message = getString(R.string.quiver_guard_manual_filter_delete_confirm_message, rule.ruleText),
            negativeLabel = getString(R.string.action_cancel),
            positiveLabel = getString(R.string.history_delete_selected),
            onPositive = { db.deleteRule(rule.id); reload() }
        )
    }

    private fun showDeleteSelectedConfirm() {
        val ids = uiState.selectedIds
        if (ids.isEmpty()) return
        deleteConfirm = ConfirmDialogConfig(
            title = getString(R.string.quiver_guard_manual_filter_delete_selected_confirm_title),
            message = getString(R.string.quiver_guard_manual_filter_delete_selected_confirm_message, ids.size),
            negativeLabel = getString(R.string.action_cancel),
            positiveLabel = getString(R.string.history_delete_selected),
            onPositive = {
                for (id in ids) db.deleteRule(id)
                uiState.exitSelectionMode()
                reload()
            }
        )
    }
}
