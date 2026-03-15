package com.jhaiian.clint

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class TabSwitcherSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onTabSelected(index: Int)
        fun onTabClosed(index: Int)
        fun onNewTab()
        fun onNewIncognitoTab()
    }

    var tabs: MutableList<TabPreview> = mutableListOf()
    var activeIndex: Int = 0
    private var listener: Listener? = null

    private lateinit var adapter: TabAdapter

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
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_dark))
            sheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.peekHeight = resources.displayMetrics.heightPixels / 2
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = false
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.bottom_sheet_tabs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TabAdapter(
            tabs = tabs,
            activeIndex = activeIndex,
            onTabClick = { index ->
                listener?.onTabSelected(index)
                dismiss()
            },
            onTabClose = { index ->
                listener?.onTabClosed(index)
                adapter.removeAt(index)
                updateHeader(view)
                if (tabs.isEmpty()) dismiss()
            }
        )

        view.findViewById<RecyclerView>(R.id.tabsRecycler).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TabSwitcherSheet.adapter
        }

        updateHeader(view)

        view.findViewById<MaterialButton>(R.id.btnNewTab).setOnClickListener {
            listener?.onNewTab()
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnNewIncognito).setOnClickListener {
            listener?.onNewIncognitoTab()
            dismiss()
        }
    }

    private fun updateHeader(view: View) {
        val regular = tabs.count { !it.isIncognito }
        val incognito = tabs.count { it.isIncognito }
        val parts = mutableListOf<String>()
        if (regular > 0) parts.add("$regular tab${if (regular != 1) "s" else ""}")
        if (incognito > 0) parts.add("$incognito incognito")
        view.findViewById<android.widget.TextView>(R.id.sheetTitle).text =
            if (tabs.isEmpty()) getString(R.string.no_tabs) else parts.joinToString("  ·  ")
    }
}
