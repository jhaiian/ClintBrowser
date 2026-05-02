package com.jhaiian.clint.browser

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jhaiian.clint.R

class MenuBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onMenuGoBack()
        fun onMenuGoForward()
        fun onMenuHome()
        fun onMenuRefreshOrStop()
        fun onMenuToggleBookmark()
        fun onMenuNewTab()
        fun onMenuIncognito()
        fun onMenuShare()
        fun onMenuOpenInApp()
        fun onMenuDownloads()
        fun onMenuBookmarks()
        fun onMenuDesktopMode()
        fun onMenuSettings()
        fun onMenuReaderMode()
        fun onMenuDataSaver()
        fun onMenuOpenDataSaverSettings()
    }

    var showNavRow: Boolean = false
    var canGoBack: Boolean = false
    var canGoForward: Boolean = false
    var isBookmarked: Boolean = false
    var isLoading: Boolean = false
    var isDesktopMode: Boolean = false
    var isDataSaverEnabled: Boolean = false
    var openInAppLabel: String? = null
    var openInAppEnabled: Boolean = false

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
        return inflater.inflate(R.layout.bottom_sheet_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navRow = view.findViewById<View>(R.id.nav_icons_row_sheet)
        val navDivider = view.findViewById<View>(R.id.nav_icons_divider_sheet)
        val desktopCheck = view.findViewById<ImageView>(R.id.sheet_desktop_mode_check)
        val dataSaverCheck = view.findViewById<ImageView>(R.id.sheet_data_saver_check)

        if (showNavRow) {
            navRow.visibility = View.VISIBLE
            navDivider.visibility = View.VISIBLE

            val btnBack = view.findViewById<ImageButton>(R.id.sheet_btn_back)
            val btnForward = view.findViewById<ImageButton>(R.id.sheet_btn_forward)
            val btnRefresh = view.findViewById<ImageButton>(R.id.sheet_btn_refresh)
            val btnBookmark = view.findViewById<ImageButton>(R.id.sheet_btn_bookmark)

            btnBack.alpha = if (canGoBack) 1.0f else 0.38f
            btnForward.alpha = if (canGoForward) 1.0f else 0.38f
            btnRefresh.setImageResource(if (isLoading) R.drawable.ic_close_24 else R.drawable.ic_refresh_24)
            btnBookmark.setImageResource(if (isBookmarked) R.drawable.ic_bookmark_filled_24 else R.drawable.ic_bookmark_24)

            btnBack.setOnClickListener { dismiss(); listener?.onMenuGoBack() }
            btnForward.setOnClickListener { dismiss(); listener?.onMenuGoForward() }
            view.findViewById<ImageButton>(R.id.sheet_btn_home).setOnClickListener { dismiss(); listener?.onMenuHome() }
            btnRefresh.setOnClickListener { dismiss(); listener?.onMenuRefreshOrStop() }
            btnBookmark.setOnClickListener { dismiss(); listener?.onMenuToggleBookmark() }
        }

        desktopCheck.alpha = if (isDesktopMode) 1f else 0f
        dataSaverCheck.alpha = if (isDataSaverEnabled) 1f else 0f

        view.findViewById<View>(R.id.sheet_menu_data_saver).setOnClickListener {
            dismiss()
            listener?.onMenuDataSaver()
        }
        view.findViewById<View>(R.id.sheet_menu_data_saver).setOnLongClickListener {
            dismiss()
            listener?.onMenuOpenDataSaverSettings()
            true
        }

        val openInAppItem = view.findViewById<View>(R.id.sheet_menu_open_in_app)
        val openInAppText = view.findViewById<TextView>(R.id.sheet_menu_open_in_app_text)
        openInAppText.text = openInAppLabel ?: getString(R.string.menu_open_in_app)
        if (!openInAppEnabled) {
            openInAppItem.isEnabled = false
            openInAppItem.alpha = 0.38f
        } else {
            openInAppItem.setOnClickListener { dismiss(); listener?.onMenuOpenInApp() }
        }

        view.findViewById<View>(R.id.sheet_menu_new_tab).setOnClickListener { dismiss(); listener?.onMenuNewTab() }
        view.findViewById<View>(R.id.sheet_menu_incognito).setOnClickListener { dismiss(); listener?.onMenuIncognito() }
        view.findViewById<View>(R.id.sheet_menu_share).setOnClickListener { dismiss(); listener?.onMenuShare() }
        view.findViewById<View>(R.id.sheet_menu_downloads).setOnClickListener { dismiss(); listener?.onMenuDownloads() }
        view.findViewById<View>(R.id.sheet_menu_bookmarks).setOnClickListener { dismiss(); listener?.onMenuBookmarks() }
        view.findViewById<View>(R.id.sheet_menu_desktop_mode).setOnClickListener {
            dismiss()
            listener?.onMenuDesktopMode()
        }
        view.findViewById<View>(R.id.sheet_menu_reader_mode).setOnClickListener { dismiss(); listener?.onMenuReaderMode() }
        view.findViewById<View>(R.id.sheet_menu_settings).setOnClickListener { dismiss(); listener?.onMenuSettings() }
    }
}
