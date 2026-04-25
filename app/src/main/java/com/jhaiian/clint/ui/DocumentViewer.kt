package com.jhaiian.clint.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import io.noties.markwon.Markwon
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors

object DocumentViewer {

    private val client = OkHttpClient()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    const val PRIVACY_POLICY_URL =
        "https://raw.githubusercontent.com/jhaiian/ClintBrowser/main/PRIVACY_POLICY.md"
    const val TERMS_URL =
        "https://raw.githubusercontent.com/jhaiian/ClintBrowser/main/TERMS_OF_SERVICE.md"
    const val CHANGELOG_URL =
        "https://raw.githubusercontent.com/jhaiian/ClintBrowser/main/CHANGELOG.md"

    private fun getDialogTheme(context: Context): Int {
        return if (context is ClintActivity) context.getDialogTheme()
        else R.style.ThemeOverlay_ClintBrowser_Dialog
    }

    private fun resolveColor(context: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    fun show(context: Context, title: String, url: String) {
        val dp = context.resources.displayMetrics.density
        val dialogTheme = getDialogTheme(context)
        val colorOnSurface = resolveColor(context, com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceMedium = (colorOnSurface and 0x00FFFFFF) or 0xCC000000.toInt().let {
            val alpha = ((colorOnSurface ushr 24) * 0.8).toInt()
            (colorOnSurface and 0x00FFFFFF) or (alpha shl 24)
        }
        val colorPrimary = resolveColor(context, com.google.android.material.R.attr.colorPrimary)
        val dividerColor = resolveColor(context, R.attr.clintDividerColor)

        val spinner = ProgressBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = (32 * dp).toInt()
                it.bottomMargin = (32 * dp).toInt()
            }
            isIndeterminate = true
        }

        val contentTv = TextView(context).apply {
            visibility = View.GONE
            setPadding(64, 24, 64, 8)
            setTextColor(colorOnSurface)
            textSize = 13f
        }

        val errorTv = TextView(context).apply {
            visibility = View.GONE
            setPadding(64, 32, 64, 32)
            setTextColor(colorOnSurfaceMedium)
            textSize = 13f
            gravity = Gravity.CENTER
            text = context.getString(R.string.document_viewer_error)
        }

        val scrollView = ScrollView(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(spinner)
                addView(contentTv)
                addView(errorTv)
            })
        }

        val divider = View(context).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.topMargin = (8 * dp).toInt() }
        }

        val btnBack = TextView(context).apply {
            text = context.getString(R.string.back)
            setTextColor(colorPrimary)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            background = TypedValue().let { tv ->
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                androidx.core.content.ContextCompat.getDrawable(context, tv.resourceId)
            }
        }

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            val hPad = (8 * dp).toInt()
            val vPad = (4 * dp).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            addView(btnBack)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(scrollView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(divider)
            addView(buttonRow)
        }

        val dialog = MaterialAlertDialogBuilder(context, dialogTheme)
            .setTitle(title)
            .setView(container)
            .setCancelable(false)
            .create()

        btnBack.setOnClickListener { dialog.dismiss() }
        (context as? ClintActivity)?.applyStatusBarFlagToDialog(dialog)
        dialog.show()

        executor.submit {
            try {
                val request = Request.Builder().url(url).build()
                val markdown = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.string() ?: throw Exception("Empty body")
                }
                mainHandler.post {
                    if (!dialog.isShowing) return@post
                    val markwon = Markwon.create(context)
                    markwon.setMarkdown(contentTv, markdown)
                    spinner.visibility = View.GONE
                    contentTv.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                mainHandler.post {
                    if (!dialog.isShowing) return@post
                    spinner.visibility = View.GONE
                    errorTv.visibility = View.VISIBLE
                }
            }
        }
    }
}
