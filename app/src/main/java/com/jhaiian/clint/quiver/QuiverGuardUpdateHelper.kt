package com.jhaiian.clint.quiver

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.jhaiian.clint.R
import com.jhaiian.clint.downloads.formatFileSize
import com.jhaiian.clint.ui.ClintToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Shows a confirmation dialog before starting an update check so the user
// is aware that the operation will make network requests. Skips immediately
// if no lists have been downloaded yet, since there is nothing to update.
internal fun QuiverGuardActivity.showFilterListUpdateConfirmation() {
    if (isUpdateRunning || isCompileRunning) return
    val downloadedCount = effectiveFilterLists().count { it.isDownloaded }
    if (downloadedCount == 0) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.filter_list_update_check_title))
            .setMessage(getString(R.string.filter_list_update_no_lists_message))
            .setPositiveButton(getString(R.string.action_ok), null)
            .create().also { applyStatusBarFlagToDialog(it) }.show()
        return
    }
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.filter_list_update_check_title))
        .setMessage(getString(R.string.filter_list_update_check_message, downloadedCount))
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.filter_list_update_check_action)) { _, _ ->
            startFilterListUpdateCheck(forceUpdate = false)
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Iterates over filter lists using FilterListUpdateChecker, emitting progress
// events for each one. Results are accumulated and handled by onUpdateCheckComplete.
// listsOverride lets callers supply a pre-filtered subset (e.g. only enabled lists,
// or a single list from the per-row overflow menu). When null, all downloaded lists
// are used. When forceUpdate is true, the checker skips conditional HTTP headers so
// the server always responds with fresh content. progressTitleOverride lets callers
// (e.g. single-item operations) show a more specific dialog title than the generic
// "Checking for Updates" / "Force Updating Filter Lists" defaults.
internal fun QuiverGuardActivity.startFilterListUpdateCheck(
    forceUpdate: Boolean = false,
    listsOverride: List<FilterList>? = null,
    progressTitleOverride: String? = null
) {
    if (isUpdateRunning || isCompileRunning) return
    val activity = this
    val filterLists = listsOverride ?: effectiveFilterLists().filter { it.isDownloaded }
    if (filterLists.isEmpty()) return

    isUpdateRunning = true
    setRefreshEnabled(false)

    val progressView = layoutInflater.inflate(R.layout.dialog_filter_list_update_progress, null)
    val progressBar = progressView.findViewById<LinearProgressIndicator>(R.id.update_progress_bar)
    val tvCounter = progressView.findViewById<TextView>(R.id.update_progress_counter)
    val tvStatus = progressView.findViewById<TextView>(R.id.update_progress_status)
    val tvListName = progressView.findViewById<TextView>(R.id.update_progress_list_name)

    progressBar.max = filterLists.size
    progressBar.progress = 0
    tvCounter.text = getString(R.string.filter_list_update_progress_counter, 0, filterLists.size)
    // Force update never sends conditional headers, so "Checking" would be
    // misleading — every list is going to be redownloaded outright.
    tvStatus.text = if (forceUpdate) {
        getString(R.string.filter_list_force_update_progress_preparing)
    } else {
        getString(R.string.filter_list_update_progress_checking)
    }
    tvListName.text = ""

    var updateJob: Job? = null

    // Use a different title when force-updating so the user knows all lists
    // are being re-downloaded rather than conditionally checked. A caller-supplied
    // override takes precedence (used for single-item operations to name the list).
    val dialogTitle = progressTitleOverride ?: if (forceUpdate) {
        getString(R.string.filter_list_force_update_progress_title)
    } else {
        getString(R.string.filter_list_update_progress_title)
    }

    val dialog = MaterialAlertDialogBuilder(activity, getDialogTheme())
        .setTitle(dialogTitle)
        .setView(progressView)
        .setCancelable(false)
        .setNegativeButton(getString(R.string.action_cancel)) { _, _ -> updateJob?.cancel() }
        .create()
        .also { applyStatusBarFlagToDialog(it) }
    dialog.show()

    val updatedResults = mutableListOf<FilterListUpdateItemResult.Updated>()
    val failedResults = mutableListOf<FilterListUpdateItemResult.Failed>()
    var upToDateCount = 0
    var processedCount = 0

    updateJob = activityScope.launch {
        try {
            FilterListUpdateChecker.checkAndUpdateAll(
                applicationContext,
                filterLists,
                forceUpdate = forceUpdate
            ).collect { event ->
                when (event) {
                    is FilterListUpdateEvent.CheckingList -> {
                        processedCount = event.index
                        progressBar.progress = event.index
                        tvCounter.text = getString(
                            R.string.filter_list_update_progress_counter, event.index + 1, event.total
                        )
                        tvStatus.text = if (forceUpdate) {
                            getString(R.string.filter_list_force_update_progress_preparing)
                        } else {
                            getString(R.string.filter_list_update_progress_checking)
                        }
                        tvListName.text = event.filterList.name
                    }
                    is FilterListUpdateEvent.DownloadingList -> {
                        // Show determinate progress once the content-length is known,
                        // or a size-only indicator when it is not available.
                        tvStatus.text = if (event.totalBytes > 0L) {
                            getString(
                                R.string.filter_list_update_progress_downloading_known,
                                formatFileSize(event.bytesRead), formatFileSize(event.totalBytes)
                            )
                        } else {
                            getString(R.string.filter_list_update_progress_downloading_unknown, formatFileSize(event.bytesRead))
                        }
                    }
                    is FilterListUpdateEvent.ItemComplete -> {
                        processedCount++
                        progressBar.progress = processedCount
                        when (val result = event.result) {
                            is FilterListUpdateItemResult.Updated -> {
                                // Persist the new download metadata so future update checks
                                // can use the new ETag or Last-Modified headers.
                                val downloadedAt = System.currentTimeMillis()
                                database().updateDownloadResult(
                                    result.filterList.id,
                                    FilterListDownloader.localFileFor(applicationContext, result.filterList.id).absolutePath,
                                    result.newFileSizeBytes, downloadedAt, result.newRuleCount,
                                    result.newEtag, result.newLastModified
                                )
                                updatedResults.add(result)
                            }
                            is FilterListUpdateItemResult.Failed -> failedResults.add(result)
                            is FilterListUpdateItemResult.UpToDate -> upToDateCount++
                            is FilterListUpdateItemResult.Skipped -> {}
                        }
                    }
                }
            }

            dialog.dismiss()
            onUpdateCheckComplete(updatedResults, failedResults, upToDateCount)
        } catch (e: CancellationException) {
            dialog.dismiss()
            ClintToast.show(activity, getString(R.string.filter_list_update_cancelled), R.drawable.ic_warning_24)
            throw e
        } catch (_: Exception) {
            dialog.dismiss()
            ClintToast.show(activity, getString(R.string.filter_list_update_error_generic), R.drawable.ic_warning_24)
        } finally {
            isUpdateRunning = false
            refreshFabState()
        }
    }
}

// Handles the four possible outcome combinations after all lists are processed:
// all up-to-date (toast), all updated successfully (toast + recompile),
// some updated and some failed (dialog with recompile option), all failed (dialog).
private fun QuiverGuardActivity.onUpdateCheckComplete(
    updatedResults: List<FilterListUpdateItemResult.Updated>,
    failedResults: List<FilterListUpdateItemResult.Failed>,
    upToDateCount: Int
) {
    refreshFilterListDisplay()

    when {
        updatedResults.isEmpty() && failedResults.isEmpty() -> {
            ClintToast.show(this, getString(R.string.filter_list_update_all_up_to_date), R.drawable.ic_check_24)
        }
        updatedResults.isNotEmpty() && failedResults.isEmpty() -> {
            ClintToast.show(
                this, getString(R.string.filter_list_update_success_recompiling, updatedResults.size), R.drawable.ic_check_24
            )
            triggerRecompilationAfterUpdate()
        }
        updatedResults.isNotEmpty() && failedResults.isNotEmpty() -> {
            showPartialUpdateResultDialog(updatedResults.size, failedResults)
        }
        else -> {
            val failedNames = failedResults.joinToString(separator = "\n") { "• ${it.filterList.name}" }
            MaterialAlertDialogBuilder(this, getDialogTheme())
                .setTitle(getString(R.string.filter_list_update_result_title))
                .setMessage(getString(R.string.filter_list_update_all_failed, failedResults.size, failedNames))
                .setPositiveButton(getString(R.string.action_ok), null)
                .create().also { applyStatusBarFlagToDialog(it) }.show()
        }
    }
}

// Shown when at least one list was updated and at least one failed, giving the
// user the choice to compile with the partial update or cancel.
private fun QuiverGuardActivity.showPartialUpdateResultDialog(
    updatedCount: Int,
    failedResults: List<FilterListUpdateItemResult.Failed>
) {
    val failedNames = failedResults.joinToString(separator = "\n") { "• ${it.filterList.name}" }
    val message = getString(
        R.string.filter_list_update_partial_result, updatedCount, failedResults.size, failedNames
    )
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.filter_list_update_result_title))
        .setMessage(message)
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.quiver_guard_back_dialog_compile)) { _, _ ->
            triggerRecompilationAfterUpdate()
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Starts a compile run after a successful update so the engine immediately
// benefits from the freshly downloaded rule content.
private fun QuiverGuardActivity.triggerRecompilationAfterUpdate() {
    if (!isCompileRunning) {
        startCompilation()
    }
}

// Enables or disables the refresh/update button and the filter list actions
// button, and adjusts their alpha so they appear visually inactive during
// long-running operations such as update checks and compilation.
internal fun QuiverGuardActivity.setRefreshEnabled(enabled: Boolean) {
    btnRefresh?.let { btn ->
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1f else 0.38f
    }
    btnFilterListActions?.let { btn ->
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1f else 0.38f
    }
}

// Returns all filter lists that are both enabled and downloaded. These are the
// "active" lists — the ones currently contributing to request filtering. Used
// by the overflow menu operations that target the active subset only.
internal fun QuiverGuardActivity.getActiveFilterLists(): List<FilterList> =
    effectiveFilterLists().filter { it.isEnabled && it.isDownloaded }

// Shows the filter list actions popup anchored to the toolbar button. The menu
// provides active-list and all-list variants of update checks, force updates,
// and a direct recompile trigger. "Active" means currently enabled filter lists.
internal fun QuiverGuardActivity.showFilterListActionsMenu(anchor: View) {
    val popupView = LayoutInflater.from(this).inflate(R.layout.popup_filter_list_actions, null)
    val popup = android.widget.PopupWindow(
        popupView,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    )
    popup.elevation = 12f
    popup.isOutsideTouchable = true

    popupView.findViewById<View>(R.id.menu_check_update_active).setOnClickListener {
        popup.dismiss()
        showActiveFilterListUpdateConfirmation(forceUpdate = false)
    }
    popupView.findViewById<View>(R.id.menu_check_update_all).setOnClickListener {
        popup.dismiss()
        showFilterListUpdateConfirmation()
    }
    popupView.findViewById<View>(R.id.menu_force_update_active).setOnClickListener {
        popup.dismiss()
        showActiveFilterListUpdateConfirmation(forceUpdate = true)
    }
    popupView.findViewById<View>(R.id.menu_force_update_all).setOnClickListener {
        popup.dismiss()
        showForceUpdateAllConfirmation()
    }
    popupView.findViewById<View>(R.id.menu_recompile).setOnClickListener {
        popup.dismiss()
        showRecompileConfirmation()
    }

    popupView.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val maxPopupH = (resources.displayMetrics.heightPixels * 0.90).toInt()
    if (popupView.measuredHeight > maxPopupH) popup.height = maxPopupH
    val xOff = -popupView.measuredWidth + anchor.width
    popup.showAsDropDown(anchor, xOff, 0, Gravity.TOP or Gravity.END)
}

// Confirms before checking for updates on (or force-downloading) only the
// enabled+downloaded filter lists. If no lists are enabled, informs the user
// so they know to enable at least one list first.
private fun QuiverGuardActivity.showActiveFilterListUpdateConfirmation(forceUpdate: Boolean) {
    if (isUpdateRunning || isCompileRunning) {
        ClintToast.show(this, getString(R.string.filter_list_operation_in_progress), R.drawable.ic_warning_24)
        return
    }
    val activeLists = getActiveFilterLists()
    if (activeLists.isEmpty()) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(
                if (forceUpdate) getString(R.string.filter_list_force_update_active_confirm_title)
                else getString(R.string.filter_list_update_check_title)
            )
            .setMessage(getString(R.string.filter_list_no_active_selected))
            .setPositiveButton(getString(R.string.action_ok), null)
            .create().also { applyStatusBarFlagToDialog(it) }.show()
        return
    }
    val (title, message, action) = if (forceUpdate) {
        Triple(
            getString(R.string.filter_list_force_update_active_confirm_title),
            getString(R.string.filter_list_force_update_active_confirm_message, activeLists.size),
            getString(R.string.filter_list_force_update_action)
        )
    } else {
        Triple(
            getString(R.string.filter_list_update_check_title),
            getString(R.string.filter_list_check_update_active_message, activeLists.size),
            getString(R.string.filter_list_update_check_action)
        )
    }
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(action) { _, _ ->
            startFilterListUpdateCheck(forceUpdate = forceUpdate, listsOverride = activeLists)
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Shows a confirmation dialog before force-updating all downloaded filter lists.
// Reports the count so the user knows the scope of the network operation.
internal fun QuiverGuardActivity.showForceUpdateAllConfirmation() {
    if (isUpdateRunning || isCompileRunning) {
        ClintToast.show(this, getString(R.string.filter_list_operation_in_progress), R.drawable.ic_warning_24)
        return
    }
    val downloadedCount = effectiveFilterLists().count { it.isDownloaded }
    if (downloadedCount == 0) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.filter_list_force_update_all_confirm_title))
            .setMessage(getString(R.string.filter_list_force_update_no_lists_message))
            .setPositiveButton(getString(R.string.action_ok), null)
            .create().also { applyStatusBarFlagToDialog(it) }.show()
        return
    }
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.filter_list_force_update_all_confirm_title))
        .setMessage(getString(R.string.filter_list_force_update_all_confirm_message, downloadedCount))
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.filter_list_force_update_action)) { _, _ ->
            startFilterListUpdateCheck(forceUpdate = true)
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Shows a confirmation dialog before triggering a full recompile from the
// overflow menu. Unlike the FAB compile path (which only activates when there
// are unsaved changes), this can be triggered at any time.
internal fun QuiverGuardActivity.showRecompileConfirmation() {
    if (isCompileRunning) return
    if (isUpdateRunning) {
        ClintToast.show(this, getString(R.string.filter_list_operation_in_progress), R.drawable.ic_warning_24)
        return
    }
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.filter_list_recompile_confirm_title))
        .setMessage(getString(R.string.filter_list_recompile_confirm_message))
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.quiver_guard_back_dialog_compile)) { _, _ ->
            startCompilation()
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}
