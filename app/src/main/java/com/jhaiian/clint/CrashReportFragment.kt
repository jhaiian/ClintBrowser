package com.jhaiian.clint

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jhaiian.clint.databinding.FragmentCrashReportBinding
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

        CrashHandler.deleteOldReports(requireContext())
        loadReports()
        setupReportTemplate()

        binding.btnClearAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_ClintBrowser_Dialog)
                .setTitle(getString(R.string.crash_clear_title))
                .setMessage(getString(R.string.crash_clear_message))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.crash_clear_confirm)) { _, _ ->
                    CrashHandler.clearAllReports(requireContext())
                    loadReports()
                }
                .show()
        }

        binding.btnOpenGithub.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/jhaiian/Clint-Browser/issues/new")))
            }
        }
    }

    private fun loadReports() {
        val files = CrashHandler.getCrashFiles(requireContext())
        val container = binding.crashListContainer
        container.removeAllViews()

        if (files.isEmpty()) {
            binding.tvNoCrashes.visibility = View.VISIBLE
            binding.btnClearAll.isEnabled = false
            return
        }

        binding.tvNoCrashes.visibility = View.GONE
        binding.btnClearAll.isEnabled = true

        val fileDateFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val displayFmt = SimpleDateFormat("MMM d, yyyy  HH:mm:ss", Locale.US)

        files.forEach { file ->
            val cardView = layoutInflater.inflate(R.layout.item_crash_report, container, false)

            val tvTitle = cardView.findViewById<android.widget.TextView>(R.id.crashTitle)
            val tvContent = cardView.findViewById<android.widget.TextView>(R.id.crashContent)
            val btnCopy = cardView.findViewById<android.widget.ImageButton>(R.id.btnCopyCrash)
            val btnDelete = cardView.findViewById<android.widget.ImageButton>(R.id.btnDeleteCrash)
            val expandArea = cardView.findViewById<View>(R.id.crashExpandArea)

            val nameWithoutExt = file.nameWithoutExtension.removePrefix("crash_")
            val date = runCatching { fileDateFmt.parse(nameWithoutExt) }.getOrNull()
            tvTitle.text = date?.let { displayFmt.format(it) } ?: file.name

            val content = file.readText()
            tvContent.text = content
            tvContent.visibility = View.GONE

            tvTitle.setOnClickListener {
                expandArea.visibility = if (expandArea.visibility == View.GONE) View.VISIBLE else View.GONE
            }

            btnCopy.setOnClickListener {
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Clint Crash Report", content))
                Toast.makeText(requireContext(), getString(R.string.crash_copied), Toast.LENGTH_SHORT).show()
            }

            btnDelete.setOnClickListener {
                file.delete()
                loadReports()
            }

            container.addView(cardView)
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
            Toast.makeText(requireContext(), getString(R.string.crash_template_copied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
