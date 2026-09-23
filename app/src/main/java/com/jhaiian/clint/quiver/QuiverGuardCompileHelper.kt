package com.jhaiian.clint.quiver

import com.jhaiian.clint.R
import com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig
import com.jhaiian.clint.quiver.engine.CompileEvent
import com.jhaiian.clint.quiver.engine.CompileResult
import com.jhaiian.clint.quiver.engine.CompileStage
import com.jhaiian.clint.quiver.engine.CompiledManifest
import com.jhaiian.clint.quiver.engine.CompiledManifestData
import com.jhaiian.clint.quiver.engine.CompiledManifestEntry
import com.jhaiian.clint.quiver.engine.FilterListCompileInput
import com.jhaiian.clint.quiver.engine.QuiverGuardCompiler
import com.jhaiian.clint.quiver.engine.QuiverGuardPaths
import com.jhaiian.clint.quiver.engine.QuiverGuardWebIntegration
import com.jhaiian.clint.util.formatFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat

internal fun QuiverGuardActivity.showSetupGuideDialog() {
    uiState.confirmDialog = ConfirmDialogConfig(
        title = getString(R.string.quiver_guard_no_active_lists_title),
        message = getString(R.string.quiver_guard_no_active_lists_message),
        positiveLabel = getString(R.string.action_ok)
    )
}

internal fun QuiverGuardActivity.handleBackNavigation() {
    if (uiState.isCompileRunning) return
    if (!isConfigurationDirty()) {
        finish()
        return
    }
    uiState.confirmDialog = ConfirmDialogConfig(
        title = getString(R.string.quiver_guard_back_dialog_title),
        message = getString(R.string.quiver_guard_back_dialog_message),
        neutralLabel = getString(R.string.action_cancel),
        negativeLabel = getString(R.string.quiver_guard_back_dialog_discard),
        onNegative = { discardPendingChanges(); finish() },
        positiveLabel = getString(R.string.quiver_guard_back_dialog_compile),
        onPositive = { startCompilation() }
    )
}

internal fun QuiverGuardActivity.performStartupValidation() {
    val dbFile = QuiverGuardPaths.databaseFile(this)
    val manifest = CompiledManifest.read(QuiverGuardPaths.manifestFile(this))

    if (!dbFile.exists() || manifest == null) {
        uiState.bannerText = getString(R.string.quiver_guard_banner_no_database)
        return
    }

    val currentLists = database().getAllFilterLists()

    val manifestMap = manifest.entries.filterNot { it.id == ManualFilterState.COMPILE_ID }.associateBy { it.id }

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
        uiState.isStartupDirty = true
    }
}

internal fun QuiverGuardActivity.startCompilation() {
    if (uiState.isCompileRunning) return

    val effectiveLists = effectiveFilterLists()
    val enabledAndDownloaded = effectiveLists.filter { it.isEnabled && it.isDownloaded }
    val manualFilterRules = manualFilterDb().getAllRules()
    val manualFilterContributes = ManualFilterState.isEnabled(this) && manualFilterRules.isNotEmpty()

    if (enabledAndDownloaded.isEmpty() && !manualFilterContributes) {
        uiState.confirmDialog = ConfirmDialogConfig(
            title = getString(R.string.quiver_guard_compile_progress_title),
            message = getString(R.string.quiver_guard_banner_no_database),
            positiveLabel = getString(R.string.action_ok)
        )
        return
    }

    uiState.isCompileRunning = true

    val inputs = enabledAndDownloaded.map { fl ->
        FilterListCompileInput(
            id = fl.id, name = fl.name,
            rulesFile = FilterListDownloader.localFileFor(applicationContext, fl.id)
        )
    } + if (manualFilterContributes) {

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

    uiState.compileProgress = CompileProgressUi(
        stageText = "", listCounterText = "", rulesText = "",
        elapsedText = getString(R.string.quiver_guard_compile_progress_elapsed, "0s")
    )

    val compileStartMs = System.currentTimeMillis()

    var timerJob: Job? = null
    timerJob = activityScope.launch {
        while (true) {
            delay(500L)
            val elapsedSec = (System.currentTimeMillis() - compileStartMs) / 1000L
            uiState.compileProgress = uiState.compileProgress?.copy(
                elapsedText = getString(R.string.quiver_guard_compile_progress_elapsed, formatElapsedSeconds(elapsedSec))
            )
        }
    }

    activityScope.launch {
        try {
            QuiverGuardCompiler.compile(inputs, outputFile, tempFile).collect { event ->
                when (event) {
                    is CompileEvent.Progress -> {
                        val p = event.progress
                        uiState.compileProgress = uiState.compileProgress?.copy(
                            listCounterText = getString(R.string.quiver_guard_compile_progress_list, p.completedLists, p.totalLists),
                            stageText = compileStageLabel(p.stage, p.currentFilterListName),
                            rulesText = getString(R.string.quiver_guard_compile_progress_rules, NumberFormat.getNumberInstance().format(p.rulesProcessed))
                        )
                    }
                    is CompileEvent.Completed -> {
                        timerJob.cancel()
                        uiState.compileProgress = null
                        when (val r = event.result) {
                            is CompileResult.Success -> onCompileSuccess(r, inputs.size, effectiveLists, manualFilterContributes)
                            is CompileResult.Failure -> onCompileFailure(r)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            timerJob.cancel()
            uiState.compileProgress = null
            throw e
        } catch (e: Exception) {
            timerJob.cancel()
            uiState.compileProgress = null
            onCompileFailure(CompileResult.Failure(e.message ?: e.javaClass.simpleName, null, e))
        } finally {
            uiState.isCompileRunning = false
        }
    }
}

private fun QuiverGuardActivity.onCompileSuccess(
    result: CompileResult.Success,
    compiledListCount: Int,
    effectiveLists: List<FilterList>,
    manualFilterIncluded: Boolean
) {
    val compiledAtMillis = System.currentTimeMillis()
    val survivingLists = database().getAllFilterLists().filterNot { it.id in uiState.pendingRemovedIds }

    for (id in uiState.pendingRemovedIds) {
        val localFile = FilterListDownloader.localFileFor(applicationContext, id)
        if (localFile.exists()) localFile.delete()
        database().deleteFilterList(id)
    }

    val enabledStates = survivingLists.associate { fl ->
        fl.id to (uiState.pendingEnabledOverrides[fl.id] ?: fl.isEnabled)
    }
    database().commitCompiledState(enabledStates, compiledAtMillis)

    val filterListEntries = effectiveLists.filterNot { it.id in uiState.pendingRemovedIds }.map { fl ->
        CompiledManifestEntry(
            id = fl.id,
            name = fl.name,
            downloadUrl = fl.downloadUrl,
            isCustom = fl.isCustom,
            isEnabled = enabledStates[fl.id] ?: fl.isEnabled,

            contentFingerprint = "${fl.id}:${fl.downloadedAt}:${fl.ruleCount}"
        )
    }

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

    uiState.pendingEnabledOverrides = emptyMap()
    uiState.pendingRemovedIds = emptySet()
    uiState.isStartupDirty = false
    uiState.bannerText = null
    refreshFilterListDisplay()
    QuiverGuardWebIntegration.onCompileComplete(this)

    showCompileSuccessDialog(result, compiledListCount)
}

private fun QuiverGuardActivity.onCompileFailure(result: CompileResult.Failure) {
    showCompileFailureDialog(result)
}

private fun QuiverGuardActivity.showCompileSuccessDialog(result: CompileResult.Success, listCount: Int) {
    val fmt = NumberFormat.getNumberInstance()
    val s = result.statistics
    uiState.compileResult = CompileResultUi(
        isSuccess = true,
        title = getString(R.string.quiver_guard_compile_success_title),
        rows = listOf(
            CompileResultRow(getString(R.string.quiver_guard_compile_result_label_lists), fmt.format(listCount)),
            CompileResultRow(getString(R.string.quiver_guard_compile_result_label_rules), fmt.format(s.ruleLines)),
            CompileResultRow(getString(R.string.quiver_guard_compile_result_label_comments), fmt.format(s.commentLines)),
            CompileResultRow(getString(R.string.quiver_guard_compile_result_label_empty), fmt.format(s.emptyLines)),
            CompileResultRow(getString(R.string.quiver_guard_compile_result_label_size), formatFileSize(result.outputFileSizeBytes)),
            CompileResultRow(getString(R.string.quiver_guard_compile_result_label_duration), formatElapsedSeconds(result.durationMs / 1000L))
        )
    )
}

private fun QuiverGuardActivity.showCompileFailureDialog(result: CompileResult.Failure) {
    val detail = buildString {
        result.failedFilterListName?.let {
            append(getString(R.string.quiver_guard_compile_failure_failed_list, it))
            append("\n")
        }
        append(getString(R.string.quiver_guard_compile_failure_details, result.message))
        append("\n")
        append(getString(R.string.quiver_guard_compile_failure_previous_active))
    }
    uiState.compileResult = CompileResultUi(
        isSuccess = false,
        title = getString(R.string.quiver_guard_compile_failure_title),
        rows = emptyList(),
        failureDetail = detail,
        onRetry = { startCompilation() }
    )
}

private fun QuiverGuardActivity.compileStageLabel(stage: CompileStage, currentList: String?): String =
    when (stage) {
        CompileStage.PREPARING  -> getString(R.string.quiver_guard_compile_progress_stage_preparing)
        CompileStage.READING    -> getString(R.string.quiver_guard_compile_progress_stage_reading, currentList ?: "")
        CompileStage.PARSING    -> getString(R.string.quiver_guard_compile_progress_stage_parsing, currentList ?: "")
        CompileStage.FINALIZING -> getString(R.string.quiver_guard_compile_progress_stage_finalizing)
    }

internal fun formatElapsedSeconds(totalSeconds: Long): String {
    val m = totalSeconds / 60L
    val s = totalSeconds % 60L
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
