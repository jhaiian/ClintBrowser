package com.jhaiian.clint.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.util.formatFileSize
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object UpdateChecker {

    private const val STABLE_URL =
        "https://raw.githubusercontent.com/jhaiian/ClintBrowser/main/Update/Stable.json"
    private const val BETA_URL =
        "https://raw.githubusercontent.com/jhaiian/ClintBrowser/main/Update/Beta.json"

    private const val PREFS_NAME = "update_prefs"
    private const val KEY_SKIPPED_VERSION_CODE = "skipped_version_code"
    private const val KEY_CACHED_APK_VERSION_CODE = "cached_apk_version_code"
    private const val ARCH_UNIVERSAL = "universal"

    // Minimum interval between progress UI updates while downloading, so we
    // don't hop to the main thread on every single buffer read.
    private const val PROGRESS_UI_THROTTLE_BYTES = 65536L
    private const val SPEED_SAMPLE_INTERVAL_MS = 400L

    private val client = OkHttpClient()

    private fun getDialogTheme(context: Context): Int {
        return if (context is ClintActivity) context.getDialogTheme()
        else R.style.ThemeOverlay_ClintBrowser_Dialog
    }

    private fun resolveColor(context: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    // Ties background work to the activity's own lifecycle when possible (so it is
    // cancelled automatically if the activity goes away), falling back to a
    // standalone main-dispatcher scope for the rare case the activity isn't a
    // LifecycleOwner.
    private fun scopeFor(activity: Activity): CoroutineScope =
        (activity as? LifecycleOwner)?.lifecycleScope ?: CoroutineScope(Dispatchers.Main.immediate)

    fun check(activity: Activity, isBeta: Boolean, silent: Boolean) {
        scopeFor(activity).launch {
            try {
                val (json, isSelectedBeta) = withContext(Dispatchers.IO) {
                    val stableJson = fetchJson(STABLE_URL)
                    val betaJson = if (isBeta) fetchJson(BETA_URL) else null
                    when {
                        betaJson == null -> Pair(stableJson, false)
                        betaJson.getLong("versionCode") > stableJson.getLong("versionCode") ->
                            Pair(betaJson, true)
                        else -> Pair(stableJson, false)
                    }
                }

                val remoteVersion = json.getString("version")
                val remoteVersionCode = json.getLong("versionCode")
                val changelog = json.getString("changelog")
                val downloads = json.getJSONObject("downloads")

                val (installedArch, isUniversalInstall) = withContext(Dispatchers.IO) {
                    getInstalledAppArch(activity)
                }
                val downloadUrl = resolveDownloadUrl(downloads, installedArch, isUniversalInstall)

                val currentVersionCode = PackageInfoCompat.getLongVersionCode(
                    activity.packageManager.getPackageInfo(activity.packageName, 0)
                )

                val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val skippedVersionCode = prefs.getLong(KEY_SKIPPED_VERSION_CODE, -1L)

                withContext(Dispatchers.IO) { cleanStaleApk(activity, currentVersionCode) }

                val hasUpdate = remoteVersionCode > currentVersionCode
                val isSkipped = silent && remoteVersionCode == skippedVersionCode

                if (hasUpdate && !isSkipped) {
                    showUpdateDialog(activity, remoteVersion, remoteVersionCode, changelog, downloadUrl, isSelectedBeta)
                } else if (!silent) {
                    showNoUpdateDialog(activity)
                }
            } catch (_: Throwable) {
                if (!silent) showErrorDialog(activity)
            }
        }
    }

    private fun fetchJson(url: String): JSONObject {
        val request = Request.Builder().url(url).build()
        val body = client.newCall(request).execute().body?.string()
            ?: throw Exception("Empty response from $url")
        return JSONObject(body)
    }

    // Picks the download URL matching the architecture actually installed on this
    // device. A universal (fat) install always prefers the universal build back, so
    // an update never silently swaps a user from a universal install onto a slimmer
    // arch-specific one (or vice versa) — falling back to the other kind only if the
    // preferred one isn't published.
    private fun resolveDownloadUrl(downloads: JSONObject, arch: String, isUniversal: Boolean): String? {
        return if (isUniversal) {
            downloads.optString(ARCH_UNIVERSAL).takeIf { it.isNotEmpty() }
                ?: downloads.optString(arch).takeIf { it.isNotEmpty() }
        } else {
            downloads.optString(arch).takeIf { it.isNotEmpty() }
                ?: downloads.optString(ARCH_UNIVERSAL).takeIf { it.isNotEmpty() }
        }
    }

    // Determines which ABI(s) are actually packaged in the currently *installed*
    // APK(s), rather than which ABIs the device supports. This matters now that
    // AdBlock's engine is bundled as native (Rust/NDK) code: a device can support
    // multiple ABIs while the installed build only ships one of them (an
    // arch-specific split), or ships all of them together (a universal/fat APK).
    // Basing the update choice on the installed build's own contents ensures a
    // universal install is offered a universal update (and an arch-specific install
    // stays on that same arch) rather than picking whatever the device prefers.
    private fun getInstalledAppArch(context: Context): Pair<String, Boolean> {
        val appInfo = context.applicationInfo
        val apkPaths = mutableListOf(appInfo.sourceDir)
        appInfo.splitSourceDirs?.let { apkPaths.addAll(it) }

        val foundAbis = mutableSetOf<String>()
        for (path in apkPaths) {
            if (path.isNullOrEmpty()) continue
            try {
                ZipFile(path).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement().name
                        if (name.startsWith("lib/") && name.endsWith(".so")) {
                            val abi = name.removePrefix("lib/").substringBefore('/')
                            if (abi.isNotEmpty()) foundAbis.add(abi)
                        }
                    }
                }
            } catch (_: Exception) {
                // This APK part couldn't be read; other paths may still yield an answer.
            }
        }

        // More than one ABI folder packaged together means this install is a
        // universal/fat build rather than a single-arch split.
        val isUniversal = foundAbis.size > 1
        val arch = when {
            isUniversal || foundAbis.isEmpty() -> ARCH_UNIVERSAL
            foundAbis.contains("arm64-v8a") -> "arm64-v8a"
            foundAbis.contains("armeabi-v7a") -> "armeabi-v7a"
            foundAbis.contains("x86_64") -> "x86_64"
            foundAbis.contains("x86") -> "x86"
            else -> ARCH_UNIVERSAL
        }
        return Pair(arch, isUniversal)
    }

    private fun cleanStaleApk(activity: Activity, currentVersionCode: Long) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedVersionCode = prefs.getLong(KEY_CACHED_APK_VERSION_CODE, -1L)
        if (cachedVersionCode <= currentVersionCode) {
            val apkFile = File(activity.cacheDir, "updates/update.apk")
            if (apkFile.exists()) apkFile.delete()
            prefs.edit().remove(KEY_CACHED_APK_VERSION_CODE).apply()
        }
    }

    private fun showUpdateDialog(
        activity: Activity,
        version: String,
        versionCode: Long,
        rawChangelog: String,
        downloadUrl: String?,
        isBeta: Boolean
    ) {
        val channelLabel = if (isBeta) " (Beta)" else ""
        val changelog = rawChangelog.trim()
        val markwon = Markwon.create(activity)
        val dialogTheme = getDialogTheme(activity)

        val dp = activity.resources.displayMetrics.density

        val colorOnSurface = resolveColor(activity, com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceMedium = run {
            val alpha = ((colorOnSurface ushr 24) * 0.6).toInt()
            (colorOnSurface and 0x00FFFFFF) or (alpha shl 24)
        }
        val colorPrimary = resolveColor(activity, androidx.appcompat.R.attr.colorPrimary)
        val dividerColor = resolveColor(activity, R.attr.clintDividerColor)

        val changelogTv = TextView(activity).apply {
            setPadding(64, 24, 64, 8)
            setTextColor(colorOnSurface)
            textSize = 13f
        }
        markwon.setMarkdown(changelogTv, changelog)

        val divider = android.view.View(activity).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.topMargin = (8 * dp).toInt() }
        }

        fun makeBtn(label: String, color: Int) = TextView(activity).apply {
            text = label
            setTextColor(color)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            background = android.util.TypedValue().let { tv2 ->
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv2, true)
                androidx.core.content.ContextCompat.getDrawable(activity, tv2.resourceId)
            }
        }

        val btnSkip = makeBtn(activity.getString(R.string.update_dialog_skip), colorOnSurfaceMedium)
        val btnLater = makeBtn(activity.getString(R.string.action_later), colorPrimary)

        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            val hPad = (8 * dp).toInt()
            val vPad = (4 * dp).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            addView(btnSkip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnLater)
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val scrollView = ScrollView(activity).apply {
                addView(changelogTv)
            }
            addView(scrollView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(divider)
            addView(buttonRow)
        }

        val dialog = MaterialAlertDialogBuilder(activity, dialogTheme)
            .setTitle(activity.getString(R.string.update_dialog_title, version, channelLabel))
            .setView(container)
            .setCancelable(false)
            .create()

        btnSkip.setOnClickListener {
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_SKIPPED_VERSION_CODE, versionCode).apply()
            dialog.dismiss()
        }
        btnLater.setOnClickListener { dialog.dismiss() }

        val btnAction = makeBtn(
            if (!downloadUrl.isNullOrEmpty()) activity.getString(R.string.update_dialog_download)
            else activity.getString(R.string.update_dialog_view_github),
            colorPrimary
        )
        buttonRow.addView(btnAction)
        btnAction.setOnClickListener {
            dialog.dismiss()
            if (!downloadUrl.isNullOrEmpty()) {
                startDownload(activity, downloadUrl, versionCode)
            } else {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/jhaiian/ClintBrowser/releases"))
                )
            }
        }

        (activity as? ClintActivity)?.applyStatusBarFlagToDialog(dialog)
        dialog.show()
    }

    private fun startDownload(activity: Activity, downloadUrl: String, remoteVersionCode: Long) {
        val apkFile = File(activity.cacheDir, "updates/update.apk").also {
            it.parentFile?.mkdirs()
        }

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedVersionCode = prefs.getLong(KEY_CACHED_APK_VERSION_CODE, -1L)

        if (apkFile.exists() && apkFile.length() > 0 && cachedVersionCode == remoteVersionCode) {
            installApk(activity, apkFile)
            return
        }

        apkFile.delete()

        val dialogTheme = getDialogTheme(activity)
        val colorOnSurface = resolveColor(activity, com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceMedium = run {
            val alpha = ((colorOnSurface ushr 24) * 0.6).toInt()
            (colorOnSurface and 0x00FFFFFF) or (alpha shl 24)
        }
        val colorPrimary = resolveColor(activity, androidx.appcompat.R.attr.colorPrimary)

        val statusText = TextView(activity).apply {
            text = activity.getString(R.string.update_download_preparing)
            setTextColor(colorOnSurface)
            textSize = 14f
        }

        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
            progressTintList = ColorStateList.valueOf(colorPrimary)
            indeterminateTintList = ColorStateList.valueOf(colorPrimary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { lp -> lp.topMargin = 32; lp.bottomMargin = 8 }
        }

        val sizeText = TextView(activity).apply {
            text = ""
            setTextColor(colorOnSurfaceMedium)
            textSize = 12f
            gravity = Gravity.START
        }

        val speedText = TextView(activity).apply {
            text = ""
            setTextColor(colorOnSurfaceMedium)
            textSize = 12f
            gravity = Gravity.END
        }

        val detailRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(sizeText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(speedText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 48, 72, 24)
            addView(statusText)
            addView(progressBar)
            addView(detailRow)
        }

        var downloadJob: Job? = null

        val dialog = MaterialAlertDialogBuilder(activity, dialogTheme)
            .setTitle(activity.getString(R.string.update_download_dialog_title))
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton(activity.getString(R.string.action_cancel)) { _, _ -> downloadJob?.cancel() }
            .create()
        (activity as? ClintActivity)?.applyStatusBarFlagToDialog(dialog)
        dialog.show()

        downloadJob = scopeFor(activity).launch {
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(downloadUrl).build()
                    val call = client.newCall(request)
                    currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }

                    call.execute().use { response ->
                        val body = response.body ?: throw Exception("Empty response")
                        val contentLength = body.contentLength()

                        withContext(Dispatchers.Main) {
                            statusText.text = activity.getString(R.string.update_download_in_progress)
                            progressBar.isIndeterminate = contentLength <= 0L
                        }

                        var downloaded = 0L
                        var lastUiBytes = 0L
                        var lastSpeedBytes = 0L
                        var lastSpeedTime = System.currentTimeMillis()
                        var speedBps = 0L

                        body.byteStream().use { input ->
                            apkFile.outputStream().use { output ->
                                val buffer = ByteArray(65536)
                                var bytes: Int
                                while (input.read(buffer).also { bytes = it } != -1) {
                                    output.write(buffer, 0, bytes)
                                    downloaded += bytes
                                    val isLastChunk = contentLength in 1..downloaded
                                    if (downloaded - lastUiBytes >= PROGRESS_UI_THROTTLE_BYTES || isLastChunk) {
                                        lastUiBytes = downloaded
                                        val now = System.currentTimeMillis()
                                        val elapsed = now - lastSpeedTime
                                        if (elapsed >= SPEED_SAMPLE_INTERVAL_MS || isLastChunk) {
                                            val delta = downloaded - lastSpeedBytes
                                            speedBps = if (elapsed > 0) delta * 1000L / elapsed else speedBps
                                            lastSpeedBytes = downloaded
                                            lastSpeedTime = now
                                        }
                                        updateDownloadProgress(
                                            activity, progressBar, sizeText, speedText,
                                            downloaded, contentLength, speedBps
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                prefs.edit().putLong(KEY_CACHED_APK_VERSION_CODE, remoteVersionCode).apply()
                if (dialog.isShowing) dialog.dismiss()
                installApk(activity, apkFile)
            } catch (_: Exception) {
                apkFile.delete()
                if (dialog.isShowing) dialog.dismiss()
            }
        }
    }

    private suspend fun updateDownloadProgress(
        activity: Activity,
        progressBar: ProgressBar,
        sizeText: TextView,
        speedText: TextView,
        downloaded: Long,
        contentLength: Long,
        speedBps: Long
    ) = withContext(Dispatchers.Main) {
        if (contentLength > 0) {
            val pct = (downloaded * 100 / contentLength).toInt()
            progressBar.progress = pct
            sizeText.text = activity.getString(
                R.string.update_download_progress_size_known,
                formatFileSize(downloaded), formatFileSize(contentLength), pct
            )
        } else {
            sizeText.text = activity.getString(
                R.string.update_download_progress_size_unknown, formatFileSize(downloaded)
            )
        }
        speedText.text = if (speedBps > 0) activity.getString(R.string.download_speed_only, formatFileSize(speedBps)) else ""
    }

    private fun installApk(activity: Activity, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        activity.startActivity(intent)
    }

    private fun showNoUpdateDialog(activity: Activity) {
        val dialogTheme = getDialogTheme(activity)
        MaterialAlertDialogBuilder(activity, dialogTheme)
            .setTitle(activity.getString(R.string.update_up_to_date_title))
            .setMessage(activity.getString(R.string.update_up_to_date_message))
            .setPositiveButton(activity.getString(R.string.action_ok), null)
            .create().also { (activity as? ClintActivity)?.applyStatusBarFlagToDialog(it) }.show()
    }

    private fun showErrorDialog(activity: Activity) {
        val dialogTheme = getDialogTheme(activity)
        MaterialAlertDialogBuilder(activity, dialogTheme)
            .setTitle(activity.getString(R.string.update_check_failed_title))
            .setMessage(activity.getString(R.string.update_check_failed_message))
            .setPositiveButton(activity.getString(R.string.action_ok), null)
            .create().also { (activity as? ClintActivity)?.applyStatusBarFlagToDialog(it) }.show()
    }

    private fun extractLatestChangelog(raw: String): String {
        val lines = raw.split("\n")
        val result = mutableListOf<String>()
        var sectionPrefix: String? = null
        for (line in lines) {
            val trimmed = line.trimStart()
            if (sectionPrefix == null) {
                sectionPrefix = when {
                    trimmed.startsWith("## ") -> "## "
                    trimmed.startsWith("# ")  -> "# "
                    else -> null
                }
                if (sectionPrefix != null) result.add(line)
            } else {
                if (trimmed.startsWith(sectionPrefix)) break
                result.add(line)
            }
        }
        return result.joinToString("\n").trim()
    }
}
