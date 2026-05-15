package com.jhaiian.clint.settings.desktopmode

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.settings.sitepermissions.SitePermissionAdapter
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.settings.sitepermissions.SitePermissionFastScroller
import com.jhaiian.clint.settings.sitepermissions.SitePermissionManager
import com.jhaiian.clint.ui.ClintToast

class DesktopModeActivity : ClintActivity() {

    private lateinit var adapter: SitePermissionAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var fastScroller: SitePermissionFastScroller
    private lateinit var toolbarTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnSearch: ImageView
    private lateinit var btnSearchClose: ImageView
    private lateinit var btnSelectionOptions: ImageView
    private lateinit var btnSort: ImageView
    private lateinit var searchEditText: EditText
    private lateinit var fab: FloatingActionButton
    private lateinit var fabDelete: FloatingActionButton

    private lateinit var cardSaveState: MaterialCardView
    private lateinit var cardDoNotSave: MaterialCardView
    private lateinit var radioSaveState: RadioButton
    private lateinit var radioDoNotSave: RadioButton

    private var isSearchMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_desktop_mode)

        val toolbar = findViewById<View>(R.id.desktop_mode_toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, statusBars.top, 0, 0)
            insets
        }

        fab = findViewById(R.id.fab_add)
        fabDelete = findViewById(R.id.fab_delete)

        ViewCompat.setOnApplyWindowInsetsListener(fab) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = (24 * resources.displayMetrics.density).toInt() + navBars.bottom
            v.layoutParams = lp
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(fabDelete) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = (24 * resources.displayMetrics.density).toInt() + navBars.bottom
            v.layoutParams = lp
            insets
        }

        recycler = findViewById(R.id.recycler_exceptions)
        fastScroller = findViewById(R.id.site_permission_fast_scroller)
        tvEmpty = findViewById(R.id.tv_empty)
        toolbarTitle = findViewById(R.id.toolbar_title)
        btnBack = findViewById(R.id.btn_back)
        btnSearch = findViewById(R.id.btn_search)
        btnSearchClose = findViewById(R.id.btn_search_close)
        btnSelectionOptions = findViewById(R.id.btn_selection_options)
        btnSort = findViewById(R.id.btn_sort)
        searchEditText = findViewById(R.id.search_edit_text)

        cardSaveState = findViewById(R.id.card_save_state)
        cardDoNotSave = findViewById(R.id.card_do_not_save_state)
        radioSaveState = findViewById(R.id.radio_save_state)
        radioDoNotSave = findViewById(R.id.radio_do_not_save_state)

        btnBack.setOnClickListener {
            when {
                isSearchMode -> exitSearchMode()
                ::adapter.isInitialized && adapter.isInSelectionMode -> exitSelectionMode()
                else -> onBackPressedDispatcher.onBackPressed()
            }
        }

        btnSearch.setOnClickListener { enterSearchMode() }
        btnSearchClose.setOnClickListener { exitSearchMode() }
        btnSelectionOptions.setOnClickListener { showMoreOptionsMenu(it) }
        btnSort.setOnClickListener { showSortMenu(it) }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (::adapter.isInitialized) {
                    adapter.setFilter(s?.toString() ?: "")
                    updateEmptyState()
                    fastScroller.notifyDataChanged()
                }
            }
        })

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                true
            } else false
        }

        fab.setOnClickListener { showAddSiteDialog() }
        fabDelete.setOnClickListener { showDeleteConfirmDialog() }

        setupCardSelection()
        setupRecycler()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isSearchMode -> exitSearchMode()
            ::adapter.isInitialized && adapter.isInSelectionMode -> exitSelectionMode()
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    private fun setupCardSelection() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString(PREF_DESKTOP_MODE_SAVE_STATE, VALUE_SAVE_STATE) ?: VALUE_SAVE_STATE
        val strokePx = (3 * resources.displayMetrics.density).toInt()

        fun selectOption(key: String) {
            val isSave = key == VALUE_SAVE_STATE
            cardSaveState.strokeWidth = if (isSave) strokePx else 0
            cardSaveState.alpha = if (isSave) 1f else 0.45f
            radioSaveState.isChecked = isSave

            cardDoNotSave.strokeWidth = if (!isSave) strokePx else 0
            cardDoNotSave.alpha = if (!isSave) 1f else 0.45f
            radioDoNotSave.isChecked = !isSave

            prefs.edit().putString(PREF_DESKTOP_MODE_SAVE_STATE, key).apply()
        }

        selectOption(current)

        cardSaveState.setOnClickListener { selectOption(VALUE_SAVE_STATE) }
        cardDoNotSave.setOnClickListener { selectOption(VALUE_DO_NOT_SAVE) }
    }

    private fun setupRecycler() {
        adapter = SitePermissionAdapter(
            onSelectionChanged = { count -> updateSelectionUi(count) },
            stateToLabel = { _, context ->
                val label = context.getString(R.string.desktop_mode_state_on)
                val color = com.google.android.material.color.MaterialColors
                    .getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)
                Pair(label, color)
            }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        fastScroller.attach(recycler, adapter)
        fastScroller.isInteractive = adapter.sortKey == SitePermissionAdapter.SortKey.TITLE
        refreshList()
    }

    private fun refreshList() {
        val items = SitePermissionManager.getAllByType(this, SitePermissionDatabase.TYPE_DESKTOP_MODE)
        adapter.updateItems(items)
        updateEmptyState()
        fastScroller.notifyDataChanged()
    }

    private fun updateEmptyState() {
        val hasItems = adapter.itemCount > 0
        recycler.visibility = if (hasItems) View.VISIBLE else View.GONE
        tvEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
        fastScroller.visibility = if (hasItems) View.VISIBLE else View.GONE
    }

    private fun updateSelectionUi(selectedCount: Int) {
        val inSelectionMode = ::adapter.isInitialized && adapter.isInSelectionMode

        btnSelectionOptions.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        btnSort.visibility = if (inSelectionMode || isSearchMode) View.GONE else View.VISIBLE
        btnSearch.visibility = if (inSelectionMode || isSearchMode) View.GONE else View.VISIBLE

        if (inSelectionMode && selectedCount > 0) {
            fabDelete.visibility = View.VISIBLE
            fab.visibility = View.GONE
        } else {
            fabDelete.visibility = View.GONE
            fab.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        }

        if (inSelectionMode) {
            toolbarTitle.text = getString(R.string.history_selected_count, selectedCount)
            btnBack.setImageResource(R.drawable.ic_close_24)
            btnSearch.visibility = if (isSearchMode) View.GONE else View.VISIBLE
        } else {
            toolbarTitle.text = getString(R.string.site_settings_desktop_mode)
            btnBack.setImageResource(R.drawable.ic_arrow_back_24)
            if (!isSearchMode) {
                fab.visibility = View.VISIBLE
                if (::adapter.isInitialized) {
                    fastScroller.isInteractive = adapter.sortKey == SitePermissionAdapter.SortKey.TITLE
                    fastScroller.attach(recycler, adapter)
                }
            }
        }

        updateEmptyState()
    }

    private fun exitSelectionMode() {
        if (::adapter.isInitialized) adapter.exitSelectionMode()
        updateSelectionUi(0)
    }

    private fun enterSearchMode() {
        isSearchMode = true
        toolbarTitle.visibility = View.GONE
        btnSearch.visibility = View.GONE
        btnSort.visibility = View.GONE
        searchEditText.visibility = View.VISIBLE
        btnSearchClose.visibility = View.VISIBLE
        fastScroller.isInteractive = false
        searchEditText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun exitSearchMode() {
        isSearchMode = false
        searchEditText.setText("")
        searchEditText.visibility = View.GONE
        btnSearchClose.visibility = View.GONE
        toolbarTitle.visibility = View.VISIBLE
        val inSelectionMode = ::adapter.isInitialized && adapter.isInSelectionMode
        if (inSelectionMode) {
            toolbarTitle.text = getString(R.string.history_selected_count, adapter.selectedCount)
            btnSearch.visibility = View.VISIBLE
        } else {
            btnSearch.visibility = View.VISIBLE
            btnSort.visibility = View.VISIBLE
        }
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        if (::adapter.isInitialized) {
            adapter.setFilter("")
            fastScroller.isInteractive = adapter.sortKey == SitePermissionAdapter.SortKey.TITLE
            fastScroller.notifyDataChanged()
            updateEmptyState()
        }
    }

    private fun showMoreOptionsMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_history_selection, null)
        val popup = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = 12f
        popup.isOutsideTouchable = true

        popupView.findViewById<View>(R.id.menu_select_all).setOnClickListener {
            popup.dismiss()
            if (::adapter.isInitialized) adapter.selectAll()
        }
        popupView.findViewById<View>(R.id.menu_invert_selection).setOnClickListener {
            popup.dismiss()
            if (::adapter.isInitialized) adapter.invertSelection()
        }
        popupView.findViewById<View>(R.id.menu_deselect_all).setOnClickListener {
            popup.dismiss()
            if (::adapter.isInitialized) adapter.deselectAll()
        }

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val xOff = -popupView.measuredWidth + anchor.width
        popup.showAsDropDown(anchor, xOff, 0, Gravity.TOP or Gravity.END)
    }

    private fun showSortMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_site_permission_sort, null)
        val popup = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = 12f
        popup.isOutsideTouchable = true

        val checkTitle = popupView.findViewById<ImageView>(R.id.check_sort_by_title)
        val checkDateAdded = popupView.findViewById<ImageView>(R.id.check_sort_by_date_added)
        val checkAscending = popupView.findViewById<ImageView>(R.id.check_sort_ascending)
        val checkDescending = popupView.findViewById<ImageView>(R.id.check_sort_descending)

        if (::adapter.isInitialized) {
            checkTitle.visibility = if (adapter.sortKey == SitePermissionAdapter.SortKey.TITLE) View.VISIBLE else View.GONE
            checkDateAdded.visibility = if (adapter.sortKey == SitePermissionAdapter.SortKey.DATE_ADDED) View.VISIBLE else View.GONE
            checkAscending.visibility = if (adapter.sortOrder == SitePermissionAdapter.SortOrder.ASCENDING) View.VISIBLE else View.GONE
            checkDescending.visibility = if (adapter.sortOrder == SitePermissionAdapter.SortOrder.DESCENDING) View.VISIBLE else View.GONE
        }

        popupView.findViewById<View>(R.id.menu_sort_by_title).setOnClickListener {
            popup.dismiss()
            if (::adapter.isInitialized) {
                adapter.sortKey = SitePermissionAdapter.SortKey.TITLE
                adapter.sortOrder = SitePermissionAdapter.SortOrder.ASCENDING
                adapter.applySortAndRefresh()
                fastScroller.isInteractive = true
                updateEmptyState()
                fastScroller.notifyDataChanged()
            }
        }
        popupView.findViewById<View>(R.id.menu_sort_by_date_added).setOnClickListener {
            popup.dismiss()
            if (::adapter.isInitialized) {
                adapter.sortKey = SitePermissionAdapter.SortKey.DATE_ADDED
                adapter.sortOrder = SitePermissionAdapter.SortOrder.DESCENDING
                adapter.applySortAndRefresh()
                fastScroller.isInteractive = false
                updateEmptyState()
                fastScroller.notifyDataChanged()
            }
        }
        popupView.findViewById<View>(R.id.menu_sort_ascending).setOnClickListener {
            popup.dismiss()
            if (::adapter.isInitialized) {
                adapter.sortOrder = SitePermissionAdapter.SortOrder.ASCENDING
                adapter.applySortAndRefresh()
                fastScroller.isInteractive = adapter.sortKey == SitePermissionAdapter.SortKey.TITLE
                updateEmptyState()
                fastScroller.notifyDataChanged()
            }
        }
        popupView.findViewById<View>(R.id.menu_sort_descending).setOnClickListener {
            popup.dismiss()
            if (::adapter.isInitialized) {
                adapter.sortOrder = SitePermissionAdapter.SortOrder.DESCENDING
                adapter.applySortAndRefresh()
                fastScroller.isInteractive = adapter.sortKey == SitePermissionAdapter.SortKey.TITLE
                updateEmptyState()
                fastScroller.notifyDataChanged()
            }
        }

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val xOff = -popupView.measuredWidth + anchor.width
        popup.showAsDropDown(anchor, xOff, 0, Gravity.TOP or Gravity.END)
    }

    private fun showDeleteConfirmDialog() {
        if (!::adapter.isInitialized || adapter.selectedCount == 0) return
        val count = adapter.selectedCount
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.desktop_mode_delete_confirm_title))
            .setMessage(getString(R.string.desktop_mode_delete_confirm_message, count))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.history_delete_selected)) { _, _ ->
                val origins = adapter.getSelectedOrigins()
                for (origin in origins) {
                    SitePermissionManager.deleteEntry(this, origin, SitePermissionDatabase.TYPE_DESKTOP_MODE)
                }
                adapter.removeSelectedItems()
                updateSelectionUi(0)
                fastScroller.notifyDataChanged()
                ClintToast.show(this, getString(R.string.desktop_mode_items_deleted), R.drawable.ic_delete_24)
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun showAddSiteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_site_exception, null)
        val etOrigin = dialogView.findViewById<TextInputEditText>(R.id.et_origin)
        dialogView.findViewById<View>(R.id.radio_allowed)?.visibility = View.GONE
        dialogView.findViewById<View>(R.id.radio_denied)?.visibility = View.GONE

        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.desktop_mode_add_site))
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.site_permission_add)) { _, _ ->
                val rawInput = etOrigin.text?.toString()?.trim() ?: ""
                val origin = normalizeOrigin(rawInput)
                if (origin.isNotEmpty()) {
                    SitePermissionManager.setState(
                        this, origin,
                        SitePermissionDatabase.TYPE_DESKTOP_MODE,
                        SitePermissionDatabase.STATE_ALLOW
                    )
                    refreshList()
                }
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun normalizeOrigin(input: String): String {
        if (input.isEmpty()) return ""
        val withScheme = if (!input.contains("://")) "https://$input" else input
        val host = Uri.parse(withScheme).host?.takeIf { it.isNotEmpty() } ?: return input
        return com.jhaiian.clint.util.registeredDomain(host)
    }

    companion object {
        const val PREF_DESKTOP_MODE_SAVE_STATE = "desktop_mode_save_state"
        const val VALUE_SAVE_STATE   = "save"
        const val VALUE_DO_NOT_SAVE  = "do_not_save"
    }
}
