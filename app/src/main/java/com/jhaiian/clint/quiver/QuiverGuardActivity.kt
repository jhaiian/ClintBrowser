package com.jhaiian.clint.quiver

import android.content.Intent
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
import android.widget.Switch
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.ui.ClintToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

// Main UI for the Quiver Guard feature. Displays the list of filter lists with
// their download status, enabled state, and metadata, and hosts all in-activity
// workflows: downloading, compiling, updating, adding custom lists, and searching.
//
// The activity delegates most of its logic to extension-function files:
//   - QuiverGuardDirtyState: pending override and removal tracking
//   - QuiverGuardCompileHelper: compile workflow and dialogs
//   - QuiverGuardDownloadHelper: per-list download workflow
//   - QuiverGuardUpdateHelper: batch update-check workflow
//   - QuiverGuardItemMenuHelper: per-row and multi-select overflow menus
//   - CustomFilterListDialogHelper: add-custom-list-from-link dialog
//   - CustomFilterListFileDialogHelper: add-custom-list-from-file dialog
//   - QuiverGuardFabMenuHelper: expand/collapse animation for the add-list FAB menu
class QuiverGuardActivity : ClintActivity() {

    companion object {
        // Extra passed from the settings screen when Quiver Guard is enabled for the
        // first time, triggering the setup-guide dialog on activity start.
        const val EXTRA_SHOW_SETUP_GUIDE = "extra_show_setup_guide"
        // SharedPreferences key tracking whether the one-time experimental-feature
        // notice has been shown to the user.
        const val PREF_EXPERIMENTAL_SHOWN = "quiver_guard_experimental_shown"
    }

    // SupervisorJob ensures that one failing child coroutine (e.g. a cancelled download)
    // does not cancel other coroutines (e.g. the compile timer job).
    internal val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var filterListAdapter: FilterListAdapter
    private lateinit var filterListDb: FilterListDatabase
    private lateinit var manualFilterDatabase: ManualFilterDatabase
    private lateinit var fastScroller: FilterListFastScroller
    private lateinit var toolbarTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnSearch: ImageView
    private lateinit var btnSearchClose: ImageView
    private lateinit var btnSelectionOptions: ImageView
    private lateinit var btnSelectionItemOptions: ImageView
    private lateinit var btnSort: ImageView
    // btnRefresh is nullable because it may not be present in all layout variants.
    internal var btnRefresh: ImageView? = null
    // btnFilterListActions hosts the overflow menu for update and compile operations.
    // Nullable for the same reason as btnRefresh.
    internal var btnFilterListActions: ImageView? = null
    internal lateinit var fabAdd: FloatingActionButton
    internal lateinit var fabDelete: FloatingActionButton
    internal lateinit var fabMenuScrim: View
    internal lateinit var fabMenuOptions: View
    internal lateinit var recycler: RecyclerView

    // True while the add-list FAB menu (file / link options) is expanded.
    internal var isFabMenuOpen = false

    // Must be registered before the activity reaches STARTED, so it lives here
    // as a field initializer rather than being created inside onCreate's body.
    internal val filePickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri: Uri? = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                handlePickedFilterListFile(uri)
            }
        }

    // IDs of lists currently being downloaded, used to prevent duplicate launches
    // and to update the adapter's downloading indicator.
    private val downloadingIds = mutableSetOf<Long>()
    private var isSearchMode = false

    // In-memory overrides for enabled states that have not yet been compiled.
    // Keyed by filter list ID; a true/false value means the user has toggled the
    // list since the last compile.
    internal val pendingEnabledOverrides = LinkedHashMap<Long, Boolean>()
    // IDs staged for deletion that will be removed from the database after the
    // next successful compile.
    internal val pendingRemovedIds = LinkedHashSet<Long>()
    // True while a QuiverGuardCompiler.compile coroutine is active.
    internal var isCompileRunning = false
    // True when the compiled database does not match the current list configuration,
    // detected at startup by performStartupValidation.
    internal var isStartupDirty = false
    // True while a FilterListUpdateChecker.checkAndUpdateAll coroutine is active.
    internal var isUpdateRunning = false

    internal fun filterListAdapterOrNull(): FilterListAdapter? =
        if (::filterListAdapter.isInitialized) filterListAdapter else null

    internal fun fastScrollerOrNull(): FilterListFastScroller? =
        if (::fastScroller.isInitialized) fastScroller else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars so the toolbar and FABs can inset themselves
        // using WindowInsetsCompat rather than being pushed up by the system.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_quiver_guard)

        val toolbar = findViewById<View>(R.id.quiver_guard_toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, statusBars.top, 0, 0)
            insets
        }

        toolbarTitle = findViewById(R.id.toolbar_title)
        btnBack = findViewById(R.id.btn_back)
        btnSearch = findViewById(R.id.btn_search)
        btnSearchClose = findViewById(R.id.btn_search_close)
        btnSelectionOptions = findViewById(R.id.btn_selection_options)
        btnSelectionItemOptions = findViewById(R.id.btn_selection_item_options)
        btnSort = findViewById(R.id.btn_sort)
        btnRefresh = findViewById(R.id.btn_refresh_filter_lists)
        fabAdd = findViewById(R.id.fab_add_filter_list)
        fabDelete = findViewById(R.id.fab_delete)
        fabMenuScrim = findViewById(R.id.fab_menu_scrim)
        fabMenuOptions = findViewById(R.id.fab_menu_options)
        recycler = findViewById(R.id.recycler_filter_lists)
        fastScroller = findViewById(R.id.filter_list_fast_scroller)
        btnFilterListActions = findViewById(R.id.btn_filter_list_actions)

        // Offset the FABs above the navigation bar so they don't overlap system UI.
        ViewCompat.setOnApplyWindowInsetsListener(fabAdd) { v, insets ->
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
        // The option pills sit directly above the FAB: FAB margin + FAB height + gap.
        ViewCompat.setOnApplyWindowInsetsListener(fabMenuOptions) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = (96 * resources.displayMetrics.density).toInt() + navBars.bottom
            v.layoutParams = lp
            insets
        }

        fabMenuScrim.setOnClickListener { closeFabMenu() }
        findViewById<View>(R.id.fab_menu_item_file).setOnClickListener {
            closeFabMenu()
            launchAddFilterListFromFile()
        }
        findViewById<View>(R.id.fab_menu_item_link).setOnClickListener {
            closeFabMenu()
            showAddCustomFilterListDialog()
        }

        // The back button's behaviour depends on the current UI mode: exit search
        // or selection mode first, then handle navigation-level back.
        btnBack.setOnClickListener {
            when {
                isFabMenuOpen -> closeFabMenu()
                isSearchMode -> exitSearchMode()
                ::filterListAdapter.isInitialized && filterListAdapter.isInSelectionMode -> exitSelectionMode()
                else -> handleBackNavigation()
            }
        }

        btnSearch.setOnClickListener { enterSearchMode() }
        btnSearchClose.setOnClickListener { exitSearchMode() }
        btnSelectionOptions.setOnClickListener { showMoreOptionsMenu(it) }
        btnSelectionItemOptions.setOnClickListener { showSelectionItemOptionsMenu(it) }
        btnSort.setOnClickListener { showSortMenu(it) }
        btnRefresh?.setOnClickListener { showFilterListUpdateConfirmation() }
        // Open the filter list actions overflow menu anchored to the button itself.
        btnFilterListActions?.setOnClickListener { showFilterListActionsMenu(it) }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (::filterListAdapter.isInitialized) {
                    filterListAdapter.setFilter(s?.toString() ?: "")
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

        fabDelete.setOnClickListener { showDeleteConfirmDialog() }

        val masterSwitch = findViewById<Switch>(R.id.switch_quiver_guard)
        val masterRow = findViewById<View>(R.id.row_quiver_guard_master)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        masterSwitch.isChecked = prefs.getBoolean("quiver_guard_enabled", false)
        masterSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("quiver_guard_enabled", isChecked).apply()
            applyMasterState(isChecked)
        }
        // Tapping the row also toggles the switch for a larger tap target.
        masterRow.setOnClickListener { masterSwitch.isChecked = !masterSwitch.isChecked }

        filterListDb = FilterListDatabase(this)
        manualFilterDatabase = ManualFilterDatabase(this)

        filterListAdapter = FilterListAdapter(
            onItemClick = { filterList -> handleFilterListTap(filterList) },
            onSelectionChanged = { count -> updateSelectionUi(count) },
            onShowOptions = { filterList, anchor -> showFilterListItemOptionsMenu(filterList, anchor) },
            onManualFilterClick = { openManualFilter() }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = filterListAdapter
        // Disable the default change animation so toggling a switch does not cause
        // a cross-fade blink on the affected item.
        (recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        fastScroller.attach(recycler, filterListAdapter)
        // The fast scroller is non-interactive until sorting by title is selected,
        // because section letters are not meaningful for date-sorted lists.
        fastScroller.isInteractive = false

        filterListAdapter.updateItems(effectiveFilterLists())
        refreshManualFilterSummary()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isFabMenuOpen -> closeFabMenu()
                    isSearchMode -> exitSearchMode()
                    filterListAdapterOrNull()?.isInSelectionMode == true -> exitSelectionMode()
                    else -> handleBackNavigation()
                }
            }
        })

        applyMasterState(masterSwitch.isChecked)
        performStartupValidation()
        if (intent.getBooleanExtra(EXTRA_SHOW_SETUP_GUIDE, false)) {
            showSetupGuideDialog()
        } else if (!prefs.getBoolean(PREF_EXPERIMENTAL_SHOWN, false)) {
            showExperimentalDialog()
        }
    }

    private val searchEditText: EditText
        get() = findViewById(R.id.search_edit_text)

    // Dims the filter list and disables FABs when Quiver Guard is disabled globally.
    private fun applyMasterState(enabled: Boolean) {
        val dimAlpha = if (enabled) 1f else 0.38f
        if (::filterListAdapter.isInitialized) filterListAdapter.setMasterEnabled(enabled)
        findViewById<TextView>(R.id.tv_filter_lists_section_header).alpha = dimAlpha
        refreshFabState()
    }

    // Adjusts toolbar icons and FAB visibility based on the current selection state.
    // During selection mode the title shows the count, the back button becomes a
    // close icon, and sort/search icons are hidden to reduce clutter.
    private fun updateSelectionUi(selectedCount: Int) {
        closeFabMenu()
        val inSelectionMode = ::filterListAdapter.isInitialized && filterListAdapter.isInSelectionMode

        btnSelectionOptions.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        // The bulk-actions ellipsis needs at least one selected item to act on, so
        // it follows the same condition as the FAB delete button further below.
        btnSelectionItemOptions.visibility = if (inSelectionMode && selectedCount > 0) View.VISIBLE else View.GONE
        btnSort.visibility = if (inSelectionMode || isSearchMode) View.GONE else View.VISIBLE
        btnSearch.visibility = if (inSelectionMode || isSearchMode) View.GONE else View.VISIBLE
        btnRefresh?.visibility = if (inSelectionMode || isSearchMode) View.GONE else View.VISIBLE
        // Hide the actions button in selection and search modes to reduce toolbar clutter.
        btnFilterListActions?.visibility = if (inSelectionMode || isSearchMode) View.GONE else View.VISIBLE

        if (inSelectionMode && selectedCount > 0) {
            fabDelete.visibility = View.VISIBLE
            fabAdd.visibility = View.GONE
        } else {
            fabDelete.visibility = View.GONE
            fabAdd.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        }

        if (inSelectionMode) {
            toolbarTitle.text = getString(R.string.history_selected_count, selectedCount)
            btnBack.setImageResource(R.drawable.ic_close_24)
        } else {
            toolbarTitle.text = getString(R.string.quiver_guard)
            btnBack.setImageResource(R.drawable.ic_arrow_back_24)
            if (!isSearchMode) {
                // Re-enable the fast scroller thumb only when sorted by title,
                // where alphabetical letter bubbles are meaningful.
                fastScroller.isInteractive = filterListAdapter.sortKey == FilterListAdapter.SortKey.TITLE
                fastScroller.attach(recycler, filterListAdapter)
            }
        }

        fastScroller.notifyDataChanged()
    }

    private fun exitSelectionMode() {
        if (::filterListAdapter.isInitialized) filterListAdapter.exitSelectionMode()
        updateSelectionUi(0)
    }

    // Enters search mode: hides regular toolbar icons, shows the search field,
    // requests focus, and shows the soft keyboard.
    private fun enterSearchMode() {
        closeFabMenu()
        isSearchMode = true
        toolbarTitle.visibility = View.GONE
        btnSearch.visibility = View.GONE
        btnSort.visibility = View.GONE
        btnRefresh?.visibility = View.GONE
        btnFilterListActions?.visibility = View.GONE
        searchEditText.visibility = View.VISIBLE
        btnSearchClose.visibility = View.VISIBLE
        fastScroller.isInteractive = false
        searchEditText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    // Exits search mode: clears the query, hides the field, restores toolbar icons,
    // and dismisses the soft keyboard.
    private fun exitSearchMode() {
        isSearchMode = false
        searchEditText.setText("")
        searchEditText.visibility = View.GONE
        btnSearchClose.visibility = View.GONE
        toolbarTitle.visibility = View.VISIBLE
        val inSelectionMode = ::filterListAdapter.isInitialized && filterListAdapter.isInSelectionMode
        if (inSelectionMode) {
            toolbarTitle.text = getString(R.string.history_selected_count, filterListAdapter.selectedCount)
            btnSearch.visibility = View.VISIBLE
        } else {
            btnSearch.visibility = View.VISIBLE
            btnSort.visibility = View.VISIBLE
            btnRefresh?.visibility = View.VISIBLE
            btnFilterListActions?.visibility = View.VISIBLE
        }
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        if (::filterListAdapter.isInitialized) {
            filterListAdapter.setFilter("")
            fastScroller.isInteractive = filterListAdapter.sortKey == FilterListAdapter.SortKey.TITLE
            fastScroller.notifyDataChanged()
        }
    }

    // Shows a PopupWindow anchored below the selection-options icon with
    // select-all, invert-selection, and deselect-all actions.
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
            if (::filterListAdapter.isInitialized) filterListAdapter.selectAll()
        }
        popupView.findViewById<View>(R.id.menu_invert_selection).setOnClickListener {
            popup.dismiss()
            if (::filterListAdapter.isInitialized) filterListAdapter.invertSelection()
        }
        popupView.findViewById<View>(R.id.menu_deselect_all).setOnClickListener {
            popup.dismiss()
            if (::filterListAdapter.isInitialized) filterListAdapter.deselectAll()
        }

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val maxPopupH = (resources.displayMetrics.heightPixels * 0.90).toInt()
        if (popupView.measuredHeight > maxPopupH) popup.height = maxPopupH
        val xOff = -popupView.measuredWidth + anchor.width
        popup.showAsDropDown(anchor, xOff, 0, Gravity.TOP or Gravity.END)
    }

    // Shows a PopupWindow with sort-key (title, date) and sort-order (ascending,
    // descending) options. Checkmarks reflect the current adapter sort state.
    // Switching to date sort also disables the fast scroller since date sections
    // don't have meaningful letter headings.
    private fun showSortMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_filter_list_sort, null)
        val popup = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = 12f
        popup.isOutsideTouchable = true

        val checkTitle = popupView.findViewById<ImageView>(R.id.check_sort_by_title)
        val checkDate = popupView.findViewById<ImageView>(R.id.check_sort_by_date_added)
        val checkAscending = popupView.findViewById<ImageView>(R.id.check_sort_ascending)
        val checkDescending = popupView.findViewById<ImageView>(R.id.check_sort_descending)

        if (::filterListAdapter.isInitialized) {
            checkTitle.visibility = if (filterListAdapter.sortKey == FilterListAdapter.SortKey.TITLE) View.VISIBLE else View.GONE
            checkDate.visibility = if (filterListAdapter.sortKey == FilterListAdapter.SortKey.DATE_DOWNLOADED) View.VISIBLE else View.GONE
            checkAscending.visibility = if (filterListAdapter.sortOrder == FilterListAdapter.SortOrder.ASCENDING) View.VISIBLE else View.GONE
            checkDescending.visibility = if (filterListAdapter.sortOrder == FilterListAdapter.SortOrder.DESCENDING) View.VISIBLE else View.GONE
        }

        popupView.findViewById<View>(R.id.menu_sort_by_title).setOnClickListener {
            popup.dismiss()
            if (::filterListAdapter.isInitialized) {
                filterListAdapter.sortKey = FilterListAdapter.SortKey.TITLE
                filterListAdapter.sortOrder = FilterListAdapter.SortOrder.ASCENDING
                filterListAdapter.applySortAndRefresh()
                fastScroller.isInteractive = true
                fastScroller.notifyDataChanged()
            }
        }
        popupView.findViewById<View>(R.id.menu_sort_by_date_added).setOnClickListener {
            popup.dismiss()
            if (::filterListAdapter.isInitialized) {
                filterListAdapter.sortKey = FilterListAdapter.SortKey.DATE_DOWNLOADED
                filterListAdapter.sortOrder = FilterListAdapter.SortOrder.DESCENDING
                filterListAdapter.applySortAndRefresh()
                fastScroller.isInteractive = false
                fastScroller.notifyDataChanged()
            }
        }
        popupView.findViewById<View>(R.id.menu_sort_ascending).setOnClickListener {
            popup.dismiss()
            if (::filterListAdapter.isInitialized) {
                filterListAdapter.sortOrder = FilterListAdapter.SortOrder.ASCENDING
                filterListAdapter.applySortAndRefresh()
                fastScroller.isInteractive = filterListAdapter.sortKey == FilterListAdapter.SortKey.TITLE
                fastScroller.notifyDataChanged()
            }
        }
        popupView.findViewById<View>(R.id.menu_sort_descending).setOnClickListener {
            popup.dismiss()
            if (::filterListAdapter.isInitialized) {
                filterListAdapter.sortOrder = FilterListAdapter.SortOrder.DESCENDING
                filterListAdapter.applySortAndRefresh()
                fastScroller.isInteractive = filterListAdapter.sortKey == FilterListAdapter.SortKey.TITLE
                fastScroller.notifyDataChanged()
            }
        }

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val maxPopupH = (resources.displayMetrics.heightPixels * 0.90).toInt()
        if (popupView.measuredHeight > maxPopupH) popup.height = maxPopupH
        val xOff = -popupView.measuredWidth + anchor.width
        popup.showAsDropDown(anchor, xOff, 0, Gravity.TOP or Gravity.END)
    }

    // Stages the currently selected lists for deletion and removes them from the
    // adapter. The actual database rows and local files are not deleted until the
    // next compile completes successfully. Reachable from the delete FAB and from
    // the selection-mode overflow menu's Remove action.
    internal fun showDeleteConfirmDialog() {
        if (!::filterListAdapter.isInitialized || filterListAdapter.selectedCount == 0) return
        val count = filterListAdapter.selectedCount
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.filter_list_delete_confirm_title))
            .setMessage(getString(R.string.filter_list_delete_confirm_message, count))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.history_delete_selected)) { _, _ ->
                val ids = filterListAdapter.getSelectedIds()
                stagePendingRemovals(ids)
                filterListAdapter.removeSelectedItems()
                updateSelectionUi(0)
                fastScroller.notifyDataChanged()
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    internal fun isDownloadInProgress(filterListId: Long): Boolean = downloadingIds.contains(filterListId)

    // Updates the downloading-ID set and notifies the adapter to show or hide
    // the downloading indicator on the corresponding list item.
    internal fun markDownloading(filterListId: Long, downloading: Boolean) {
        if (downloading) downloadingIds.add(filterListId) else downloadingIds.remove(filterListId)
        filterListAdapter.setDownloading(filterListId, downloading)
    }

    // Called by the download helper after a successful download to mark the list
    // as enabled and update the adapter item without requiring a full reload.
    internal fun onFilterListDownloaded(updated: FilterList) {
        setPendingEnabled(updated.id, true)
        filterListAdapterOrNull()?.updateItem(
            updated.copy(isEnabled = pendingEnabledOverrides[updated.id] ?: updated.isEnabled)
        )
        fastScrollerOrNull()?.notifyDataChanged()
    }

    // Called by the add-custom-list dialog helper after the file has been saved
    // to disk and the database row created, to insert it into the adapter.
    internal fun onFilterListAdded(added: FilterList) {
        setPendingEnabled(added.id, true)
        filterListAdapterOrNull()?.addItem(
            added.copy(isEnabled = pendingEnabledOverrides[added.id] ?: added.isEnabled)
        )
        fastScrollerOrNull()?.notifyDataChanged()
    }

    // Decides what happens when a list item is tapped: download if not yet
    // downloaded, or toggle enabled state if already downloaded.
    private fun handleFilterListTap(filterList: FilterList) {
        if (filterList.isDownloaded) {
            toggleFilterListEnabled(filterList, !filterList.isEnabled)
        } else {
            startFilterListDownload(filterList)
        }
    }

    // Records a pending enabled-state change without writing to the database.
    // The adapter item is also updated immediately so the toggle appears instant.
    internal fun toggleFilterListEnabled(filterList: FilterList, enabled: Boolean) {
        setPendingEnabled(filterList.id, enabled)
        filterListAdapterOrNull()?.updateItem(filterList.copy(isEnabled = enabled))
    }

    private fun openManualFilter() {
        startActivity(Intent(this, ManualFilterActivity::class.java))
    }

    internal fun database(): FilterListDatabase = filterListDb
    internal fun manualFilterDb(): ManualFilterDatabase = manualFilterDatabase

    // ManualFilterActivity is a separate Activity instance, so anything it changed only
    // becomes visible here once this activity resumes: the pinned row's rule count and the
    // dirty banner both need a fresh look at ManualFilterDatabase and SharedPreferences.
    override fun onResume() {
        super.onResume()
        if (::manualFilterDatabase.isInitialized) {
            refreshManualFilterSummary()
            recheckManualFilterDirtyState()
        }
    }

    override fun onDestroy() {
        fastScroller.detach()
        // Cancel all in-flight download and compile coroutines so they do not
        // outlive the activity and attempt to update destroyed views.
        activityScope.cancel()
        super.onDestroy()
    }
}
