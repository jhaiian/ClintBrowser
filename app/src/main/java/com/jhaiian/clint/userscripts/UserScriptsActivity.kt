package com.jhaiian.clint.userscripts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.quiver.UpdateProgressDialog
import com.jhaiian.clint.quiver.UpdateResultDialog
import com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig
import com.jhaiian.clint.ui.listscreen.ConfirmDialogHost
import com.jhaiian.clint.ui.rememberMaxContentWidth
import com.jhaiian.clint.ui.theme.ClintComposeTheme
import androidx.lifecycle.lifecycleScope
import com.jhaiian.clint.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

class UserScriptsActivity : ClintActivity() {

    internal lateinit var db: UserScriptDatabase
    internal lateinit var uiState: UserScriptsUiState
    internal val activityScope: CoroutineScope get() = lifecycleScope
    internal var activeUpdateJob: Job? = null

    internal fun reload() {
        uiState.scripts = db.getAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        onBackPressedDispatcher.addCallback(this) {
            when {
                uiState.addFromLinkDialogOpen -> uiState.addFromLinkDialogOpen = false
                uiState.isFabMenuOpen -> uiState.isFabMenuOpen = false
                uiState.isSearchMode -> {
                    uiState.isSearchMode = false
                    uiState.searchQuery = ""
                }
                uiState.isInSelectionMode -> uiState.exitSelectionMode()
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        db = UserScriptDatabase(this)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "system") ?: "system"
        val hideStatusBar = prefs.getBoolean("hide_status_bar", false)
        val hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)

        uiState = UserScriptsUiState()
        uiState.isEnabled = UserScriptState.isEnabled(this)
        reload()

        setContent {
            ClintComposeTheme(theme = theme) {
                val maxContentWidth = rememberMaxContentWidth(this)
                val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let { importScript(it) }
                }
                Box {
                    UserScriptsScreen(
                        state = uiState,
                        maxContentWidth = maxContentWidth,
                        hideStatusBar = hideStatusBar,
                        hideSystemNavigation = hideSystemNavigation,
                        onExit = { finish() },
                        onToggleMasterEnabled = { enabled ->
                            uiState.isEnabled = enabled
                            UserScriptState.setEnabled(this@UserScriptsActivity, enabled)
                        },
                        onCreateNew = {
                            startActivity(Intent(this@UserScriptsActivity, UserScriptEditorActivity::class.java))
                        },
                        onUpload = {
                            uploadLauncher.launch(arrayOf("text/javascript", "application/javascript", "text/plain", "*/*"))
                        },
                        onAddFromLink = { showAddUserScriptFromLinkDialog() },
                        onEditScript = { script ->
                            startActivity(
                                Intent(this@UserScriptsActivity, UserScriptEditorActivity::class.java)
                                    .putExtra(UserScriptEditorActivity.EXTRA_SCRIPT_ID, script.id)
                            )
                        },
                        onToggleScriptEnabled = { script, enabled ->
                            db.setEnabled(script.id, enabled)
                            UserScriptState.bumpDataVersion(this@UserScriptsActivity)
                            reload()
                        },
                        onDeleteSelected = {
                            uiState.confirmDialog = ConfirmDialogConfig(
                                title = getString(R.string.user_scripts_delete_confirm_title),
                                message = getString(R.string.user_scripts_delete_confirm_message, uiState.selectedIds.size),
                                negativeLabel = getString(R.string.action_cancel),
                                positiveLabel = getString(R.string.history_delete_selected),
                                onPositive = {
                                    db.removeAll(uiState.selectedIds)
                                    UserScriptState.bumpDataVersion(this@UserScriptsActivity)
                                    uiState.exitSelectionMode()
                                    reload()
                                }
                            )
                        },
                        onRefreshClick = { showUserScriptUpdateConfirmation() },
                        onScriptActionsClick = { uiState.scriptActionsMenuOpen = true },
                        onItemCheckUpdate = { script -> confirmCheckUpdateForItem(script) },
                        onItemForceUpdate = { script -> confirmForceUpdateForItem(script) },
                        onItemRemove = { script -> confirmRemoveUserScriptItem(script) },
                        onItemCopyName = { script -> copyUserScriptName(script) },
                        onItemCopyLink = { script -> copyUserScriptSourceLink(script) },
                        onItemShareLink = { script -> shareUserScriptSourceLink(script) },
                        onSelectionCheckUpdate = { confirmCheckUpdateForSelection() },
                        onSelectionForceUpdate = { confirmForceUpdateForSelection() },
                        onSelectionCopyName = { copySelectedUserScriptNames() },
                        onSelectionCopyLink = { copySelectedUserScriptSourceLinks() },
                        onSelectionShareLink = { shareSelectedUserScriptSourceLinks() },
                        onCheckUpdateActive = { showActiveUserScriptUpdateConfirmation(forceUpdate = false) },
                        onCheckUpdateAll = { showUserScriptUpdateConfirmation() },
                        onForceUpdateActive = { showActiveUserScriptUpdateConfirmation(forceUpdate = true) },
                        onForceUpdateAll = { showForceUpdateAllUserScriptsConfirmation() }
                    )

                    ConfirmDialogHost(uiState.confirmDialog, hideStatusBar, hideSystemNavigation) { uiState.confirmDialog = null }
                    UpdateProgressDialog(uiState.updateProgress, hideStatusBar, hideSystemNavigation) { activeUpdateJob?.cancel() }
                    UpdateResultDialog(uiState.updateResult, hideStatusBar, hideSystemNavigation) { uiState.updateResult = null }

                    if (uiState.addFromLinkDialogOpen) {
                        AddUserScriptFromLinkDialog(
                            hideStatusBar = hideStatusBar, hideSystemNavigation = hideSystemNavigation,
                            fetchStatus = uiState.addLinkFetchStatus,
                            onFetch = { url -> fetchUserScriptFromUrl(url) },
                            onUrlChanged = { resetUserScriptLinkFetch() },
                            onConfirm = { url -> confirmAddUserScriptFromLink(url) },
                            onDismiss = { resetUserScriptLinkFetch(); uiState.addFromLinkDialogOpen = false }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) reload()
    }

    private fun importScript(uri: Uri) {
        val code = runCatching {
            contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull() ?: return
        startActivity(
            Intent(this, UserScriptEditorActivity::class.java)
                .putExtra(UserScriptEditorActivity.EXTRA_INITIAL_CODE, code)
        )
    }
}
