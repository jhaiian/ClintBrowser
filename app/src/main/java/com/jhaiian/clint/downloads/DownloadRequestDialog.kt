package com.jhaiian.clint.downloads
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link

import com.jhaiian.clint.R

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import com.jhaiian.clint.settings.common.dialogSectionBackground
import com.jhaiian.clint.settings.common.SettingsSection
import com.jhaiian.clint.settings.downloads.DownloadSettingsKeys
import com.jhaiian.clint.ui.ClintDialog
import com.jhaiian.clint.ui.ClintOutlinedTextField
import com.jhaiian.clint.ui.ClintSlider
import com.jhaiian.clint.ui.ClintSwitch
import com.jhaiian.clint.ui.listscreen.ConfirmDialogConfig
import com.jhaiian.clint.ui.listscreen.ConfirmDialogHost
import com.jhaiian.clint.ui.listscreen.PopupShape
import com.jhaiian.clint.ui.theme.LocalClintColors
import com.jhaiian.clint.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class DownloadRequestSubmission(
    val filename: String,
    val retryEnabled: Boolean,
    val unmeteredOnly: Boolean,
    val splitParts: Int,
    val multithreadingParts: Int,
    val speedLimitBytesPerSec: Long,
    val locationMode: String,
    val customLocationUri: String?,
    val categorizeEnabled: Boolean,
    val scheduledStartAtMillis: Long,
    val concurrentSegments: Int
)

@Composable
fun DownloadRequestDialog(
    hideStatusBar: Boolean, hideSystemNavigation: Boolean,
    url: String,
    onCopyLink: () -> Unit,
    initialFilename: String,
    contentLengthBytes: Long,
    fileSizeDisplayOverride: String? = null,
    fetchUrl: String? = null,
    fetchUserAgent: String = "",
    checkStorage: Boolean,
    showOptions: Boolean,
    showSchedule: Boolean = showOptions,
    showStorageInfo: Boolean = showOptions,
    showSplitAndMultithreading: Boolean = showOptions,
    showConcurrentSegments: Boolean = false,
    initialConcurrentSegments: Int = 6,
    initialLocationMode: String,
    initialCustomUri: Uri?,
    initialCategorizeEnabled: Boolean? = null,
    initialRetryEnabled: Boolean = false,
    initialUnmeteredOnly: Boolean = false,
    initialSplitParts: Int = 1,
    initialMultithreadingParts: Int = 1,
    initialSpeedLimitAmount: Int = 0,
    initialSpeedLimitUnit: String = DEFAULT_SPEED_LIMIT_UNIT,
    fragmentManager: FragmentManager,
    onLaunchFolderPicker: (onPicked: (Uri) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (DownloadRequestSubmission, onDismiss: () -> Unit, onRename: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalClintColors.current
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val filenameFocusRequester = remember { FocusRequester() }

    val dot = initialFilename.lastIndexOf('.')
    var filename by remember { mutableStateOf(if (dot > 0) initialFilename.substring(0, dot) else initialFilename) }
    var extension by remember { mutableStateOf(if (dot > 0) initialFilename.substring(dot + 1) else "") }

    var effectiveContentLength by remember { mutableStateOf(contentLengthBytes) }
    var fileSizeText by remember {
        mutableStateOf(
            fileSizeDisplayOverride
                ?: if (fetchUrl != null) context.getString(R.string.download_dialog_file_size_fetching)
                else if (contentLengthBytes > 0L) context.getString(R.string.download_dialog_file_size_value, formatFileSize(contentLengthBytes))
                else context.getString(R.string.download_dialog_file_size_unknown)
        )
    }

    LaunchedEffect(fetchUrl) {
        if (fetchUrl == null) return@LaunchedEffect
        val ua = fetchUserAgent.ifEmpty { "Mozilla/5.0" }
        val result = withContext(Dispatchers.IO) {
            tryHeadForSize(fetchUrl, ua).takeIf { it > 0L } ?: tryRangeGetForSize(fetchUrl, ua)
        }
        effectiveContentLength = result
        fileSizeText = if (result > 0L) context.getString(R.string.download_dialog_file_size_value, formatFileSize(result))
            else context.getString(R.string.download_dialog_file_size_unknown)
    }

    var locationMode by remember { mutableStateOf(initialLocationMode) }
    var customUri by remember { mutableStateOf(initialCustomUri) }
    var locationMenuOpen by remember { mutableStateOf(false) }
    val storageInfoText = remember(locationMode, customUri) {
        if (showStorageInfo) resolveStorageInfoText(context, locationMode, customUri) else ""
    }
    var categorizeEnabled by remember { mutableStateOf(initialCategorizeEnabled ?: DownloadCategories.isEnabled(context)) }
    val destinationPreviewText = remember(locationMode, customUri, filename, extension, categorizeEnabled) {
        if (!categorizeEnabled) return@remember null
        val resolvedFilename = if (extension.isNotBlank()) "$filename.$extension" else filename
        val basePath = if (locationMode == DownloadSettingsKeys.MODE_CUSTOM) customUri?.let { uriToDisplayPath(it) } else DEFAULT_DOWNLOAD_PATH
        basePath?.let { it.trimEnd('/') + "/" + DownloadCategories.categorySubpathDisplay(categorizeEnabled, resolvedFilename) + resolvedFilename }
    }

    var retryEnabled by remember { mutableStateOf(initialRetryEnabled) }
    var unmeteredOnly by remember { mutableStateOf(initialUnmeteredOnly) }
    var splitParts by remember { mutableStateOf(initialSplitParts.coerceIn(1, 32)) }
    var multithreadingParts by remember { mutableStateOf(initialMultithreadingParts.coerceIn(1, 8)) }
    var concurrentSegments by remember { mutableStateOf(initialConcurrentSegments.coerceIn(1, 8)) }

    var speedLimitText by remember { mutableStateOf(if (initialSpeedLimitAmount > 0) initialSpeedLimitAmount.toString() else "") }
    val kbLabel = stringResource(R.string.speed_limit_unit_kb)
    val mbLabel = stringResource(R.string.speed_limit_unit_mb)
    var speedUnitLabel by remember { mutableStateOf(if (initialSpeedLimitUnit == SPEED_LIMIT_UNIT_MB) mbLabel else kbLabel) }
    var speedUnitMenuOpen by remember { mutableStateOf(false) }

    var scheduleEnabled by remember { mutableStateOf(false) }
    var scheduledMillis by remember { mutableStateOf(0L) }
    var blockingError by remember { mutableStateOf<ConfirmDialogConfig?>(null) }

    fun pickCustomFolder() {
        onLaunchFolderPicker { uri ->
            prefs.edit().putString(DownloadSettingsKeys.PREF_DOWNLOAD_CUSTOM_URI, uri.toString()).apply()
            customUri = uri
        }
    }

    ClintDialog(
        title = stringResource(R.string.download_dialog_title),
        hideStatusBar = hideStatusBar, hideSystemNavigation = hideSystemNavigation,
        onDismiss = onDismiss,
        footer = {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), color = colors.primary, fontWeight = FontWeight.Medium)
                }
                TextButton(
                    onClick = {
                        val resolvedFilename = if (extension.isNotBlank()) "$filename.$extension" else filename
                        if (checkStorage) {
                            val storageError = checkStorageAvailable(context, effectiveContentLength, locationMode, customUri)
                            if (storageError != null) {
                                blockingError = ConfirmDialogConfig(
                                    title = context.getString(R.string.download_error_storage_title),
                                    message = storageError,
                                    positiveLabel = context.getString(R.string.action_ok)
                                )
                                return@TextButton
                            }
                        }
                        val fat32Error = checkFat32FileSizeLimit(context, effectiveContentLength, locationMode, customUri)
                        if (fat32Error != null) {
                            blockingError = ConfirmDialogConfig(
                                title = context.getString(R.string.download_error_fat32_title),
                                message = fat32Error,
                                positiveLabel = context.getString(R.string.action_ok)
                            )
                            return@TextButton
                        }
                        val effectiveScheduledMillis = if (showSchedule && scheduleEnabled) scheduledMillis else 0L
                        if (effectiveScheduledMillis > 0L && effectiveScheduledMillis <= System.currentTimeMillis()) {
                            blockingError = ConfirmDialogConfig(
                                title = context.getString(R.string.download_schedule_invalid_title),
                                message = context.getString(R.string.download_schedule_past_time_error),
                                positiveLabel = context.getString(R.string.action_ok)
                            )
                            return@TextButton
                        }
                        val speedAmount = speedLimitText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        val speedUnit = if (speedUnitLabel == mbLabel) SPEED_LIMIT_UNIT_MB else SPEED_LIMIT_UNIT_KB
                        val speedLimitBytesPerSec = resolveSpeedLimitBytesPerSec(context, speedAmount, speedUnit)
                        val submission = DownloadRequestSubmission(
                            filename = resolvedFilename,
                            retryEnabled = retryEnabled,
                            unmeteredOnly = unmeteredOnly,
                            splitParts = splitParts,
                            multithreadingParts = multithreadingParts,
                            speedLimitBytesPerSec = speedLimitBytesPerSec,
                            locationMode = locationMode,
                            customLocationUri = customUri?.toString(),
                            categorizeEnabled = categorizeEnabled,
                            scheduledStartAtMillis = effectiveScheduledMillis,
                            concurrentSegments = concurrentSegments
                        )
                        onSubmit(submission, onDismiss) {
                            filenameFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_download), color = colors.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            DialogSectionLabel(stringResource(R.string.download_dialog_section_link))
            SettingsSection(colors.dialogSectionBackground) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Link,
                        contentDescription = stringResource(R.string.download_dialog_link_clip_label),
                        tint = colors.iconTint,
                        modifier = Modifier.size(18.dp).clickable { onCopyLink() }.padding(2.dp)
                    )
                    Text(
                        url, color = colors.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 10.dp)
                    )
                }
            }

            DialogSectionLabel(stringResource(R.string.download_dialog_section_file))
            SettingsSection(colors.dialogSectionBackground) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        ClintOutlinedTextField(
                            value = filename, onValueChange = { filename = it },
                            modifier = Modifier.weight(1f).focusRequester(filenameFocusRequester),
                            label = { Text(stringResource(R.string.download_dialog_filename_hint)) }, singleLine = true
                        )
                        ClintOutlinedTextField(
                            value = extension, onValueChange = { extension = it },
                            modifier = Modifier.width(96.dp).padding(start = 8.dp),
                            label = { Text(stringResource(R.string.download_dialog_extension_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true
                        )
                    }
                    Text(fileSizeText, color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            DialogSectionLabel(stringResource(R.string.download_dialog_section_location))
            SettingsSection(colors.dialogSectionBackground) {
                Column(Modifier.padding(16.dp)) {
                    var locationAnchorWidthPx by remember { mutableStateOf(0) }
                    val density = LocalDensity.current
                    Box(Modifier.onGloballyPositioned { coordinates -> locationAnchorWidthPx = coordinates.size.width }) {
                        Row(
                            Modifier.fillMaxWidth().clickable { locationMenuOpen = true }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.download_location_label), color = colors.secondaryText, fontSize = 11.sp)
                                Text(
                                    if (locationMode == DownloadSettingsKeys.MODE_CUSTOM) stringResource(R.string.download_location_option_custom) else stringResource(R.string.download_location_option_default),
                                    color = colors.onSurface, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Icon(androidx.compose.material.icons.Icons.Filled.ArrowDownward, contentDescription = null, tint = colors.iconTint)
                        }
                        DropdownMenu(
                            expanded = locationMenuOpen, onDismissRequest = { locationMenuOpen = false },
                            modifier = Modifier.width(with(density) { locationAnchorWidthPx.toDp() }),
                            shape = PopupShape, containerColor = colors.popupBackground, border = BorderStroke(1.dp, colors.popupStroke)
                        ) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.download_location_option_default), color = colors.onSurface) }, onClick = {
                                locationMode = DownloadSettingsKeys.MODE_DEFAULT; locationMenuOpen = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.download_location_option_custom), color = colors.onSurface) }, onClick = {
                                locationMode = DownloadSettingsKeys.MODE_CUSTOM; locationMenuOpen = false
                                pickCustomFolder()
                            })
                        }
                    }
                    if (locationMode == DownloadSettingsKeys.MODE_CUSTOM) {
                        Row(
                            Modifier.fillMaxWidth().clickable { pickCustomFolder() }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Folder, contentDescription = null, tint = colors.iconTint, modifier = Modifier.size(20.dp))
                            Text(
                                customUri?.let { uriToDisplayPath(it) } ?: stringResource(R.string.download_location_tap_to_choose),
                                color = colors.onSurface, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                    if (showStorageInfo && storageInfoText.isNotEmpty()) {
                        Text(storageInfoText, color = colors.secondaryText, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            categorizeEnabled = !categorizeEnabled
                        }.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.download_categorize_title), color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.download_categorize_summary), color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        ClintSwitch(checked = categorizeEnabled)
                    }
                    destinationPreviewText?.let {
                        Text(
                            it, color = colors.secondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            if (showOptions) {
                DialogSectionLabel(stringResource(R.string.download_dialog_section_options))
                SettingsSection(colors.dialogSectionBackground) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { retryEnabled = !retryEnabled }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.download_retry_enabled_title), color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(stringResource(R.string.download_retry_enabled_summary), color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            ClintSwitch(checked = retryEnabled)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { unmeteredOnly = !unmeteredOnly }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.download_unmetered_only_title), color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(stringResource(R.string.download_dialog_unmetered_summary), color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            ClintSwitch(checked = unmeteredOnly)
                        }

                        if (showSplitAndMultithreading) {
                            Text(
                                stringResource(R.string.download_split_parts_title), color = colors.onSurface,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp)
                            )
                            Text(
                                pluralStringResource(R.plurals.download_split_parts_value, splitParts, splitParts),
                                color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                            )
                            ClintSlider(
                                value = splitParts.toFloat(),
                                onValueChange = { splitParts = it.toInt() },
                                valueRange = 1f..32f, steps = 30
                            )

                            Text(
                                stringResource(R.string.download_multithreading_title), color = colors.onSurface,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                pluralStringResource(R.plurals.download_multithreading_value, multithreadingParts, multithreadingParts),
                                color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                            )
                            ClintSlider(
                                value = multithreadingParts.toFloat(),
                                onValueChange = { multithreadingParts = it.toInt() },
                                valueRange = 1f..8f, steps = 6
                            )
                        }

                        if (showConcurrentSegments) {
                            Text(
                                stringResource(R.string.download_concurrent_segments_title), color = colors.onSurface,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp)
                            )
                            Text(
                                pluralStringResource(R.plurals.download_concurrent_segments_value, concurrentSegments, concurrentSegments),
                                color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                            )
                            ClintSlider(
                                value = concurrentSegments.toFloat(),
                                onValueChange = { concurrentSegments = it.toInt() },
                                valueRange = 1f..8f, steps = 6
                            )
                        }

                        Text(
                            stringResource(R.string.download_dialog_speed_limit_title), color = colors.onSurface,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            stringResource(R.string.download_dialog_speed_limit_desc), color = colors.secondaryText,
                            fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ClintOutlinedTextField(
                                value = speedLimitText,
                                onValueChange = { speedLimitText = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.download_dialog_speed_limit_hint)) },
                                singleLine = true
                            )
                            Box(Modifier.padding(start = 8.dp)) {
                                Row(
                                    Modifier.clickable { speedUnitMenuOpen = true }.padding(horizontal = 12.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(speedUnitLabel, color = colors.onSurface, fontSize = 14.sp)
                                    Icon(androidx.compose.material.icons.Icons.Filled.ArrowDownward, contentDescription = null, tint = colors.iconTint, modifier = Modifier.padding(start = 2.dp))
                                }
                                DropdownMenu(
                                    expanded = speedUnitMenuOpen, onDismissRequest = { speedUnitMenuOpen = false },
                                    shape = PopupShape, containerColor = colors.popupBackground, border = BorderStroke(1.dp, colors.popupStroke),
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    DropdownMenuItem(text = { Text(kbLabel, color = colors.onSurface) }, onClick = { speedUnitLabel = kbLabel; speedUnitMenuOpen = false })
                                    DropdownMenuItem(text = { Text(mbLabel, color = colors.onSurface) }, onClick = { speedUnitLabel = mbLabel; speedUnitMenuOpen = false })
                                }
                            }
                        }

                        if (showSchedule) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    scheduleEnabled = !scheduleEnabled
                                    if (scheduleEnabled) {
                                        scheduledMillis = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }.timeInMillis
                                        if (needsExactAlarmPermissionRationale(context)) blockingError = exactAlarmPermissionDialogConfig(context)
                                    }
                                }.padding(vertical = 10.dp).padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.download_schedule_this_title), color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(stringResource(R.string.download_schedule_this_summary), color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                ClintSwitch(checked = scheduleEnabled)
                            }
                            if (scheduleEnabled) {
                                val dateFmt = remember { android.text.format.DateFormat.getMediumDateFormat(context) }
                                val timeFmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        showScheduleDatePicker(fragmentManager, scheduledMillis) { year, month, day ->
                                            scheduledMillis = Calendar.getInstance().apply {
                                                timeInMillis = scheduledMillis
                                                set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                                            }.timeInMillis
                                        }
                                    }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.download_schedule_date_title), color = colors.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Text(dateFmt.format(java.util.Date(scheduledMillis)), color = colors.secondaryText, fontSize = 13.sp)
                                }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        showScheduleTimePicker(context, fragmentManager, scheduledMillis) { hour, minute ->
                                            scheduledMillis = Calendar.getInstance().apply {
                                                timeInMillis = scheduledMillis
                                                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
                                            }.timeInMillis
                                        }
                                    }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.download_schedule_time_title), color = colors.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Text(timeFmt.format(java.util.Date(scheduledMillis)), color = colors.secondaryText, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ConfirmDialogHost(blockingError, hideStatusBar, hideSystemNavigation) { blockingError = null }
}

@Composable
private fun DialogSectionLabel(text: String) {
    val colors = LocalClintColors.current
    Text(text, color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

private fun tryHeadForSize(url: String, userAgent: String): Long {
    return try {
        val request = okhttp3.Request.Builder().url(url).head().header("User-Agent", userAgent).build()
        val response = ClintDownloadManager.httpClient.newCall(request).execute()
        val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
        response.close()
        len
    } catch (_: Throwable) { -1L }
}

private fun tryRangeGetForSize(url: String, userAgent: String): Long {
    return try {
        val request = okhttp3.Request.Builder().url(url).get().header("User-Agent", userAgent).header("Range", "bytes=0-0").build()
        val response = ClintDownloadManager.httpClient.newCall(request).execute()
        val total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
            ?: response.header("Content-Length")?.toLongOrNull() ?: -1L
        response.close()
        total
    } catch (_: Throwable) { -1L }
}
