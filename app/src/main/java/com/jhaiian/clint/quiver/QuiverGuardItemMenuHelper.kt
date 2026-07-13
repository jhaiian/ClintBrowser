package com.jhaiian.clint.quiver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.ui.ClintToast

// Shows the per-row overflow menu anchored to a filter list item's ellipsis
// button, or the equivalent multi-item menu anchored to the toolbar's
// selection-mode button (see showSelectionItemOptionsMenu below). Both reuse
// the same popup_filter_list_item_options layout: single-item actions are
// scoped to exactly the list the user tapped regardless of its enabled or
// download state, while selection actions operate on every selected list at once.
internal fun QuiverGuardActivity.showFilterListItemOptionsMenu(filterList: FilterList, anchor: View) {
    val popupView = LayoutInflater.from(this).inflate(R.layout.popup_filter_list_item_options, null)
    val popup = PopupWindow(
        popupView,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    )
    popup.elevation = 12f
    popup.isOutsideTouchable = true

    popupView.findViewById<View>(R.id.menu_item_check_update).setOnClickListener {
        popup.dismiss(); confirmCheckUpdateForItem(filterList)
    }
    popupView.findViewById<View>(R.id.menu_item_force_update).setOnClickListener {
        popup.dismiss(); confirmForceUpdateForItem(filterList)
    }
    popupView.findViewById<View>(R.id.menu_item_remove).setOnClickListener {
        popup.dismiss(); confirmRemoveFilterListItem(filterList)
    }
    popupView.findViewById<View>(R.id.menu_item_copy_name).setOnClickListener {
        popup.dismiss(); copyFilterListName(filterList)
    }
    popupView.findViewById<View>(R.id.menu_item_copy_link).setOnClickListener {
        popup.dismiss(); copyFilterListDownloadLink(filterList)
    }
    popupView.findViewById<View>(R.id.menu_item_share_link).setOnClickListener {
        popup.dismiss(); shareFilterListDownloadLink(filterList)
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

// Shows the same popup_filter_list_item_options layout as the per-row ellipsis,
// but anchored to the toolbar's selection-mode button. A snapshot of the
// current selection is taken once, when the menu opens, and passed to every
// handler below so all six actions act on a consistent set even if the
// underlying selection could somehow change while the popup is showing.
internal fun QuiverGuardActivity.showSelectionItemOptionsMenu(anchor: View) {
    val selection = filterListAdapterOrNull()?.getSelectedItems().orEmpty()
    if (selection.isEmpty()) return

    val popupView = LayoutInflater.from(this).inflate(R.layout.popup_filter_list_item_options, null)
    val popup = PopupWindow(
        popupView,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    )
    popup.elevation = 12f
    popup.isOutsideTouchable = true

    popupView.findViewById<View>(R.id.menu_item_check_update).setOnClickListener {
        popup.dismiss(); confirmCheckUpdateForSelection(selection)
    }
    popupView.findViewById<View>(R.id.menu_item_force_update).setOnClickListener {
        popup.dismiss(); confirmForceUpdateForSelection(selection)
    }
    popupView.findViewById<View>(R.id.menu_item_remove).setOnClickListener {
        popup.dismiss(); showDeleteConfirmDialog()
    }
    popupView.findViewById<View>(R.id.menu_item_copy_name).setOnClickListener {
        popup.dismiss(); copySelectedFilterListNames(selection)
    }
    popupView.findViewById<View>(R.id.menu_item_copy_link).setOnClickListener {
        popup.dismiss(); copySelectedFilterListDownloadLinks(selection)
    }
    popupView.findViewById<View>(R.id.menu_item_share_link).setOnClickListener {
        popup.dismiss(); shareSelectedFilterListDownloadLinks(selection)
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

// Confirms then performs a conditional update check scoped to a single list.
// Blocked while another compile/update is running, and requires the list to
// already be downloaded since there is nothing to conditionally check against
// otherwise (no prior ETag/Last-Modified to send, no local file to compare).
private fun QuiverGuardActivity.confirmCheckUpdateForItem(filterList: FilterList) {
    if (isUpdateRunning || isCompileRunning || isDownloadInProgress(filterList.id)) {
        ClintToast.show(this, getString(R.string.filter_list_operation_in_progress), R.drawable.ic_warning_24)
        return
    }
    if (!filterList.isDownloaded) {
        ClintToast.show(this, getString(R.string.filter_list_item_not_downloaded, filterList.name), R.drawable.ic_warning_24)
        return
    }
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.filter_list_update_check_title))
        .setMessage(getString(R.string.filter_list_item_check_update_confirm_message, filterList.name))
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.filter_list_update_check_action)) { _, _ ->
            startFilterListUpdateCheck(
                forceUpdate = false,
                listsOverride = listOf(filterList),
                progressTitleOverride = getString(R.string.filter_list_item_check_update_progress_title, filterList.name)
            )
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Confirms then performs an unconditional re-download scoped to a single list.
// "Force" means no conditional headers are sent regardless of any stored ETag
// or Last-Modified, so the list is always redownloaded and always recompiled
// on success — never short-circuited to an UpToDate result.
private fun QuiverGuardActivity.confirmForceUpdateForItem(filterList: FilterList) {
    if (isUpdateRunning || isCompileRunning || isDownloadInProgress(filterList.id)) {
        ClintToast.show(this, getString(R.string.filter_list_operation_in_progress), R.drawable.ic_warning_24)
        return
    }
    if (!filterList.isDownloaded) {
        ClintToast.show(this, getString(R.string.filter_list_item_not_downloaded, filterList.name), R.drawable.ic_warning_24)
        return
    }
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.filter_list_force_update_action))
        .setMessage(getString(R.string.filter_list_item_force_update_confirm_message, filterList.name))
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.filter_list_force_update_action)) { _, _ ->
            startFilterListUpdateCheck(
                forceUpdate = true,
                listsOverride = listOf(filterList),
                progressTitleOverride = getString(R.string.filter_list_item_force_update_progress_title, filterList.name)
            )
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Confirms then stages a single list for removal, reusing the same staged-removal
// pattern as the multi-select delete flow: the database row and local file are
// only actually deleted once the next compile completes successfully, so the
// removal can still be discarded via the existing "discard changes" path.
private fun QuiverGuardActivity.confirmRemoveFilterListItem(filterList: FilterList) {
    if (isUpdateRunning || isCompileRunning || isDownloadInProgress(filterList.id)) {
        ClintToast.show(this, getString(R.string.filter_list_operation_in_progress), R.drawable.ic_warning_24)
        return
    }
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.filter_list_delete_confirm_title))
        .setMessage(getString(R.string.filter_list_delete_confirm_message, 1))
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(getString(R.string.history_delete_selected)) { _, _ ->
            stagePendingRemovals(listOf(filterList.id))
            filterListAdapterOrNull()?.removeItem(filterList.id)
            fastScrollerOrNull()?.notifyDataChanged()
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Copies the filter list's display name to the clipboard. A toast confirmation
// is only shown pre-Android-13, since the system clipboard overlay already
// confirms the copy on Tiramisu and above (matches the convention already used
// for downloads' copy-link/copy-filename actions elsewhere in the app).
private fun QuiverGuardActivity.copyFilterListName(filterList: FilterList) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.filter_list_name_clip_label), filterList.name))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        ClintToast.show(this, getString(R.string.filter_list_item_name_copied), R.drawable.ic_copy_24)
    }
}

// Copies the filter list's download URL to the clipboard.
private fun QuiverGuardActivity.copyFilterListDownloadLink(filterList: FilterList) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.filter_list_link_clip_label), filterList.downloadUrl))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        ClintToast.show(this, getString(R.string.filter_list_item_link_copied), R.drawable.ic_copy_24)
    }
}

// Opens the system share sheet with the filter list's download URL as plain text.
private fun QuiverGuardActivity.shareFilterListDownloadLink(filterList: FilterList) {
    try {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, filterList.downloadUrl)
                },
                getString(R.string.filter_list_share_chooser_title)
            )
        )
    } catch (_: Exception) {
    }
}

// Confirms then checks the downloaded subset of the selection for available
// updates. Lists that have never been downloaded are silently excluded rather
// than blocking the whole batch, since there is nothing to conditionally check
// against for them; this mirrors how the toolbar's "active lists" action only
// ever targets lists that can meaningfully be checked.
private fun QuiverGuardActivity.confirmCheckUpdateForSelection(selection: List<FilterList>) {
    if (isUpdateRunning || isCompileRunning || selection.any { isDownloadInProgress(it.id) }) {
        ClintToast.show(this, getString(R.string.filter_list_operation_in_progress), R.drawable.ic_warning_24)
        return
    }
    val downloaded = selection.filter { it.isDownloaded }
    if (downloaded.isEmpty()) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.filter_list_update_check_title))
            .setMessage(getString(R.string.filter_list_selection_not_downloaded))
            .setPositiveButton(getString(R.string.action_ok), null)
            .create().also { applyStatusBarFlagToDialog(it) }.show()
        return
    }
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.filter_list_update_check_title))
        .setMessage(getString(R.string.filter_list_check_update_selected_message, downloaded.size))
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.filter_list_update_check_action)) { _, _ ->
            startFilterListUpdateCheck(forceUpdate = false, listsOverride = downloaded)
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Confirms then force-updates the downloaded subset of the selection, the same
// way confirmForceUpdateForItem does for a single list.
private fun QuiverGuardActivity.confirmForceUpdateForSelection(selection: List<FilterList>) {
    if (isUpdateRunning || isCompileRunning || selection.any { isDownloadInProgress(it.id) }) {
        ClintToast.show(this, getString(R.string.filter_list_operation_in_progress), R.drawable.ic_warning_24)
        return
    }
    val downloaded = selection.filter { it.isDownloaded }
    if (downloaded.isEmpty()) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.filter_list_force_update_selected_confirm_title))
            .setMessage(getString(R.string.filter_list_selection_not_downloaded))
            .setPositiveButton(getString(R.string.action_ok), null)
            .create().also { applyStatusBarFlagToDialog(it) }.show()
        return
    }
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.filter_list_force_update_selected_confirm_title))
        .setMessage(getString(R.string.filter_list_force_update_selected_confirm_message, downloaded.size))
        .setNegativeButton(getString(R.string.action_cancel), null)
        .setPositiveButton(getString(R.string.filter_list_force_update_action)) { _, _ ->
            startFilterListUpdateCheck(forceUpdate = true, listsOverride = downloaded)
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Copies every selected list's name to the clipboard as one block of text with
// one name per line, so the result can be pasted as a ready-made list.
private fun QuiverGuardActivity.copySelectedFilterListNames(selection: List<FilterList>) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    val combined = selection.joinToString("\n") { it.name }
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.filter_list_name_clip_label), combined))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        ClintToast.show(this, getString(R.string.filter_list_selection_names_copied), R.drawable.ic_copy_24)
    }
}

// Copies every selected list's download URL to the clipboard as one block of
// text with one URL per line.
private fun QuiverGuardActivity.copySelectedFilterListDownloadLinks(selection: List<FilterList>) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    val combined = selection.joinToString("\n") { it.downloadUrl }
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.filter_list_link_clip_label), combined))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        ClintToast.show(this, getString(R.string.filter_list_selection_links_copied), R.drawable.ic_copy_24)
    }
}

// Opens the system share sheet with every selected list's download URL as a
// single block of plain text, one URL per line.
private fun QuiverGuardActivity.shareSelectedFilterListDownloadLinks(selection: List<FilterList>) {
    try {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, selection.joinToString("\n") { it.downloadUrl })
                },
                getString(R.string.filter_list_share_chooser_title)
            )
        )
    } catch (_: Exception) {
    }
}
