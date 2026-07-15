package com.jhaiian.clint.quiver

import android.widget.Switch
import com.jhaiian.clint.R
import com.jhaiian.clint.quiver.engine.CompiledManifest
import com.jhaiian.clint.quiver.engine.CompiledManifestData
import com.jhaiian.clint.quiver.engine.QuiverGuardPaths

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
    refreshManualFilterSummary()
    fastScrollerOrNull()?.notifyDataChanged()
}

// Pushes the current rule count and enabled state to the pinned Manual Filter row. Called after
// any change to the rule set and whenever the activity resumes, since ManualFilterActivity edits
// rules in a separate Activity instance and has no direct reference to this adapter.
internal fun QuiverGuardActivity.refreshManualFilterSummary() {
    val rules = manualFilterDb().getAllRules()
    filterListAdapterOrNull()?.setManualFilterSummary(
        ManualFilterSummary(ruleCount = rules.size, isEnabled = ManualFilterState.isEnabled(this))
    )
}

// True when the manual filter's contribution to a compile (whether it is included at all, and
// if so its rule content) no longer matches what the last successful compile recorded. Mirrors
// the "contributes to the compile" condition startCompilation() uses so this never reports dirty
// for a state that would actually compile to the same manifest entry.
internal fun QuiverGuardActivity.isManualFilterDirty(manifest: CompiledManifestData): Boolean {
    val rules = manualFilterDb().getAllRules()
    val contributesNow = ManualFilterState.isEnabled(this) && rules.isNotEmpty()
    val manifestEntry = manifest.entries.firstOrNull { it.id == ManualFilterState.COMPILE_ID }
    val contributedBefore = manifestEntry != null
    if (!contributesNow && !contributedBefore) return false
    if (contributesNow != contributedBefore) return true
    return manifestEntry?.contentFingerprint != ManualFilterState.contentFingerprint(rules)
}

// Escalates isStartupDirty when the manual filter changed since the last compile, or since the
// engine was ever compiled at all. This only ever turns the flag on, mirroring how
// performStartupValidation treats FilterList changes, so it is safe to call every time the user
// returns to this activity from ManualFilterActivity.
internal fun QuiverGuardActivity.recheckManualFilterDirtyState() {
    if (isStartupDirty) return
    val manifest = CompiledManifest.read(QuiverGuardPaths.manifestFile(this))
    val dirty = if (manifest == null) {
        ManualFilterState.isEnabled(this) && manualFilterDb().getAllRules().isNotEmpty()
    } else {
        isManualFilterDirty(manifest)
    }
    if (dirty) {
        isStartupDirty = true
        refreshFabState()
    }
}

// Updates the FAB icon and click target, and keeps the "Compilation required" banner in sync,
// based on the current isConfigurationDirty() state. This is the single place that decides
// whether the banner should be visible, so every action that can change pendingEnabledOverrides,
// pendingRemovedIds, or isStartupDirty only needs to call this afterward rather than also
// remembering to show or hide the banner itself.
internal fun QuiverGuardActivity.refreshFabState() {
    val masterEnabled = findViewById<Switch>(R.id.switch_quiver_guard)?.isChecked ?: true
    val dirty = isConfigurationDirty()
    val locked = isCompileRunning || isUpdateRunning

    // The FAB menu only makes sense for the "add" role below; the "save" role
    // performs its action directly, and a disabled FAB shouldn't have a menu
    // left open behind it either.
    closeFabMenu()

    if (dirty) {
        fabAdd.setImageResource(R.drawable.ic_save_24)
        fabAdd.contentDescription = getString(R.string.quiver_guard_compile_fab_desc)
        fabAdd.setOnClickListener { startCompilation() }
        showStartupBanner(getString(R.string.quiver_guard_banner_recompile_needed))
    } else {
        fabAdd.setImageResource(R.drawable.ic_add_24)
        fabAdd.contentDescription = getString(R.string.filter_list_add_fab_desc)
        fabAdd.setOnClickListener { toggleFabMenu() }
        hideBanner()
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
    // isStartupDirty may have been true partly (or entirely) because of manual filter changes,
    // which are already saved and were never part of the pending state just cleared above, so
    // it needs a fresh look rather than staying cleared unconditionally.
    recheckManualFilterDirtyState()
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
