package com.jhaiian.clint.quiver

import com.jhaiian.clint.R

import android.net.Uri
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig
import com.jhaiian.clint.ui.listscreen.ConfirmDialogHost
import com.jhaiian.clint.ui.rememberMaxContentWidth
import com.jhaiian.clint.ui.theme.ClintComposeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

class QuiverGuardActivity : ClintActivity() {

    companion object {
        const val EXTRA_SHOW_SETUP_GUIDE = "show_setup_guide"
        const val EXTRA_AUTO_RECOMPILE = "auto_recompile"
    }

    internal lateinit var uiState: QuiverGuardUiState
    internal val activityScope: CoroutineScope get() = lifecycleScope
    internal var activeDownloadJob: Job? = null
    internal var activeUpdateJob: Job? = null
    internal lateinit var filePickerLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>

    private var filterListDb: FilterListDatabase? = null
    internal fun database(): FilterListDatabase =
        filterListDb ?: FilterListDatabase(this).also { filterListDb = it }

    private var manualFilterDatabase: ManualFilterDatabase? = null
    internal fun manualFilterDb(): ManualFilterDatabase =
        manualFilterDatabase ?: ManualFilterDatabase(this).also { manualFilterDatabase = it }

    private fun loadManualFilterSummary() {
        val rules = manualFilterDb().getAllRules()
        uiState.manualFilterSummary = ManualFilterSummary(ruleCount = rules.size, isEnabled = ManualFilterState.isEnabled(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        onBackPressedDispatcher.addCallback(this) {
            when {
                uiState.isFabMenuOpen -> uiState.isFabMenuOpen = false
                uiState.isSearchMode -> { uiState.isSearchMode = false; uiState.searchQuery = "" }
                uiState.isInSelectionMode -> uiState.exitSelectionMode()
                else -> handleBackNavigation()
            }
        }

        uiState = QuiverGuardUiState()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "system") ?: "system"
        val hideStatusBar = prefs.getBoolean("hide_status_bar", false)
        val hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)

        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri: Uri? = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) importFilterListFromFile(uri)
        }

        uiState.masterEnabled = prefs.getBoolean("quiver_guard_enabled", false)
        refreshFilterListDisplay()
        loadManualFilterSummary()
        performStartupValidation()

        if (intent.getBooleanExtra(EXTRA_SHOW_SETUP_GUIDE, false) && effectiveFilterLists().none { it.isEnabled && it.isDownloaded }) {
            showSetupGuideDialog()
        }
        if (intent.getBooleanExtra(EXTRA_AUTO_RECOMPILE, false)) {
            startCompilation()
        }

        setContent {
            ClintComposeTheme(theme = theme) {
                val maxContentWidth = rememberMaxContentWidth(this)
                Box {
                    QuiverGuardScreen(
                        state = uiState,
                        maxContentWidth = maxContentWidth,
                        onExit = { handleBackNavigation() },
                        onMasterToggle = { enabled ->
                            uiState.masterEnabled = enabled
                            prefs.edit().putBoolean("quiver_guard_enabled", enabled).apply()
                        },
                        onManualFilterClick = {
                            startActivity(android.content.Intent(this@QuiverGuardActivity, ManualFilterActivity::class.java))
                        },
                        onItemClick = { filterList ->
                            if (!filterList.isDownloaded) startFilterListDownload(filterList)
                            else setPendingEnabled(filterList.id, !filterList.isEnabled)
                        },
                        onAddFromFileClick = { launchAddFilterListFromFile() },
                        onAddFromLinkClick = { showAddFilterListDialog() },
                        onDeleteClick = {
                            uiState.confirmDialog = ConfirmDialogConfig(
                                title = getString(R.string.filter_list_delete_confirm_title),
                                message = getString(R.string.filter_list_delete_confirm_message, uiState.selectedIds.size),
                                negativeLabel = getString(R.string.action_cancel),
                                positiveLabel = getString(R.string.history_delete_selected),
                                onPositive = {
                                    stagePendingRemovals(uiState.selectedIds)
                                    uiState.exitSelectionMode()
                                }
                            )
                        },
                        onFabPrimaryClick = { startCompilation() },
                        onRefreshClick = { showFilterListUpdateConfirmation() },
                        onFilterListActionsClick = { uiState.filterListActionsMenuOpen = true },
                        onItemCheckUpdate = { filterList -> confirmCheckUpdateForItem(filterList) },
                        onItemForceUpdate = { filterList -> confirmForceUpdateForItem(filterList) },
                        onItemRemove = { filterList -> confirmRemoveFilterListItem(filterList) },
                        onItemCopyName = { filterList -> copyFilterListName(filterList) },
                        onItemCopyLink = { filterList -> copyFilterListDownloadLink(filterList) },
                        onItemShareLink = { filterList -> shareFilterListDownloadLink(filterList) },
                        onSelectionCheckUpdate = { confirmCheckUpdateForSelection() },
                        onSelectionForceUpdate = { confirmForceUpdateForSelection() },
                        onSelectionRemove = {
                            uiState.confirmDialog = ConfirmDialogConfig(
                                title = getString(R.string.filter_list_delete_confirm_title),
                                message = getString(R.string.filter_list_delete_confirm_message, uiState.selectedIds.size),
                                negativeLabel = getString(R.string.action_cancel),
                                positiveLabel = getString(R.string.history_delete_selected),
                                onPositive = { stagePendingRemovals(uiState.selectedIds); uiState.exitSelectionMode() }
                            )
                        },
                        onSelectionCopyName = { copySelectedFilterListNames() },
                        onSelectionCopyLink = { copySelectedFilterListDownloadLinks() },
                        onSelectionShareLink = { shareSelectedFilterListDownloadLinks() },
                        onCheckUpdateActive = { showActiveFilterListUpdateConfirmation(forceUpdate = false) },
                        onCheckUpdateAll = { showFilterListUpdateConfirmation() },
                        onForceUpdateActive = { showActiveFilterListUpdateConfirmation(forceUpdate = true) },
                        onForceUpdateAll = { showForceUpdateAllConfirmation() },
                        onRecompile = { showRecompileConfirmation() }
                    )

                    ConfirmDialogHost(uiState.confirmDialog, hideStatusBar, hideSystemNavigation) { uiState.confirmDialog = null }
                    DownloadProgressDialog(uiState.downloadProgress, hideStatusBar, hideSystemNavigation) { activeDownloadJob?.cancel() }
                    UpdateProgressDialog(uiState.updateProgress, hideStatusBar, hideSystemNavigation) { activeUpdateJob?.cancel() }
                    CompileProgressDialog(uiState.compileProgress, hideStatusBar, hideSystemNavigation)
                    CompileResultDialog(uiState.compileResult, hideStatusBar, hideSystemNavigation) { uiState.compileResult = null }
                    UpdateResultDialog(uiState.updateResult, hideStatusBar, hideSystemNavigation) { uiState.updateResult = null }

                    if (uiState.addFromLinkDialogOpen) {
                        AddFilterListFromLinkDialog(
                            hideStatusBar = hideStatusBar, hideSystemNavigation = hideSystemNavigation,
                            fetchStatus = uiState.addLinkFetchStatus,
                            onFetch = { url -> fetchFilterListFromUrl(url) },
                            onUrlChanged = { resetFilterListLinkFetch() },
                            onConfirm = { url, title -> confirmAddFilterListFromLink(url, title) },
                            onDismiss = { resetFilterListLinkFetch(); uiState.addFromLinkDialogOpen = false }
                        )
                    }
                    uiState.addFromFileImport?.let { imported ->
                        AddFilterListFromFileDialog(
                            imported = imported,
                            hideStatusBar = hideStatusBar, hideSystemNavigation = hideSystemNavigation,
                            onConfirm = { title -> confirmAddFilterListFromFile(title) },
                            onDismiss = { uiState.addFromFileImport = null }
                        )
                    }

                }
            }
        }
    }
}
