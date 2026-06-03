package com.jhaiian.clint.bookmarks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.history.HistoryFastScroller
import com.jhaiian.clint.ui.ClintToast
import com.jhaiian.clint.ui.FaviconCache

class BookmarksActivity : ClintActivity() {

    enum class SortBy { TITLE, LAST_VISIT, DATE_ADDED }
    enum class SortOrder { ASCENDING, DESCENDING }

    private lateinit var adapter: BookmarksAdapter
    private lateinit var emptyView: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var toolbarTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnSort: ImageView
    private lateinit var btnSelectionOptions: ImageView
    private lateinit var fabDelete: FloatingActionButton
    private lateinit var fastScroller: HistoryFastScroller
    private lateinit var btnSearch: ImageView
    private lateinit var btnSearchClose: ImageView
    private lateinit var searchEditText: EditText

    private var isSearchMode = false
    private var sortBy: SortBy = SortBy.LAST_VISIT
    private var sortOrder: SortOrder = SortOrder.DESCENDING
    private var allItems: MutableList<Bookmark> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_bookmarks)

        val toolbar = findViewById<View>(R.id.bookmarks_toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, statusBars.top, 0, 0)
            insets
        }

        val recyclerView = findViewById<RecyclerView>(R.id.bookmarks_recycler)
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, navBars.bottom)
            insets
        }

        fabDelete = findViewById(R.id.fab_delete)
        ViewCompat.setOnApplyWindowInsetsListener(fabDelete) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = (24 * resources.displayMetrics.density).toInt() + navBars.bottom
            v.layoutParams = lp
            insets
        }

        toolbarTitle = findViewById(R.id.toolbar_title)
        btnBack = findViewById(R.id.btn_back)
        btnSort = findViewById(R.id.btn_sort)
        btnSelectionOptions = findViewById(R.id.btn_selection_options)
        btnSearch = findViewById(R.id.btn_search)
        btnSearchClose = findViewById(R.id.btn_search_close)
        searchEditText = findViewById(R.id.search_edit_text)
        emptyView = findViewById(R.id.bookmarks_empty)
        recycler = recyclerView
        fastScroller = findViewById(R.id.bookmarks_fast_scroller)

        btnBack.setOnClickListener {
            when {
                isSearchMode -> exitSearchMode()
                ::adapter.isInitialized && adapter.isInSelectionMode -> exitSelectionMode()
                else -> finish()
            }
        }

        btnSearch.setOnClickListener { enterSearchMode() }
        btnSearchClose.setOnClickListener { exitSearchMode() }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (::adapter.isInitialized) {
                    adapter.setFilter(s?.toString() ?: "")
                    emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
                    recycler.visibility = if (adapter.itemCount > 0) View.VISIBLE else View.GONE
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

        btnSort.setOnClickListener { showSortMenu(it) }
        btnSelectionOptions.setOnClickListener { showMoreOptionsMenu(it) }
        fabDelete.setOnClickListener { showDeleteConfirmDialog() }

        recycler.layoutManager = LinearLayoutManager(this)
        loadBookmarks()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isSearchMode -> exitSearchMode()
            ::adapter.isInitialized && adapter.isInSelectionMode -> exitSelectionMode()
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    private fun showSortMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_bookmarks_sort, null)
        val popup = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = 12f
        popup.isOutsideTouchable = true

        popupView.findViewById<View>(R.id.check_sort_by_title).visibility =
            if (sortBy == SortBy.TITLE) View.VISIBLE else View.GONE
        popupView.findViewById<View>(R.id.check_sort_by_last_visit).visibility =
            if (sortBy == SortBy.LAST_VISIT) View.VISIBLE else View.GONE
        popupView.findViewById<View>(R.id.check_sort_by_date_added).visibility =
            if (sortBy == SortBy.DATE_ADDED) View.VISIBLE else View.GONE
        popupView.findViewById<View>(R.id.check_sort_ascending).visibility =
            if (sortOrder == SortOrder.ASCENDING) View.VISIBLE else View.GONE
        popupView.findViewById<View>(R.id.check_sort_descending).visibility =
            if (sortOrder == SortOrder.DESCENDING) View.VISIBLE else View.GONE

        popupView.findViewById<View>(R.id.menu_sort_by_title).setOnClickListener {
            popup.dismiss(); sortBy = SortBy.TITLE; sortOrder = SortOrder.ASCENDING; applySortAndRefresh()
        }
        popupView.findViewById<View>(R.id.menu_sort_by_last_visit).setOnClickListener {
            popup.dismiss(); sortBy = SortBy.LAST_VISIT; sortOrder = SortOrder.DESCENDING; applySortAndRefresh()
        }
        popupView.findViewById<View>(R.id.menu_sort_by_date_added).setOnClickListener {
            popup.dismiss(); sortBy = SortBy.DATE_ADDED; sortOrder = SortOrder.DESCENDING; applySortAndRefresh()
        }
        popupView.findViewById<View>(R.id.menu_sort_ascending).setOnClickListener {
            popup.dismiss(); sortOrder = SortOrder.ASCENDING; applySortAndRefresh()
        }
        popupView.findViewById<View>(R.id.menu_sort_descending).setOnClickListener {
            popup.dismiss(); sortOrder = SortOrder.DESCENDING; applySortAndRefresh()
        }

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val maxPopupH = (resources.displayMetrics.heightPixels * 0.90).toInt()
        if (popupView.measuredHeight > maxPopupH) {
            popup.height = maxPopupH
        }
        popup.showAsDropDown(anchor, -popupView.measuredWidth + anchor.width, 0, Gravity.TOP or Gravity.END)
    }

    private fun showMoreOptionsMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_history_selection, null)
        val popup = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = 12f
        popup.isOutsideTouchable = true

        popupView.findViewById<View>(R.id.menu_select_all).setOnClickListener {
            popup.dismiss(); if (::adapter.isInitialized) adapter.selectAll()
        }
        popupView.findViewById<View>(R.id.menu_invert_selection).setOnClickListener {
            popup.dismiss(); if (::adapter.isInitialized) adapter.invertSelection()
        }
        popupView.findViewById<View>(R.id.menu_deselect_all).setOnClickListener {
            popup.dismiss(); if (::adapter.isInitialized) adapter.deselectAll()
        }

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val maxPopupH = (resources.displayMetrics.heightPixels * 0.90).toInt()
        if (popupView.measuredHeight > maxPopupH) {
            popup.height = maxPopupH
        }
        popup.showAsDropDown(anchor, -popupView.measuredWidth + anchor.width, 0, Gravity.TOP or Gravity.END)
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
            toolbarTitle.text = getString(R.string.bookmarks_selected_count, adapter.selectedCount)
            btnSearch.visibility = View.VISIBLE
        } else {
            toolbarTitle.text = getString(R.string.bookmarks_title)
            btnSearch.visibility = View.VISIBLE
            btnSort.visibility = View.VISIBLE
        }
        if (::adapter.isInitialized) {
            fastScroller.isInteractive = sortBy == SortBy.TITLE
            fastScroller.notifyDataChanged()
        }
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        if (::adapter.isInitialized) {
            adapter.setFilter("")
            val hasItems = adapter.itemCount > 0
            emptyView.visibility = if (hasItems) View.GONE else View.VISIBLE
            recycler.visibility = if (hasItems) View.VISIBLE else View.GONE
        }
    }

    private fun loadBookmarks() {
        Thread {
            val items = BookmarkManager.getAll(this)
            runOnUiThread {
                allItems = items
                if (allItems.isEmpty()) showEmpty() else showList(getSortedItems())
            }
        }.start()
    }

    private fun applySortAndRefresh() {
        if (allItems.isEmpty()) return
        if (::adapter.isInitialized) {
            adapter.updateItems(getSortedItems())
            fastScroller.isInteractive = sortBy == SortBy.TITLE
            fastScroller.notifyDataChanged()
        }
    }

    private fun getSortedItems(): MutableList<Bookmark> {
        val sorted = when (sortBy) {
            SortBy.TITLE -> allItems.sortedWith(compareBy { it.title.ifBlank { it.url }.lowercase() })
            SortBy.LAST_VISIT -> allItems.sortedBy { it.lastVisit }
            SortBy.DATE_ADDED -> allItems.sortedBy { it.addedAt }
        }
        return if (sortOrder == SortOrder.DESCENDING) sorted.reversed().toMutableList()
        else sorted.toMutableList()
    }

    private fun showEmpty() {
        emptyView.visibility = View.VISIBLE
        recycler.visibility = View.GONE
    }

    private fun showList(items: MutableList<Bookmark>) {
        emptyView.visibility = View.GONE
        recycler.visibility = View.VISIBLE

        adapter = BookmarksAdapter(
            items = items,
            onOpen = { bookmark ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bookmark.url))
                intent.setPackage(packageName)
                startActivity(intent)
                finish()
            },
            onSelectionChanged = { count -> updateSelectionUi(count) }
        )
        recycler.adapter = adapter
        fastScroller.visibility = View.VISIBLE
        fastScroller.isInteractive = sortBy == SortBy.TITLE
        fastScroller.attach(recycler, adapter)
    }

    private fun updateSelectionUi(selectedCount: Int) {
        val inSelectionMode = ::adapter.isInitialized && adapter.isInSelectionMode

        btnSelectionOptions.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        btnSort.visibility = if (inSelectionMode) View.GONE else View.VISIBLE

        if (inSelectionMode && selectedCount > 0) fabDelete.show() else fabDelete.hide()

        if (inSelectionMode) {
            toolbarTitle.text = getString(R.string.bookmarks_selected_count, selectedCount)
            btnBack.setImageResource(R.drawable.ic_close_24)
            btnSearch.visibility = if (isSearchMode) View.GONE else View.VISIBLE
        } else {
            toolbarTitle.text = getString(R.string.bookmarks_title)
            btnBack.setImageResource(R.drawable.ic_arrow_back_24)
            if (!isSearchMode) {
                btnSearch.visibility = View.VISIBLE
                if (::adapter.isInitialized) {
                    fastScroller.visibility = View.VISIBLE
                    fastScroller.isInteractive = sortBy == SortBy.TITLE
                    fastScroller.attach(recycler, adapter)
                }
            }
        }

        if (::adapter.isInitialized && adapter.itemCount == 0) showEmpty()
    }

    private fun exitSelectionMode() {
        if (::adapter.isInitialized) adapter.exitSelectionMode()
        updateSelectionUi(0)
    }

    private fun showDeleteConfirmDialog() {
        if (!::adapter.isInitialized || adapter.selectedCount == 0) return
        val count = adapter.selectedCount
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.bookmarks_delete_confirm_title))
            .setMessage(getString(R.string.bookmarks_delete_confirm_message, count))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.bookmarks_delete_selected)) { _, _ ->
                val toDelete = adapter.getSelectedItems()
                Thread {
                    for (item in toDelete) {
                        FaviconCache.evict(applicationContext, item.url)
                        BookmarkManager.remove(applicationContext, item.url)
                    }
                    runOnUiThread {
                        val deletedUrls = toDelete.map { it.url }.toSet()
                        allItems.removeAll { it.url in deletedUrls }
                        adapter.removeSelectedItems()
                        updateSelectionUi(0)
                        if (adapter.itemCount == 0) showEmpty()
                        ClintToast.show(this, getString(R.string.bookmarks_items_deleted), R.drawable.ic_delete_24)
                    }
                }.start()
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }
}