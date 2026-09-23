package com.jhaiian.clint.userscripts

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig
import com.jhaiian.clint.ui.listscreen.ConfirmDialogHost
import com.jhaiian.clint.ui.theme.ClintComposeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserScriptEditorActivity : ClintActivity() {

    private lateinit var db: UserScriptDatabase
    private lateinit var uiState: UserScriptEditorUiState
    private var confirmDialog by mutableStateOf<ConfirmDialogConfig?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        db = UserScriptDatabase(this)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "system") ?: "system"
        val hideStatusBar = prefs.getBoolean("hide_status_bar", false)
        val hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)

        val scriptId = intent.getLongExtra(EXTRA_SCRIPT_ID, -1L)
        val initialCode = intent.getStringExtra(EXTRA_INITIAL_CODE)
        val existing = if (scriptId >= 0) db.getById(scriptId) else null

        uiState = UserScriptEditorUiState()
        uiState.scriptId = existing?.id ?: -1L
        uiState.code = existing?.code ?: initialCode ?: defaultUserScriptTemplate()
        uiState.initialCode = uiState.code

        onBackPressedDispatcher.addCallback(this) {
            handleBackNavigation()
        }

        setContent {
            ClintComposeTheme(theme = theme) {
                Box {
                    UserScriptEditorScreen(
                        state = uiState,
                        onBack = { handleBackNavigation() },
                        onSave = { saveScript() },
                        onDeleteClick = { showDeleteConfirm() },
                        hideStatusBar = hideStatusBar,
                        hideSystemNavigation = hideSystemNavigation
                    )
                    ConfirmDialogHost(confirmDialog, hideStatusBar, hideSystemNavigation) { confirmDialog = null }
                }
            }
        }
    }

    private fun handleBackNavigation() {
        if (uiState.isSaving) return
        if (!uiState.isDirty) {
            finish()
            return
        }
        confirmDialog = ConfirmDialogConfig(
            title = getString(R.string.user_scripts_editor_unsaved_dialog_title),
            message = getString(R.string.user_scripts_editor_unsaved_dialog_message),
            neutralLabel = getString(R.string.action_cancel),
            negativeLabel = getString(R.string.user_scripts_editor_unsaved_dialog_discard),
            onNegative = { finish() },
            positiveLabel = getString(R.string.action_save),
            onPositive = { saveScript() }
        )
    }

    private fun saveScript() {
        if (uiState.isSaving) return
        val code = uiState.code
        if (code.isBlank()) return
        uiState.isSaving = true
        lifecycleScope.launch {
            val requiresCache = withContext(Dispatchers.IO) {
                val meta = UserScriptMetadataParser.parse(code, "")
                if (meta.requires.isEmpty() && meta.resources.isEmpty()) "" else UserScriptRequireFetcher.fetchAssets(meta)
            }
            withContext(Dispatchers.IO) {
                if (uiState.isNew) {
                    val newId = db.insert(code, requiresCache, true)
                    uiState.scriptId = newId
                } else {
                    db.update(uiState.scriptId, code, requiresCache)
                }
            }
            UserScriptState.bumpDataVersion(this@UserScriptEditorActivity)
            uiState.isSaving = false
            finish()
        }
    }

    private fun showDeleteConfirm() {
        if (uiState.isNew) return
        confirmDialog = ConfirmDialogConfig(
            title = getString(R.string.user_scripts_delete_confirm_title),
            message = getString(R.string.user_scripts_delete_one_confirm_message),
            negativeLabel = getString(R.string.action_cancel),
            positiveLabel = getString(R.string.history_delete_selected),
            onPositive = {
                db.remove(uiState.scriptId)
                UserScriptState.bumpDataVersion(this@UserScriptEditorActivity)
                finish()
            }
        )
    }

    companion object {
        const val EXTRA_SCRIPT_ID = "extra_script_id"
        const val EXTRA_INITIAL_CODE = "extra_initial_code"
    }
}
