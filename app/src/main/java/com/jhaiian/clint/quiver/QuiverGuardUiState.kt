package com.jhaiian.clint.quiver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig
import com.jhaiian.clint.ui.listscreen.ListSortKey
import com.jhaiian.clint.ui.listscreen.ListSortOrder

data class ManualFilterSummary(val ruleCount: Int, val isEnabled: Boolean)

data class DownloadProgressUi(
    val filterListName: String,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val indeterminate: Boolean = true
)

data class UpdateProgressUi(
    val title: String,
    val totalCount: Int,
    val processedCount: Int = 0,
    val statusText: String = "",
    val currentListName: String = ""
)

data class UpdateResultUi(
    val title: String,
    val message: String,
    val onCompile: (() -> Unit)? = null
)

data class CompileProgressUi(
    val stageText: String,
    val listCounterText: String,
    val rulesText: String,
    val elapsedText: String
)

data class CompileResultRow(val label: String, val value: String)

data class CompileResultUi(
    val isSuccess: Boolean,
    val title: String,
    val rows: List<CompileResultRow>,
    val failureDetail: String? = null,
    val onRetry: (() -> Unit)? = null
)

sealed class AddLinkFetchStatus {
    object Idle : AddLinkFetchStatus()
    data class Fetching(val bytesRead: Long, val totalBytes: Long) : AddLinkFetchStatus()
    data class Fetched(val file: java.io.File, val sizeBytes: Long, val ruleCount: Long, val metadataTitle: String?) : AddLinkFetchStatus()
    data class Error(val message: String) : AddLinkFetchStatus()
}

class QuiverGuardUiState {
    var filterLists by mutableStateOf<List<FilterList>>(emptyList())
    var manualFilterSummary by mutableStateOf(ManualFilterSummary(ruleCount = 0, isEnabled = false))
    var masterEnabled by mutableStateOf(false)
    var bannerText by mutableStateOf<String?>(null)

    var searchQuery by mutableStateOf("")
    var isSearchMode by mutableStateOf(false)
    var sortKey by mutableStateOf(ListSortKey.TITLE)
    var sortOrder by mutableStateOf(ListSortOrder.ASCENDING)
    var selectedIds by mutableStateOf<Set<Long>>(emptySet())
    var isInSelectionMode by mutableStateOf(false)

    var isFabMenuOpen by mutableStateOf(false)
    var sortMenuOpen by mutableStateOf(false)
    var filterListActionsMenuOpen by mutableStateOf(false)
    var selectionOptionsMenuOpen by mutableStateOf(false)

    var pendingEnabledOverrides by mutableStateOf<Map<Long, Boolean>>(emptyMap())
    var pendingRemovedIds by mutableStateOf<Set<Long>>(emptySet())
    var isStartupDirty by mutableStateOf(false)
    var isCompileRunning by mutableStateOf(false)
    var isUpdateRunning by mutableStateOf(false)
    var downloadingIds by mutableStateOf<Set<Long>>(emptySet())

    var confirmDialog by mutableStateOf<ConfirmDialogConfig?>(null)
    var downloadProgress by mutableStateOf<DownloadProgressUi?>(null)
    var updateProgress by mutableStateOf<UpdateProgressUi?>(null)
    var updateResult by mutableStateOf<UpdateResultUi?>(null)
    var compileProgress by mutableStateOf<CompileProgressUi?>(null)
    var compileResult by mutableStateOf<CompileResultUi?>(null)
    var addFromLinkDialogOpen by mutableStateOf(false)
    var addLinkFetchStatus by mutableStateOf<AddLinkFetchStatus>(AddLinkFetchStatus.Idle)
    internal var addFromFileImport by mutableStateOf<LocalFilterListImportResult.Success?>(null)
    var setupGuideDialogOpen by mutableStateOf(false)

    fun isConfigurationDirty(): Boolean =
        pendingEnabledOverrides.isNotEmpty() || pendingRemovedIds.isNotEmpty() || isStartupDirty

    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun enterSelectionWith(id: Long) {
        isInSelectionMode = true
        selectedIds = selectedIds + id
    }

    fun selectAll(displayed: List<FilterList>) {
        selectedIds = selectedIds + displayed.map { it.id }
    }

    fun invertSelection(displayed: List<FilterList>) {
        val displayedIds = displayed.map { it.id }.toSet()
        val keptOutsideView = selectedIds - displayedIds
        val invertedWithinView = displayedIds - selectedIds
        selectedIds = keptOutsideView + invertedWithinView
    }

    fun deselectAll() {
        selectedIds = emptySet()
    }

    fun exitSelectionMode() {
        isInSelectionMode = false
        selectedIds = emptySet()
    }
}
