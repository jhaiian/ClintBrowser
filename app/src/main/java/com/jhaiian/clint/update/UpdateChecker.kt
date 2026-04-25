package com.jhaiian.clint.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import io.noties.markwon.Markwon
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

object UpdateChecker {

    private const val STABLE_URL =
        "https://raw.githubusercontent.com/jhaiian/ClintBrowser/main/Update/Stable.json"
    private const val BETA_URL =
        "https://raw.githubusercontent.com/jhaiian/ClintBrowser/main/Update/Beta.json"

    private const val PREFS_NAME = "update_prefs"
    private const val KEY_SKIPPED_VERSION_CODE = "skipped_version_code"
    private const val KEY_CACHED_APK_VERSION_CODE = "cached_apk_version_code"

    private val executor = Executors.newSingleThreadExecutor()
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

    fun check(activity: Activity, isBeta: Boolean, silent: Boolean) {
        executor.submit {
            try {
                val stableJson = fetchJson(STABLE_URL)
                val betaJson   = if (isBeta) fetchJson(BETA_URL) else null

                val (json, isSelectedBeta) = when {
                    betaJson == null -> Pair(stableJson, false)
                    betaJson.getLong("versionCode") > stableJson.getLong("versionCode") ->
                        Pair(betaJson, true)
                    else -> Pair(stableJson, false)
                }

                val remoteVersion = json.getString("version")
                val remoteVersionCode = json.getLong("versionCode")
                val changelog = json.getString("changelog")
                val downloads = json.getJSONObject("downloads")

                val arch = getDeviceArch()
                val downloadUrl = downloads.optString(arch).takeIf { it.isNotEmpty() }
                    ?: downloads.optString("universal").takeIf { it.isNotEmpty() }

                val currentVersionCode = PackageInfoCompat.getLongVersionCode(
                    activity.packageManager.getPackageInfo(activity.packageName, 0)
                )

                val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val skippedVersionCode = prefs.getLong(KEY_SKIPPED_VERSION_CODE, -1L)

                cleanStaleApk(activity, currentVersionCode)

                val hasUpdate = remoteVersionCode > currentVersionCode
                val isSkipped = silent && remoteVersionCode == skippedVersionCode

                activity.runOnUiThread {
                    if (hasUpdate && !isSkipped) {
                        showUpdateDialog(activity, remoteVersion, remoteVersionCode, changelog, downloadUrl, isSelectedBeta)
                    } else if (!silent) {
                        showNoUpdateDialog(activity)
                    }
                }
            } catch (_: Throwable) {
                if (!silent) {
                    activity.runOnUiThread { showErrorDialog(activity) }
                }
            }
        }
    }

    private fun fetchJson(url: String): JSONObject {
        val request = Request.Builder().url(url).build()
        val body = client.newCall(request).execute().body?.string()
            ?: throw Exception("Empty response from $url")
        return JSONObject(body)
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
        val colorPrimary = resolveColor(activity, com.google.android.material.R.attr.colorPrimary)
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
        val colorPrimary = resolveColor(activity, com.google.android.material.R.attr.colorPrimary)

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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { lp -> lp.topMargin = 32; lp.bottomMargin = 8 }
        }

        val percentText = TextView(activity).apply {
            text = activity.getString(R.string.update_download_percent, 0)
            setTextColor(colorOnSurfaceMedium)
            textSize = 12f
            gravity = Gravity.END
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 48, 72, 24)
            addView(statusText)
            addView(progressBar)
            addView(percentText)
        }

        val request = Request.Builder().url(downloadUrl).build()
        val call = client.newCall(request)

        val dialog = MaterialAlertDialogBuilder(activity, dialogTheme)
            .setTitle(activity.getString(R.string.update_download_dialog_title))
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton(activity.getString(R.string.action_cancel)) { _, _ -> call.cancel() }
            .create()
        (activity as? ClintActivity)?.applyStatusBarFlagToDialog(dialog)
        dialog.show()

        executor.submit {
            try {
                val response = call.execute()
                val body = response.body ?: throw Exception("Empty response")
                val contentLength = body.contentLength()

                activity.runOnUiThread { statusText.text = activity.getString(R.string.update_download_in_progress) }

                var downloaded = 0L
                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            if (contentLength > 0) {
                                val pct = (downloaded * 100 / contentLength).toInt()
                                activity.runOnUiThread {
                                    progressBar.progress = pct
                                    percentText.text = activity.getString(R.string.update_download_percent, pct)
                                }
                            }
                        }
                    }
                }

                prefs.edit().putLong(KEY_CACHED_APK_VERSION_CODE, remoteVersionCode).apply()

                activity.runOnUiThread {
                    if (dialog.isShowing) dialog.dismiss()
                    installApk(activity, apkFile)
                }
            } catch (_: Exception) {
                apkFile.delete()
                activity.runOnUiThread {
                    if (dialog.isShowing) dialog.dismiss()
                }
            }
        }
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

    private fun getDeviceArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return "universal"
        return when {
            abi.contains("arm64") -> "arm64-v8a"
            abi.contains("armeabi") -> "armeabi-v7a"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("x86") -> "x86"
            else -> "universal"
        }
    }
}
