package com.jhaiian.clint.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.databinding.FragmentCrashReportBinding
import com.jhaiian.clint.ui.ClintToast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class CrashReportFragment : Fragment() {

    private var _binding: FragmentCrashReportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCrashReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadReports()
        setupSteps()
        setupReportTemplate()

        binding.btnClearAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), (requireActivity() as ClintActivity).getDialogTheme())
                .setTitle(getString(R.string.crash_clear_title))
                .setMessage(getString(R.string.crash_clear_message))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.crash_clear_confirm)) { _, _ ->
                    val appCtx = requireContext().applicationContext
                    Thread {
                        CrashHandler.clearAllReports(appCtx)
                        activity?.runOnUiThread { loadReports() }
                    }.start()
                }
                .create().also { (requireActivity() as ClintActivity).applyStatusBarFlagToDialog(it) }.show()
        }

        binding.btnOpenGithub.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/jhaiian/ClintBrowser/issues/new")))
            }
        }
    }

    private fun loadReports(deleteFirst: File? = null) {
        val appCtx = requireContext().applicationContext
        val fileDateFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val displayFmt = SimpleDateFormat("MMM d, yyyy  HH:mm:ss", Locale.US)
        Thread {
            deleteFirst?.delete()
            CrashHandler.deleteOldReports(appCtx)
            val files = CrashHandler.getCrashFiles(appCtx)
            val items = files.map { file ->
                val nameWithoutExt = file.nameWithoutExtension.removePrefix("crash_")
                val date = runCatching { fileDateFmt.parse(nameWithoutExt) }.getOrNull()
                val title = date?.let { displayFmt.format(it) } ?: file.name
                val content = file.readText()
                Triple(file, title, content)
            }
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                populateReports(items)
            }
        }.start()
    }

    private fun populateReports(items: List<Triple<File, String, String>>) {
        val container = binding.crashListContainer
        container.removeAllViews()

        if (items.isEmpty()) {
            binding.tvNoCrashes.visibility = View.VISIBLE
            binding.btnClearAll.isEnabled = false
            return
        }

        binding.tvNoCrashes.visibility = View.GONE
        binding.btnClearAll.isEnabled = true

        items.forEach { (file, title, content) ->
            val cardView = layoutInflater.inflate(R.layout.item_crash_report, container, false)

            val tvTitle = cardView.findViewById<TextView>(R.id.crashTitle)
            val btnCopy = cardView.findViewById<android.widget.ImageButton>(R.id.btnCopyCrash)
            val btnDelete = cardView.findViewById<android.widget.ImageButton>(R.id.btnDeleteCrash)

            tvTitle.text = title
            tvTitle.setOnClickListener { showCrashDialog(file, title, content) }
            btnCopy.setOnClickListener { copyToClipboard(content) }
            btnDelete.setOnClickListener { loadReports(deleteFirst = file) }

            container.addView(cardView)
        }
    }

    private fun showCrashDialog(file: File, title: String, content: String) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val ta = ctx.obtainStyledAttributes(
            intArrayOf(
                com.google.android.material.R.attr.colorOnSurface,
                com.google.android.material.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorError,
                R.attr.clintSecondaryTextColor,
                R.attr.clintDividerColor
            )
        )
        val colorOnSurface    = ta.getColor(0, 0xFFFFFFFF.toInt())
        val colorPrimary      = ta.getColor(1, 0xFFBA68C8.toInt())
        val colorError        = ta.getColor(2, 0xFFCF6679.toInt())
        val colorSecondary    = ta.getColor(3, 0x99FFFFFF.toInt())
        val colorDivider      = ta.getColor(4, 0x22FFFFFF.toInt())
        ta.recycle()

        val logTv = TextView(ctx).apply {
            text = content
            setTextColor(colorOnSurface)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(64, 24, 64, 8)
            setTextIsSelectable(true)
        }

        val divider = View(ctx).apply {
            setBackgroundColor(colorDivider)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.topMargin = (8 * dp).toInt() }
        }

        fun makeBtn(label: String, color: Int) = TextView(ctx).apply {
            text = label
            setTextColor(color)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            background = android.util.TypedValue().let { tv ->
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                androidx.core.content.ContextCompat.getDrawable(ctx, tv.resourceId)
            }
        }

        val btnDelete = makeBtn(getString(R.string.action_delete), colorError)
        val btnBack   = makeBtn(getString(R.string.back),          colorSecondary)
        val btnCopy   = makeBtn(getString(R.string.action_copy),   colorPrimary)

        val buttonRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            val hPad = (8 * dp).toInt()
            val vPad = (4 * dp).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            addView(btnDelete, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnBack)
            addView(btnCopy)
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(ctx).apply {
                addView(logTv)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(divider)
            addView(buttonRow)
        }

        val dialog = MaterialAlertDialogBuilder(ctx, (requireActivity() as ClintActivity).getDialogTheme())
            .setTitle(title)
            .setView(container)
            .setCancelable(false)
            .create()

        btnBack.setOnClickListener { dialog.dismiss() }

        btnCopy.setOnClickListener {
            copyToClipboard(content)
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            loadReports(deleteFirst = file)
        }

        (requireActivity() as ClintActivity).applyStatusBarFlagToDialog(dialog)
        dialog.show()
    }

    private fun copyToClipboard(content: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Clint Crash Report", content))
        ClintToast.show(requireContext(), getString(R.string.crash_copied), R.drawable.ic_check_24)
    }

    private fun setupSteps() {
        val steps = listOf(
            binding.step1 to R.string.crash_step_reproduce,
            binding.step2 to R.string.crash_step_expand,
            binding.step3 to R.string.crash_step_copy,
            binding.step4 to R.string.crash_step_open_github,
            binding.step5 to R.string.crash_step_new_issue,
            binding.step6 to R.string.crash_step_attach,
            binding.step7 to R.string.crash_step_submit
        )
        steps.forEachIndexed { index, (stepBinding, stringRes) ->
            stepBinding.tvStepNumber.text = "${index + 1}."
            stepBinding.tvStepText.text = getString(stringRes)
        }
    }

    private fun setupReportTemplate() {
        val deviceInfo = CrashHandler.buildDeviceInfo(requireContext())
        val template = buildString {
            appendLine("**Device Information**")
            deviceInfo.lines().filter { it.isNotBlank() }.forEach {
                appendLine("- $it")
            }
            appendLine()
            appendLine("**Steps to Reproduce**")
            appendLine("1. ")
            appendLine("2. ")
            appendLine("3. ")
            appendLine()
            appendLine("**Expected Behavior**")
            appendLine("")
            appendLine()
            appendLine("**Actual Behavior**")
            appendLine("")
            appendLine()
            appendLine("**Crash Report** _(paste from Debug screen above)_")
            appendLine("```")
            appendLine("(paste here)")
            appendLine("```")
        }

        binding.tvReportTemplate.text = template

        binding.btnCopyTemplate.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Bug Report Template", template))
            ClintToast.show(requireContext(), getString(R.string.crash_template_copied), R.drawable.ic_check_24)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
