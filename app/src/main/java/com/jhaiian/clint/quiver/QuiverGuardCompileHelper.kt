package com.jhaiian.clint.quiver

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.jhaiian.clint.R
import com.jhaiian.clint.downloads.formatFileSize
import com.jhaiian.clint.quiver.engine.CompileEvent
import com.jhaiian.clint.quiver.engine.CompileResult
import com.jhaiian.clint.quiver.engine.CompileStage
import com.jhaiian.clint.quiver.engine.CompiledManifest
import com.jhaiian.clint.quiver.engine.CompiledManifestData
import com.jhaiian.clint.quiver.engine.CompiledManifestEntry
import com.jhaiian.clint.quiver.engine.FilterListCompileInput
import com.jhaiian.clint.quiver.engine.QuiverGuardCompiler
import com.jhaiian.clint.quiver.engine.QuiverGuardPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat

// Shown the first time a user enables Quiver Guard to explain that no lists
// are active yet and that they need to download and compile at least one before
// filtering takes effect.
internal fun QuiverGuardActivity.showSetupGuideDialog() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.quiver_guard_no_active_lists_title))
        .setMessage(getString(R.string.quiver_guard_no_active_lists_message))
        .setPositiveButton(getString(R.string.action_ok)) { _, _ ->
            // Show the experimental feature notice on first entry so the user is
            // aware that the feature may have rough edges.
            if (!prefs.getBoolean(QuiverGuardActivity.PREF_EXPERIMENTAL_SHOWN, false)) {
                showExperimentalDialog()
            }
        }
        .create()
        .also { applyStatusBarFlagToDialog(it) }
        .show()
}

// Displays a one-time notice that Quiver Guard is an experimental feature.
// The dismiss button is disabled for three seconds to ensure the user reads
// the message before confirming, then becomes enabled once the countdown ends.
internal fun QuiverGuardActivity.showExperimentalDialog() {
    PreferenceManager.getDefaultSharedPreferences(this)
        .edit()
        .putBoolean(QuiverGuardActivity.PREF_EXPERIMENTAL_SHOWN, true)
        .apply()

    val dialog = MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.quiver_guard_experimental_title))
        .setMessage(getString(R.string.quiver_guard_experimental_message))
        .setPositiveButton(getString(R.string.quiver_guard_experimental_ok_countdown, 3), null)
        .setCancelable(false)
        .create()
        .also { applyStatusBarFlagToDialog(it) }
    dialog.show()

    val positiveButton = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
    positiveButton.isEnabled = false

    activityScope.launch {
        for (i in 3 downTo 1) {
            positiveButton.text = getString(R.string.quiver_guard_experimental_ok_countdown, i)
            delay(1000L)
        }
        positiveButton.text = getString(R.string.action_ok)
        positiveButton.isEnabled = true
    }
}

// Handles the hardware or gesture back action. If a compile is running, back
// is suppressed so the user cannot leave mid-compile. If there are unsaved
// changes, a dialog asks whether to compile or discard them before exiting.
internal fun QuiverGuardActivity.handleBackNavigation() {
    if (isCompileRunning) return
    if (!isConfigurationDirty()) {
        finish()
        return
    }
    showCompilationRequiredDialog(onCompile = { startCompilation() }, onDiscard = {
        discardPendingChanges()
        finish()
    })
}

// Shown when back is pressed with unsaved changes. The three options are:
// compile now (stays in activity until compile finishes), discard and exit,
// or cancel and stay.
internal fun QuiverGuardActivity.showCompilationRequiredDialog(
    onCompile: () -> Unit,
    onDiscard: () -> Unit
) {
    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.quiver_guard_back_dialog_title))
        .setMessage(getString(R.string.quiver_guard_back_dialog_message))
        .setNeutralButton(getString(R.string.action_cancel), null)
        .setNegativeButton(getString(R.string.quiver_guard_back_dialog_discard)) { _, _ -> onDiscard() }
        .setPositiveButton(getString(R.string.quiver_guard_back_dialog_compile)) { _, _ -> onCompile() }
        .create()
        .also { applyStatusBarFlagToDialog(it) }
        .show()
}

// Called on activity start to check whether the compiled database is consistent
// with the current filter list configuration. A mismatch is possible when:
//   - the app was updated and default lists changed;
//   - the user modified lists in a previous session without compiling;
//   - the database file was deleted externally.
// When a mismatch is detected, isStartupDirty is set and a banner prompts
// the user to recompile.
internal fun QuiverGuardActivity.performStartupValidation() {
    val dbFile = QuiverGuardPaths.databaseFile(this)
    val manifest = CompiledManifest.read(QuiverGuardPaths.manifestFile(this))

    if (!dbFile.exists() || manifest == null) {
        showStartupBanner(getString(R.string.quiver_guard_banner_no_database))
        return
    }

    val currentLists = database().getAllFilterLists()
    // The manual filter's own entry, if any, is compared separately below via
    // isManualFilterDirty since it has no corresponding row in FilterListDatabase.
    val manifestMap = manifest.entries.filterNot { it.id == ManualFilterState.COMPILE_ID }.associateBy { it.id }

    // Compare the count and per-list enabled flags. Count mismatch means lists
    // were added or removed since the last compile; flag mismatch means lists
    // were toggled without recompiling.
    var diffFound = currentLists.size != manifestMap.size
    if (!diffFound) {
        for (fl in currentLists) {
            val entry = manifestMap[fl.id]
            if (entry == null || fl.isEnabled != entry.isEnabled) {
                diffFound = true
                break
            }
        }
    }
    if (!diffFound) diffFound = isManualFilterDirty(manifest)

    if (diffFound) {
        isStartupDirty = true
        refreshFabState()
    }
}

internal fun QuiverGuardActivity.showStartupBanner(message: String) {
    val bannerView = findViewById<View>(R.id.quiver_guard_compile_banner) ?: return
    bannerView.findViewById<TextView>(R.id.quiver_guard_compile_banner_text)?.text = message
    bannerView.visibility = View.VISIBLE
}

internal fun QuiverGuardActivity.hideBanner() {
    findViewById<View>(R.id.quiver_guard_compile_banner)?.visibility = View.GONE
}

// Drives the full compile workflow: builds inputs from the effective list state,
// shows a progress dialog with stage labels and a live rule counter, and delegates
// to QuiverGuardCompiler.compile. On success, persists the new compiled state to
// the database and manifest and activates the newly compiled engine. On failure,
// shows an error dialog with a retry option.
internal fun QuiverGuardActivity.startCompilation() {
    if (isCompileRunning) return

    val effectiveLists = effectiveFilterLists()
    val enabledAndDownloaded = effectiveLists.filter { it.isEnabled && it.isDownloaded }
    val manualFilterRules = manualFilterDb().getAllRules()
    val manualFilterContributes = ManualFilterState.isEnabled(this) && manualFilterRules.isNotEmpty()

    if (enabledAndDownloaded.isEmpty() && !manualFilterContributes) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.quiver_guard_compile_progress_title))
            .setMessage(getString(R.string.quiver_guard_banner_no_database))
            .setPositiveButton(getString(R.string.action_ok), null)
            .create().also { applyStatusBarFlagToDialog(it) }.show()
        return
    }

    isCompileRunning = true
    setInteractionsEnabled(false)
    refreshFabState()

    val inputs = enabledAndDownloaded.map { fl ->
        FilterListCompileInput(
            id = fl.id, name = fl.name,
            rulesFile = FilterListDownloader.localFileFor(applicationContext, fl.id)
        )
    } + if (manualFilterContributes) {
        // Writing the rule set to disk right before compiling, rather than keeping it
        // continuously in sync, avoids a redundant file write on every add/edit/delete in
        // ManualFilterActivity for a file only the compiler ever reads.
        ManualFilterDatabase.writeRulesFile(applicationContext, manualFilterRules)
        listOf(
            FilterListCompileInput(
                id = ManualFilterState.COMPILE_ID,
                name = getString(R.string.quiver_guard_manual_filter_title),
                rulesFile = ManualFilterDatabase.rulesFile(applicationContext)
            )
        )
    } else {
        emptyList()
    }

    val outputFile = QuiverGuardPaths.databaseFile(this)
    val tempFile = QuiverGuardPaths.tempDatabaseFile(this)

    val progressView = layoutInflater.inflate(R.layout.dialog_compile_progress, null)
    val progressBar = progressView.findViewById<LinearProgressIndicator>(R.id.compile_progress_bar)
    val tvCounter = progressView.findViewById<TextView>(R.id.compile_progress_list_counter)
    val tvStage = progressView.findViewById<TextView>(R.id.compile_progress_stage)
    val tvRules = progressView.findViewById<TextView>(R.id.compile_progress_rules)
    val tvElapsed = progressView.findViewById<TextView>(R.id.compile_progress_elapsed)

    val progressDialog = MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.quiver_guard_compile_progress_title))
        .setView(progressView)
        .setCancelable(false)
        .create()
        .also { applyStatusBarFlagToDialog(it) }
    progressDialog.show()

    val compileStartMs = System.currentTimeMillis()
    // A separate timer coroutine updates the elapsed time every 500 ms independently
    // of the progress events emitted by the compiler, so the clock ticks smoothly
    // even between long-running parsing bursts.
    var timerJob: Job? = null
    timerJob = activityScope.launch {
        while (true) {
            delay(500L)
            val elapsedSec = (System.currentTimeMillis() - compileStartMs) / 1000L
            tvElapsed?.text = getString(
                R.string.quiver_guard_compile_progress_elapsed, formatElapsedSeconds(elapsedSec)
            )
        }
    }

    activityScope.launch {
        try {
            QuiverGuardCompiler.compile(inputs, outputFile, tempFile).collect { event ->
                when (event) {
                    is CompileEvent.Progress -> {
                        val p = event.progress
                        tvCounter?.text = getString(
                            R.string.quiver_guard_compile_progress_list, p.completedLists, p.totalLists
                        )
                        tvStage?.text = compileStageLabel(p.stage, p.currentFilterListName)
                        tvRules?.text = getString(
                            R.string.quiver_guard_compile_progress_rules,
                            NumberFormat.getNumberInstance().format(p.rulesProcessed)
                        )
                    }
                    is CompileEvent.Completed -> {
                        timerJob?.cancel()
                        progressDialog.dismiss()
                        when (val r = event.result) {
                            is CompileResult.Success -> onCompileSuccess(r, inputs.size, effectiveLists, manualFilterContributes)
                            is CompileResult.Failure -> onCompileFailure(r)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            timerJob?.cancel()
            if (progressDialog.isShowing) progressDialog.dismiss()
            throw e
        } catch (e: Exception) {
            timerJob?.cancel()
            if (progressDialog.isShowing) progressDialog.dismiss()
            onCompileFailure(CompileResult.Failure(e.message ?: e.javaClass.simpleName, null, e))
        } finally {
            isCompileRunning = false
            setInteractionsEnabled(true)
            refreshFabState()
        }
    }
}

// Called after a successful compile. Flushes pending removals (deletes files and
// database rows), persists the new enabled states and compilation timestamps,
// writes the manifest, clears all pending changes, and activates the newly
// compiled engine so filtering with the new rules starts immediately.
private fun QuiverGuardActivity.onCompileSuccess(
    result: CompileResult.Success,
    compiledListCount: Int,
    effectiveLists: List<FilterList>,
    manualFilterIncluded: Boolean
) {
    val compiledAtMillis = System.currentTimeMillis()
    val survivingLists = database().getAllFilterLists().filterNot { it.id in pendingRemovedIds }

    for (id in pendingRemovedIds) {
        val localFile = FilterListDownloader.localFileFor(applicationContext, id)
        if (localFile.exists()) localFile.delete()
        database().deleteFilterList(id)
    }

    val enabledStates = survivingLists.associate { fl ->
        fl.id to (pendingEnabledOverrides[fl.id] ?: fl.isEnabled)
    }
    database().commitCompiledState(enabledStates, compiledAtMillis)

    val filterListEntries = effectiveLists.filterNot { it.id in pendingRemovedIds }.map { fl ->
        CompiledManifestEntry(
            id = fl.id,
            name = fl.name,
            downloadUrl = fl.downloadUrl,
            isCustom = fl.isCustom,
            isEnabled = enabledStates[fl.id] ?: fl.isEnabled,
            // Encodes enough information to detect content changes without re-reading the file.
            contentFingerprint = "${fl.id}:${fl.downloadedAt}:${fl.ruleCount}"
        )
    }
    // Recorded as its own manifest entry, using the same reserved id startCompilation() gave
    // it, so isManualFilterDirty can find it again next time without scanning file contents.
    val manualFilterEntries = if (manualFilterIncluded) {
        val rules = manualFilterDb().getAllRules()
        listOf(
            CompiledManifestEntry(
                id = ManualFilterState.COMPILE_ID,
                name = getString(R.string.quiver_guard_manual_filter_title),
                downloadUrl = "",
                isCustom = true,
                isEnabled = true,
                contentFingerprint = ManualFilterState.contentFingerprint(rules)
            )
        )
    } else {
        emptyList()
    }

    CompiledManifest.write(
        QuiverGuardPaths.manifestFile(this),
        CompiledManifestData(
            compiledAtMillis = compiledAtMillis,
            entries = filterListEntries + manualFilterEntries,
            totalRuleLines = result.statistics.ruleLines,
            outputFileSizeBytes = result.outputFileSizeBytes,
            durationMs = result.durationMs
        )
    )

    pendingEnabledOverrides.clear()
    pendingRemovedIds.clear()
    isStartupDirty = false
    refreshFilterListDisplay()
    refreshFabState()
    com.jhaiian.clint.quiver.engine.QuiverGuardWebIntegration.onCompileComplete(this)

    showCompileSuccessDialog(result, compiledListCount)
}

private fun QuiverGuardActivity.onCompileFailure(result: CompileResult.Failure) {
    showCompileFailureDialog(result)
}

// Populates a grid-style dialog view with per-field compile statistics.
// adblock-rust doesn't expose a duplicate-rule count or a per-rule rejection
// reason the way the old compiler did, so those rows (and the "unsupported
// rules" detail dialog that used to hang off one of them) are gone entirely
// rather than shown with fabricated data.
private fun QuiverGuardActivity.showCompileSuccessDialog(result: CompileResult.Success, listCount: Int) {
    val fmt = NumberFormat.getNumberInstance()
    val resultView = layoutInflater.inflate(R.layout.dialog_compile_result, null)
    val s = result.statistics

    fun bind(labelId: Int, valueId: Int, label: String, value: String) {
        resultView.findViewById<TextView>(labelId)?.text = label
        resultView.findViewById<TextView>(valueId)?.text = value
    }

    bind(R.id.compile_result_label_lists, R.id.compile_result_value_lists, getString(R.string.quiver_guard_compile_result_label_lists), fmt.format(listCount))
    bind(R.id.compile_result_label_rules, R.id.compile_result_value_rules, getString(R.string.quiver_guard_compile_result_label_rules), fmt.format(s.ruleLines))
    bind(R.id.compile_result_label_comments, R.id.compile_result_value_comments, getString(R.string.quiver_guard_compile_result_label_comments), fmt.format(s.commentLines))
    bind(R.id.compile_result_label_empty, R.id.compile_result_value_empty, getString(R.string.quiver_guard_compile_result_label_empty), fmt.format(s.emptyLines))
    bind(R.id.compile_result_label_size, R.id.compile_result_value_size, getString(R.string.quiver_guard_compile_result_label_size), formatFileSize(result.outputFileSizeBytes))
    bind(R.id.compile_result_label_duration, R.id.compile_result_value_duration, getString(R.string.quiver_guard_compile_result_label_duration), formatElapsedSeconds(result.durationMs / 1000L))

    resultView.findViewById<View>(R.id.compile_result_failure_detail)?.visibility = View.GONE

    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.quiver_guard_compile_success_title))
        .setView(resultView)
        .setPositiveButton(getString(R.string.action_ok), null)
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Shows an error dialog with the failed list name, error message, a note that
// the previous compiled engine is still active, and a retry button. All stat
// rows are hidden because no statistics are available on failure.
private fun QuiverGuardActivity.showCompileFailureDialog(result: CompileResult.Failure) {
    val resultView = layoutInflater.inflate(R.layout.dialog_compile_result, null)

    listOf(
        R.id.compile_result_row_lists, R.id.compile_result_row_rules,
        R.id.compile_result_row_comments, R.id.compile_result_row_empty,
        R.id.compile_result_row_size, R.id.compile_result_row_duration,
        R.id.compile_result_divider_1
    ).forEach { id -> resultView.findViewById<View>(id)?.visibility = View.GONE }

    val detail = buildString {
        result.failedFilterListName?.let {
            append(getString(R.string.quiver_guard_compile_failure_failed_list, it))
            append("\n")
        }
        append(getString(R.string.quiver_guard_compile_failure_details, result.message))
        append("\n")
        append(getString(R.string.quiver_guard_compile_failure_previous_active))
    }
    resultView.findViewById<TextView>(R.id.compile_result_failure_detail)?.let {
        it.text = detail
        it.visibility = View.VISIBLE
    }

    MaterialAlertDialogBuilder(this, getDialogTheme())
        .setTitle(getString(R.string.quiver_guard_compile_failure_title))
        .setView(resultView)
        .setNegativeButton(getString(R.string.quiver_guard_compile_action_dismiss), null)
        .setPositiveButton(getString(R.string.quiver_guard_compile_action_retry)) { _, _ ->
            startCompilation()
        }
        .create().also { applyStatusBarFlagToDialog(it) }.show()
}

// Maps a CompileStage enum to the human-readable label shown in the progress dialog.
private fun QuiverGuardActivity.compileStageLabel(stage: CompileStage, currentList: String?): String =
    when (stage) {
        CompileStage.PREPARING  -> getString(R.string.quiver_guard_compile_progress_stage_preparing)
        CompileStage.READING    -> getString(R.string.quiver_guard_compile_progress_stage_reading, currentList ?: "")
        CompileStage.PARSING    -> getString(R.string.quiver_guard_compile_progress_stage_parsing, currentList ?: "")
        CompileStage.FINALIZING -> getString(R.string.quiver_guard_compile_progress_stage_finalizing)
    }

// Formats a duration as "Nm Ns" when ≥ 60 seconds, or just "Ns" otherwise.
private fun formatElapsedSeconds(totalSeconds: Long): String {
    val m = totalSeconds / 60L
    val s = totalSeconds % 60L
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
