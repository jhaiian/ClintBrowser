package com.jhaiian.clint.quiver

import android.widget.Switch
import com.jhaiian.clint.R

// Returns true when the user has unsaved changes that require a recompile before
// they take effect. Changes include toggled list states, staged removals, or a
// database/manifest mismatch detected at startup.
internal fun QuiverGuardActivity.isConfigurationDirty(): Boolean =
    pendingEnabledOverrides.isNotEmpty() || pendingRemovedIds.isNotEmpty() || isStartupDirty

// Records whether the user wants a list enabled or disabled without immediately
// writing to the database. The override is dropped when its value matches the
// persisted baseline so that the dirty flag is not set needlessly.
internal fun QuiverGuardActivity.setPendingEnabled(id: Long, enabled: Boolean) {
    val baseline = database().getRawEnabled(id) ?: enabled
    if (baseline == enabled) pendingEnabledOverrides.remove(id) else pendingEnabledOverrides[id] = enabled
    refreshFabState()
}

// Queues a set of filter lists for deletion and removes their entries from the
// pending-enabled map so toggled states for deleted lists do not linger.
// The actual database rows and local files are deleted when the next compile completes.
internal fun QuiverGuardActivity.stagePendingRemovals(ids: Collection<Long>) {
    if (ids.isEmpty()) return
    pendingRemovedIds.addAll(ids)
    ids.forEach { pendingEnabledOverrides.remove(it) }
    refreshFabState()
}

// Returns the current filter list collection with all pending in-memory changes
// applied. Staged removals are filtered out and enabled-state overrides are merged
// in so the adapter and the compiler always see a consistent view.
internal fun QuiverGuardActivity.effectiveFilterLists(): List<FilterList> =
    database().getAllFilterLists()
        .filterNot { it.id in pendingRemovedIds }
        .map { row -> pendingEnabledOverrides[row.id]?.let { row.copy(isEnabled = it) } ?: row }

// Reloads the adapter from the current effective list state and notifies the fast
// scroller so its thumb position stays accurate.
internal fun QuiverGuardActivity.refreshFilterListDisplay() {
    filterListAdapterOrNull()?.updateItems(effectiveFilterLists())
    fastScrollerOrNull()?.notifyDataChanged()
}

// Updates the FAB icon and click target based on the current state:
// when there are unsaved changes the FAB becomes a "compile" button,
// otherwise it opens the add-custom-list dialog.
internal fun QuiverGuardActivity.refreshFabState() {
    val masterEnabled = findViewById<Switch>(R.id.switch_quiver_guard)?.isChecked ?: true
    val dirty = isConfigurationDirty()
    val locked = isCompileRunning || isUpdateRunning

    if (dirty) {
        fabAdd.setImageResource(R.drawable.ic_save_24)
        fabAdd.contentDescription = getString(R.string.quiver_guard_compile_fab_desc)
        fabAdd.setOnClickListener { startCompilation() }
    } else {
        fabAdd.setImageResource(R.drawable.ic_add_24)
        fabAdd.contentDescription = getString(R.string.filter_list_add_fab_desc)
        fabAdd.setOnClickListener { showAddCustomFilterListDialog() }
    }

    fabAdd.isEnabled = masterEnabled && !locked
    fabAdd.alpha = if (!masterEnabled || locked) 0.38f else 1f

    setRefreshEnabled(masterEnabled && !locked)
}

// Cleans up never-compiled custom lists whose local files and database rows
// would otherwise become orphans when the user discards unsaved changes.
// All pending state is then cleared so the activity returns to a clean baseline.
internal fun QuiverGuardActivity.discardPendingChanges() {
    val neverCompiledCustomIds = database().getNeverCompiledCustomIds()
    if (neverCompiledCustomIds.isNotEmpty()) {
        for (id in neverCompiledCustomIds) {
            val localFile = FilterListDownloader.localFileFor(applicationContext, id)
            if (localFile.exists()) localFile.delete()
        }
        database().deleteFilterLists(neverCompiledCustomIds)
    }
    pendingEnabledOverrides.clear()
    pendingRemovedIds.clear()
    isStartupDirty = false
    refreshFilterListDisplay()
    refreshFabState()
}

// Disables or re-enables the recycler and selection FAB during long-running
// operations such as compilation and update checks so the user cannot modify
// the list state while the operation is in progress.
internal fun QuiverGuardActivity.setInteractionsEnabled(enabled: Boolean) {
    recycler.isEnabled = enabled
    fabDelete.isEnabled = enabled
    filterListAdapterOrNull()?.setInteractionLocked(!enabled)
}
