package com.jhaiian.clint.browser

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jhaiian.clint.R

class PreviewLinkLongPressSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onPreviewLinkOpenInNewTab(url: String)
        fun onPreviewLinkOpenIncognito(url: String)
        fun onPreviewLinkOpenInCurrentTab(url: String)
        fun onPreviewLinkCopyAddress(url: String)
        fun onPreviewLinkCopyText(url: String, text: String)
        fun onPreviewLinkShare(url: String)
    }

    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (prefs.getBoolean("hide_status_bar", false)) {
            @Suppress("DEPRECATION")
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.bottom_sheet_preview_link_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val url = arguments?.getString(ARG_URL) ?: ""
        val linkText = arguments?.getString(ARG_LINK_TEXT) ?: ""

        view.findViewById<TextView>(R.id.preview_link_url_label).text = url

        val linkTextLabel = view.findViewById<TextView>(R.id.preview_link_text_label)
        if (linkText.isNotEmpty() && linkText != url) {
            linkTextLabel.text = linkText
            linkTextLabel.visibility = View.VISIBLE
        } else {
            linkTextLabel.visibility = View.GONE
        }

        val copyTextAction = view.findViewById<View>(R.id.action_copy_text)
        copyTextAction.visibility = if (linkText.isNotEmpty()) View.VISIBLE else View.GONE
        val dividerBefore = copyTextAction.previousSibling()
        dividerBefore?.visibility = if (linkText.isNotEmpty()) View.VISIBLE else View.GONE

        view.findViewById<View>(R.id.action_open_in_new_tab).setOnClickListener {
            dismiss()
            listener?.onPreviewLinkOpenInNewTab(url)
        }
        view.findViewById<View>(R.id.action_open_incognito).setOnClickListener {
            dismiss()
            listener?.onPreviewLinkOpenIncognito(url)
        }
        view.findViewById<View>(R.id.action_open_in_current_tab).setOnClickListener {
            dismiss()
            listener?.onPreviewLinkOpenInCurrentTab(url)
        }
        view.findViewById<View>(R.id.action_copy_address).setOnClickListener {
            dismiss()
            listener?.onPreviewLinkCopyAddress(url)
        }
        copyTextAction.setOnClickListener {
            dismiss()
            listener?.onPreviewLinkCopyText(url, linkText)
        }
        view.findViewById<View>(R.id.action_share_link).setOnClickListener {
            dismiss()
            listener?.onPreviewLinkShare(url)
        }
    }

    private fun View.previousSibling(): View? {
        val parent = parent as? ViewGroup ?: return null
        val idx = parent.indexOfChild(this)
        return if (idx > 0) parent.getChildAt(idx - 1) else null
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_LINK_TEXT = "link_text"

        fun newInstance(url: String, linkText: String): PreviewLinkLongPressSheet {
            return PreviewLinkLongPressSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putString(ARG_LINK_TEXT, linkText)
                }
            }
        }
    }
}
