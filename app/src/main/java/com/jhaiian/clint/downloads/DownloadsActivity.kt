package com.jhaiian.clint.downloads

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.ui.ClintToast

class DownloadsActivity : ClintActivity() {

    companion object {
        const val EXTRA_OPEN_ID = "open_download_id"
    }

    enum class SortBy { NAME, DATE, SIZE, STATUS }
    enum class SortOrder { ASCENDING, DESCENDING }

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refresh() }
    @Volatile internal var lastRefreshMs = 0L
    private val minRefreshIntervalMs = 400L

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var toolbarTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnSort: ImageView
    private lateinit var btnDownloadSettings: ImageView
    private lateinit var btnSelectionOptions: ImageView
    private lateinit var btnMultiItemOptions: ImageView
    private lateinit var btnSearch: ImageView
    private lateinit var btnSearchClose: ImageView
    private lateinit var searchEditText: EditText
    private lateinit var fabDelete: FloatingActionButton
    private lateinit var fabAdd: FloatingActionButton

    internal var manualFolderPickerCallback: ((Uri) -> Unit)? = null
    internal val manualFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            manualFolderPickerCallback?.invoke(uri)
        }
        manualFolderPickerCallback = null
    }

    private var isSearchMode = false
    internal var sortBy: SortBy = SortBy.DATE
    internal var sortOrder: SortOrder = SortOrder.DESCENDING
    private var allItems: MutableList<DownloadItem> = mutableListOf()

    internal val sharedSelection = SharedSelectionState()
    private val tabCounts = IntArray(DownloadsTabType.values().size)
    internal val tabFragments = mutableMapOf<DownloadsTabType, DownloadsTabFragment>()

    private var tabLayoutMediator: TabLayoutMediator? = null

    private var pendingApkItem: DownloadItem? = null
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val item = pendingApkItem ?: return@registerForActivityResult
        pendingApkItem = null
        if (packageManager.canRequestPackageInstalls()) launchApkInstall(item)
    }

    internal fun registerTabFragment(type: DownloadsTabType, fragment: DownloadsTabFragment) {
        tabFragments[type] = fragment
    }

    internal fun unregisterTabFragment(type: DownloadsTabType) {
        tabFragments.remove(type)
    }

    internal fun getItemsForTab(type: DownloadsTabType): List<DownloadItem> {
        val sorted = getSortedItems()
        return when (type) {
            DownloadsTabType.ALL -> sorted
            DownloadsTabType.DOWNLOADING -> sorted.filter { it.status in DownloadsTabType.ACTIVE_STATUSES }
            DownloadsTabType.FINISHED -> sorted.filter { it.status == DownloadStatus.COMPLETE }
            DownloadsTabType.ERROR -> sorted.filter { it.status == DownloadStatus.FAILED }
        }
    }

    internal fun onTabSelectionChanged(count: Int) {
        tabFragments.values.forEach { if (it.isAdded) it.syncSelectionUi() }
        updateSelectionUi(count)
    }

    private val currentTabFragment: DownloadsTabFragment?
        get() = tabFragments[DownloadsTabType.values()[viewPager.currentItem]]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_downloads)

        val toolbar = findViewById<View>(R.id.downloads_toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, statusBars.top, 0, 0)
            insets
        }

        viewPager = findViewById(R.id.downloads_view_pager)
        tabLayout = findViewById(R.id.downloads_tab_layout)

        fabDelete = findViewById(R.id.fab_delete)
        ViewCompat.setOnApplyWindowInsetsListener(fabDelete) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = (24 * resources.displayMetrics.density).toInt() + navBars.bottom
            v.layoutParams = lp
            insets
        }

        fabAdd = findViewById(R.id.fab_add_download)
        ViewCompat.setOnApplyWindowInsetsListener(fabAdd) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = (24 * resources.displayMetrics.density).toInt() + navBars.bottom
            v.layoutParams = lp
            insets
        }
        fabAdd.setOnClickListener { showManualDownloadDialog() }

        toolbarTitle = findViewById(R.id.toolbar_title)
        btnBack = findViewById(R.id.btn_back)
        btnSort = findViewById(R.id.btn_sort)
        btnDownloadSettings = findViewById(R.id.btn_download_settings)
        btnSelectionOptions = findViewById(R.id.btn_selection_options)
        btnMultiItemOptions = findViewById(R.id.btn_multi_item_options)
        btnSearch = findViewById(R.id.btn_search)
        btnSearchClose = findViewById(R.id.btn_search_close)
        searchEditText = findViewById(R.id.search_edit_text)

        btnBack.setOnClickListener {
            when {
                isSearchMode -> exitSearchMode()
                sharedSelection.isActive -> exitSelectionMode()
                else -> finish()
            }
        }

        btnSearch.setOnClickListener { enterSearchMode() }
        btnSearchClose.setOnClickListener { exitSearchMode() }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                tabFragments.values.forEach { it.setTextFilter(query) }
            }
        })

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(searchEditText.windowToken, 0)
                true
            } else false
        }

        btnSort.setOnClickListener { showSortMenu(it) }
        btnDownloadSettings.setOnClickListener {
            val intent = Intent(this, com.jhaiian.clint.settings.SettingsActivity::class.java)
            intent.putExtra(com.jhaiian.clint.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "download_settings")
            startActivity(intent)
        }
        btnSelectionOptions.setOnClickListener { showMoreOptionsMenu(it) }
        btnMultiItemOptions.setOnClickListener { showMultiItemOptions(it) }
        fabDelete.setOnClickListener { showDeleteConfirmDialog() }

        val pagerAdapter = DownloadsPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = DownloadsTabType.values().size - 1

        tabLayoutMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            val type = DownloadsTabType.values()[position]
            val count = tabCounts[position]
            val name = tabNameForType(type)
            tab.text = getString(R.string.downloads_tab_label_format, name, count)
        }.also { it.attach() }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val fragment = tabFragments[DownloadsTabType.values()[position]]
                fragment?.syncSelectionUi()
                val inSelection = sharedSelection.isActive
                val selCount = sharedSelection.count
                updateSelectionUi(if (inSelection) selCount else 0)
            }
        })

        handleOpenIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lastRefreshMs = 0L
        ClintDownloadManager.onDownloadsChanged = {
            val now = System.currentTimeMillis()
            val hasActiveDownload = synchronized(ClintDownloadManager.downloads) {
                ClintDownloadManager.downloads.any {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MOVING
                }
            }
            if (!hasActiveDownload) lastRefreshMs = 0L
            if (now - lastRefreshMs >= minRefreshIntervalMs) {
                lastRefreshMs = now
                handler.post(refreshRunnable)
            }
        }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        ClintDownloadManager.onDownloadsChanged = null
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        tabLayoutMediator?.detach()
        handler.removeCallbacks(refreshRunnable)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isSearchMode -> exitSearchMode()
            sharedSelection.isActive -> exitSelectionMode()
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    private fun refresh() {
        allItems = synchronized(ClintDownloadManager.downloads) {
            ClintDownloadManager.downloads.map { it.copy() }
        }.toMutableList()

        val allSorted = getSortedItems()
        val downloadingItems = allSorted.filter { it.status in DownloadsTabType.ACTIVE_STATUSES }
        val finishedItems = allSorted.filter { it.status == DownloadStatus.COMPLETE }
        val errorItems = allSorted.filter { it.status == DownloadStatus.FAILED }

        tabCounts[DownloadsTabType.ALL.ordinal] = allSorted.size
        tabCounts[DownloadsTabType.DOWNLOADING.ordinal] = downloadingItems.size
        tabCounts[DownloadsTabType.FINISHED.ordinal] = finishedItems.size
        tabCounts[DownloadsTabType.ERROR.ordinal] = errorItems.size

        tabFragments[DownloadsTabType.ALL]?.refreshItems(allSorted)
        tabFragments[DownloadsTabType.DOWNLOADING]?.refreshItems(downloadingItems)
        tabFragments[DownloadsTabType.FINISHED]?.refreshItems(finishedItems)
        tabFragments[DownloadsTabType.ERROR]?.refreshItems(errorItems)

        updateTabLabels()
    }

    private fun updateTabLabels() {
        DownloadsTabType.values().forEachIndexed { index, type ->
            val tab = tabLayout.getTabAt(index) ?: return@forEachIndexed
            val name = tabNameForType(type)
            tab.text = getString(R.string.downloads_tab_label_format, name, tabCounts[index])
        }
    }

    private fun tabNameForType(type: DownloadsTabType): String = when (type) {
        DownloadsTabType.ALL -> getString(R.string.downloads_tab_all)
        DownloadsTabType.DOWNLOADING -> getString(R.string.downloads_tab_downloading)
        DownloadsTabType.FINISHED -> getString(R.string.downloads_tab_finished)
        DownloadsTabType.ERROR -> getString(R.string.downloads_tab_error)
    }

    private fun getSortedItems(): List<DownloadItem> {
        val statusPriority = mapOf(
            DownloadStatus.CONNECTING to 0,
            DownloadStatus.ALLOCATING to 0,
            DownloadStatus.DOWNLOADING to 0,
            DownloadStatus.RETRYING to 0,
            DownloadStatus.MOVING to 0,
            DownloadStatus.QUEUED to 1,
            DownloadStatus.PAUSED to 2,
            DownloadStatus.FAILED to 3,
            DownloadStatus.COMPLETE to 4
        )
        val sorted = when (sortBy) {
            SortBy.NAME -> allItems.sortedBy { it.filename.lowercase() }
            SortBy.DATE -> allItems.sortedBy { it.startedAt }
            SortBy.SIZE -> allItems.sortedBy { if (it.totalBytes > 0) it.totalBytes else it.bytesDownloaded }
            SortBy.STATUS -> allItems.sortedWith(
                compareBy({ statusPriority[it.status] ?: 99 }, { -it.startedAt })
            )
        }
        return if (sortOrder == SortOrder.DESCENDING && sortBy != SortBy.STATUS) sorted.reversed()
        else sorted
    }

    private fun applySortAndRefresh() {
        val allSorted = getSortedItems()
        val downloadingItems = allSorted.filter { it.status in DownloadsTabType.ACTIVE_STATUSES }
        val finishedItems = allSorted.filter { it.status == DownloadStatus.COMPLETE }
        val errorItems = allSorted.filter { it.status == DownloadStatus.FAILED }

        tabCounts[DownloadsTabType.ALL.ordinal] = allSorted.size
        tabCounts[DownloadsTabType.DOWNLOADING.ordinal] = downloadingItems.size
        tabCounts[DownloadsTabType.FINISHED.ordinal] = finishedItems.size
        tabCounts[DownloadsTabType.ERROR.ordinal] = errorItems.size

        tabFragments[DownloadsTabType.ALL]?.let { f ->
            f.adapter.updateItems(allSorted)
            f.notifyFastScrollerDataChanged()
        }
        tabFragments[DownloadsTabType.DOWNLOADING]?.let { f ->
            f.adapter.updateItems(downloadingItems)
            f.notifyFastScrollerDataChanged()
        }
        tabFragments[DownloadsTabType.FINISHED]?.let { f ->
            f.adapter.updateItems(finishedItems)
            f.notifyFastScrollerDataChanged()
        }
        tabFragments[DownloadsTabType.ERROR]?.let { f ->
            f.adapter.updateItems(errorItems)
            f.notifyFastScrollerDataChanged()
        }

        val isNameSort = sortBy == SortBy.NAME
        tabFragments.values.forEach { it.setFastScrollerInteractive(isNameSort) }

        updateTabLabels()
    }

    private fun showSortMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_downloads_sort, null)
        val popup = PopupWindow(
            popupView, ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
        }

        popupView.findViewById<View>(R.id.check_sort_by_name).visibility =
            if (sortBy == SortBy.NAME) View.VISIBLE else View.GONE
        popupView.findViewById<View>(R.id.check_sort_by_date).visibility =
            if (sortBy == SortBy.DATE) View.VISIBLE else View.GONE
        popupView.findViewById<View>(R.id.check_sort_by_size).visibility =
            if (sortBy == SortBy.SIZE) View.VISIBLE else View.GONE
        popupView.findViewById<View>(R.id.check_sort_by_status).visibility =
            if (sortBy == SortBy.STATUS) View.VISIBLE else View.GONE
        popupView.findViewById<View>(R.id.check_sort_ascending).visibility =
            if (sortOrder == SortOrder.ASCENDING) View.VISIBLE else View.GONE
        popupView.findViewById<View>(R.id.check_sort_descending).visibility =
            if (sortOrder == SortOrder.DESCENDING) View.VISIBLE else View.GONE

        popupView.findViewById<View>(R.id.menu_sort_by_name).setOnClickListener {
            popup.dismiss(); sortBy = SortBy.NAME; sortOrder = SortOrder.ASCENDING; applySortAndRefresh()
        }
        popupView.findViewById<View>(R.id.menu_sort_by_date).setOnClickListener {
            popup.dismiss(); sortBy = SortBy.DATE; sortOrder = SortOrder.DESCENDING; applySortAndRefresh()
        }
        popupView.findViewById<View>(R.id.menu_sort_by_size).setOnClickListener {
            popup.dismiss(); sortBy = SortBy.SIZE; sortOrder = SortOrder.DESCENDING; applySortAndRefresh()
        }
        popupView.findViewById<View>(R.id.menu_sort_by_status).setOnClickListener {
            popup.dismiss(); sortBy = SortBy.STATUS; sortOrder = SortOrder.ASCENDING; applySortAndRefresh()
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
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_downloads_selection, null)
        val popup = PopupWindow(
            popupView, ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
        }

        popupView.findViewById<View>(R.id.menu_select_all).setOnClickListener {
            popup.dismiss(); currentTabFragment?.selectAll()
        }
        popupView.findViewById<View>(R.id.menu_invert_selection).setOnClickListener {
            popup.dismiss(); currentTabFragment?.invertSelection()
        }
        popupView.findViewById<View>(R.id.menu_deselect_all).setOnClickListener {
            popup.dismiss()
            sharedSelection.ids.clear()
            tabFragments.values.forEach { if (it.isAdded) it.syncSelectionUi() }
            updateSelectionUi(0)
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
        btnDownloadSettings.visibility = View.GONE
        searchEditText.visibility = View.VISIBLE
        btnSearchClose.visibility = View.VISIBLE
        tabFragments.values.forEach { it.setFastScrollerInteractive(false) }
        searchEditText.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun exitSearchMode() {
        isSearchMode = false
        searchEditText.setText("")
        searchEditText.visibility = View.GONE
        btnSearchClose.visibility = View.GONE
        toolbarTitle.visibility = View.VISIBLE
        val inSelectionMode = sharedSelection.isActive
        if (inSelectionMode) {
            toolbarTitle.text = getString(R.string.downloads_selected_count, sharedSelection.count)
            btnSearch.visibility = View.VISIBLE
        } else {
            toolbarTitle.text = getString(R.string.downloads_title)
            btnSearch.visibility = View.VISIBLE
            btnSort.visibility = View.VISIBLE
            btnDownloadSettings.visibility = View.VISIBLE
        }
        val isNameSort = sortBy == SortBy.NAME
        tabFragments.values.forEach { it.setFastScrollerInteractive(isNameSort) }
        tabFragments.values.forEach { it.setTextFilter("") }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchEditText.windowToken, 0)
    }

    private fun updateSelectionUi(selectedCount: Int) {
        val inSelectionMode = sharedSelection.isActive

        btnSelectionOptions.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        btnMultiItemOptions.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        btnSort.visibility = if (inSelectionMode || isSearchMode) View.GONE else View.VISIBLE
        btnDownloadSettings.visibility = if (inSelectionMode || isSearchMode) View.GONE else View.VISIBLE

        if (inSelectionMode && sharedSelection.count > 0) fabDelete.show() else fabDelete.hide()
        if (inSelectionMode) fabAdd.hide() else fabAdd.show()

        if (inSelectionMode) {
            toolbarTitle.text = getString(R.string.downloads_selected_count, selectedCount)
            btnBack.setImageResource(R.drawable.ic_close_24)
            btnSearch.visibility = if (isSearchMode) View.GONE else View.VISIBLE
        } else {
            toolbarTitle.text = getString(R.string.downloads_title)
            btnBack.setImageResource(R.drawable.ic_arrow_back_24)
            if (!isSearchMode) {
                btnSearch.visibility = View.VISIBLE
                val isNameSort = sortBy == SortBy.NAME
                currentTabFragment?.setFastScrollerInteractive(isNameSort)
                currentTabFragment?.notifyFastScrollerDataChanged()
            }
        }
    }

    private fun exitSelectionMode() {
        sharedSelection.clear()
        tabFragments.values.forEach { if (it.isAdded) it.syncSelectionUi() }
        updateSelectionUi(0)
    }

    private fun showDeleteConfirmDialog() {
        val frag = currentTabFragment ?: return
        if (frag.selectedCount == 0) return
        val count = frag.selectedCount
        val checkboxView = layoutInflater.inflate(R.layout.dialog_download_delete_checkbox, null)
        checkboxView.findViewById<TextView>(R.id.tv_delete_message).text =
            getString(R.string.downloads_delete_confirm_message, count)
        val cbDeleteFromStorage = checkboxView.findViewById<android.widget.CheckBox>(R.id.cb_delete_from_storage)
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.downloads_delete_confirm_title))
            .setView(checkboxView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                executeDeleteItems(frag.getSelectedItems(), cbDeleteFromStorage.isChecked)
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun executeDeleteItems(toRemove: List<DownloadItem>, deleteFromStorage: Boolean) {
        val count = toRemove.size
        if (count == 0) return

        val progressView = layoutInflater.inflate(R.layout.dialog_download_delete_progress, null)
        val progressBar = progressView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progress_bar)
        val progressText = progressView.findViewById<TextView>(R.id.progress_text)
        progressBar.max = count
        progressBar.progress = 0
        progressText.text = getString(R.string.downloads_deleting_progress, 0, count)

        val progressDialog = MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.downloads_deleting_title))
            .setView(progressView)
            .setCancelable(false)
            .create()
            .also { applyStatusBarFlagToDialog(it) }
        progressDialog.show()

        Thread {
            toRemove.forEachIndexed { index, item ->
                ClintDownloadManager.remove(this, item.id, deleteFromStorage)
                val done = index + 1
                runOnUiThread {
                    progressBar.progress = done
                    progressText.text = getString(R.string.downloads_deleting_progress, done, count)
                }
            }
            runOnUiThread {
                progressDialog.dismiss()
                exitSelectionMode()
                refresh()
                ClintToast.show(this, getString(R.string.downloads_items_removed), R.drawable.ic_delete_24)
            }
        }.start()
    }

    private fun handleOpenIntent(intent: Intent?) {
        val id = intent?.getIntExtra(EXTRA_OPEN_ID, -1) ?: return
        if (id == -1) return
        val item = synchronized(ClintDownloadManager.downloads) {
            ClintDownloadManager.downloads.find { it.id == id }
        } ?: return
        handleOpenItem(item)
    }

    internal fun handleOpenItem(item: DownloadItem) {
        if (item.status != DownloadStatus.COMPLETE) return
        val ext = when {
            item.file != null -> item.file!!.extension.lowercase()
            item.contentUri != null -> item.filename.substringAfterLast('.').lowercase()
            else -> return
        }
        if (ext == "apk") handleApkOpen(item) else openFile(item)
    }

    private fun handleApkOpen(item: DownloadItem) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.install_apk_dialog_title))
            .setMessage(getString(R.string.install_apk_dialog_message, item.filename))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.install_apk_dialog_confirm)) { _, _ ->
                if (packageManager.canRequestPackageInstalls()) launchApkInstall(item)
                else showInstallPermissionDialog(item)
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun showInstallPermissionDialog(item: DownloadItem) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.install_apk_permission_title))
            .setMessage(getString(R.string.install_apk_permission_message))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_open_settings)) { _, _ ->
                pendingApkItem = item
                installPermissionLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                )
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun launchApkInstall(item: DownloadItem) {
        val uri = when {
            item.file != null -> FileProvider.getUriForFile(this, "$packageName.fileprovider", item.file!!)
            item.contentUri != null -> Uri.parse(item.contentUri)
            else -> return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }

    private fun showMultiItemOptions(anchor: View) {
        val frag = currentTabFragment ?: return
        val selected = frag.getSelectedItems()
        if (selected.isEmpty()) return
        if (selected.size == 1) {
            showDownloadItemOptions(selected[0], anchor)
            return
        }

        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_download_multi_options, null)
        val popup = PopupWindow(
            popupView, ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
        }

        popupView.findViewById<View>(R.id.menu_multi_redownload).setOnClickListener {
            popup.dismiss()
            multiRedownload(selected)
        }
        popupView.findViewById<View>(R.id.menu_multi_remove).setOnClickListener {
            popup.dismiss()
            multiRemove(selected)
        }
        popupView.findViewById<View>(R.id.menu_multi_copy_link).setOnClickListener {
            popup.dismiss()
            multiCopyToClipboard(
                selected.joinToString("\n") { it.url },
                getString(R.string.download_menu_link_copied)
            )
        }
        popupView.findViewById<View>(R.id.menu_multi_copy_filename).setOnClickListener {
            popup.dismiss()
            multiCopyToClipboard(
                selected.joinToString("\n") { it.filename },
                getString(R.string.download_menu_filename_copied)
            )
        }
        popupView.findViewById<View>(R.id.menu_multi_copy_path).setOnClickListener {
            popup.dismiss()
            multiCopyToClipboard(
                selected.joinToString("\n") { item ->
                    when {
                        item.file != null -> item.file!!.absolutePath
                        item.contentUri != null -> {
                            val uri = Uri.parse(item.contentUri)
                            val seg = uri.lastPathSegment ?: item.contentUri!!
                            when {
                                seg.startsWith("primary:") -> "/storage/emulated/0/${seg.removePrefix("primary:")}"
                                seg.contains(":") -> { val p = seg.split(":", limit = 2); "/storage/${p[0]}/${p[1]}" }
                                else -> item.contentUri!!
                            }
                        }
                        else -> item.filename
                    }
                },
                getString(R.string.download_menu_path_copied)
            )
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

    private fun multiRedownload(items: List<DownloadItem>) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.redownload_multi_confirm_title, items.size))
            .setMessage(getString(R.string.redownload_multi_confirm_message))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.redownload_confirm_action)) { _, _ ->
                items.forEach { item ->
                    ClintDownloadManager.remove(this, item.id, true)
                    ClintDownloadManager.enqueue(
                        this, item.url, item.filename, item.userAgent,
                        item.referer, item.cookies,
                        retryEnabled = item.retryEnabled,
                        unmeteredOnly = item.unmeteredOnly,
                        splitParts = item.splitParts,
                        multithreadingParts = item.multithreadingParts,
                        locationMode = item.locationMode,
                        customLocationUri = item.customLocationUri
                    )
                }
                lastRefreshMs = 0L
                exitSelectionMode()
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun multiRemove(items: List<DownloadItem>) {
        val checkboxView = layoutInflater.inflate(R.layout.dialog_download_delete_checkbox, null)
        checkboxView.findViewById<TextView>(R.id.tv_delete_message).text =
            getString(R.string.downloads_delete_confirm_message, items.size)
        val cbDeleteFromStorage = checkboxView.findViewById<android.widget.CheckBox>(R.id.cb_delete_from_storage)
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.downloads_delete_confirm_title))
            .setView(checkboxView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                items.forEach { item ->
                    ClintDownloadManager.remove(this, item.id, cbDeleteFromStorage.isChecked)
                }
                lastRefreshMs = 0L
                exitSelectionMode()
                refresh()
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun multiCopyToClipboard(text: String, toastMessage: String) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", text))
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            ClintToast.show(this, toastMessage, R.drawable.ic_copy_24)
        }
    }

    internal fun showDownloadItemOptions(item: DownloadItem, anchor: android.view.View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_download_item_options, null)
        val popup = PopupWindow(
            popupView, ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
        }

        popupView.findViewById<View>(R.id.menu_download_open).setOnClickListener {
            popup.dismiss(); handleOpenItem(item)
        }
        popupView.findViewById<View>(R.id.menu_download_share).setOnClickListener {
            popup.dismiss(); shareFile(item)
        }
        popupView.findViewById<View>(R.id.menu_download_open_folder).setOnClickListener {
            popup.dismiss(); openFolder(item)
        }
        popupView.findViewById<View>(R.id.menu_download_redownload).setOnClickListener {
            popup.dismiss(); redownload(item)
        }
        popupView.findViewById<View>(R.id.menu_download_redownload_options).setOnClickListener {
            popup.dismiss(); showRedownloadDialog(item)
        }

        val menuUpdateLink = popupView.findViewById<View>(R.id.menu_download_update_link)
        val menuUpdateLinkInBrowser = popupView.findViewById<View>(R.id.menu_download_update_link_in_browser)
        if (item.status != DownloadStatus.COMPLETE) {
            menuUpdateLink.visibility = View.VISIBLE
            menuUpdateLink.setOnClickListener {
                popup.dismiss(); showUpdateDownloadLinkDialog(item)
            }
            menuUpdateLinkInBrowser.visibility = View.VISIBLE
            menuUpdateLinkInBrowser.setOnClickListener {
                popup.dismiss(); openBrowserForRefreshLink(item)
            }
        }

        popupView.findViewById<View>(R.id.menu_download_remove).setOnClickListener {
            popup.dismiss(); removeDownload(item)
        }
        popupView.findViewById<View>(R.id.menu_download_copy_link).setOnClickListener {
            popup.dismiss(); copyDownloadLink(item)
        }
        popupView.findViewById<View>(R.id.menu_download_copy_filename).setOnClickListener {
            popup.dismiss(); copyFileName(item)
        }
        popupView.findViewById<View>(R.id.menu_download_copy_path).setOnClickListener {
            popup.dismiss(); copyFilePath(item)
        }
        popupView.findViewById<View>(R.id.menu_download_properties).setOnClickListener {
            popup.dismiss(); showDownloadProperties(item)
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

    private fun shareFile(item: DownloadItem) {
        val uri = when {
            item.file != null -> FileProvider.getUriForFile(this, "$packageName.fileprovider", item.file!!)
            item.contentUri != null -> Uri.parse(item.contentUri)
            else -> return
        }
        val ext = item.filename.substringAfterLast('.').lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, item.filename))
        } catch (_: Exception) {}
    }

    private fun openFolder(item: DownloadItem) {
        when {
            item.file != null -> {
                val parent = item.file!!.parentFile ?: return
                val standardDownloads = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val isStandardDownloads = try {
                    parent.canonicalPath == standardDownloads.canonicalPath
                } catch (_: Exception) { false }

                if (isStandardDownloads) {
                    try {
                        startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
                        return
                    } catch (_: Exception) {}
                }

                val externalRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
                val relative = parent.absolutePath.removePrefix(externalRoot).trimStart('/')
                val docUri = Uri.parse(
                    "content://com.android.externalstorage.documents/document/primary:" +
                        Uri.encode(relative)
                )
                try {
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(docUri, "vnd.android.document/directory")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
                    } catch (_: Exception) {}
                }
            }
            item.contentUri != null -> {
                val treeUri = Uri.parse(item.contentUri)
                try {
                    val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(docUri, "vnd.android.document/directory")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
                    } catch (_: Exception) {}
                }
            }
            else -> return
        }
    }

    private fun redownload(item: DownloadItem) {
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.redownload_confirm_title))
            .setMessage(getString(R.string.redownload_confirm_message))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.redownload_confirm_action)) { _, _ ->
                ClintDownloadManager.remove(this, item.id, true)
                lastRefreshMs = 0L
                ClintDownloadManager.enqueue(
                    this, item.url, item.filename, item.userAgent,
                    item.referer, item.cookies,
                    retryEnabled = item.retryEnabled,
                    unmeteredOnly = item.unmeteredOnly,
                    splitParts = item.splitParts,
                    multithreadingParts = item.multithreadingParts,
                    locationMode = item.locationMode,
                    customLocationUri = item.customLocationUri
                )
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun removeDownload(item: DownloadItem) {
        val checkboxView = layoutInflater.inflate(R.layout.dialog_download_delete_checkbox, null)
        checkboxView.findViewById<TextView>(R.id.tv_delete_message).text =
            getString(R.string.downloads_delete_confirm_message, 1)
        val cbDeleteFromStorage = checkboxView.findViewById<android.widget.CheckBox>(R.id.cb_delete_from_storage)
        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.downloads_delete_confirm_title))
            .setView(checkboxView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                ClintDownloadManager.remove(this, item.id, cbDeleteFromStorage.isChecked)
                lastRefreshMs = 0L
                refresh()
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun copyDownloadLink(item: DownloadItem) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.download_dialog_link_clip_label), item.url))
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            ClintToast.show(this, getString(R.string.download_menu_link_copied), R.drawable.ic_copy_24)
        }
    }

    private fun copyFileName(item: DownloadItem) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.download_menu_copy_filename), item.filename))
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            ClintToast.show(this, getString(R.string.download_menu_filename_copied), R.drawable.ic_copy_24)
        }
    }

    private fun copyFilePath(item: DownloadItem) {
        val path = when {
            item.file != null -> item.file!!.absolutePath
            item.contentUri != null -> {
                val uri = Uri.parse(item.contentUri)
                val segment = uri.lastPathSegment ?: item.contentUri!!
                when {
                    segment.startsWith("primary:") -> "/storage/emulated/0/${segment.removePrefix("primary:")}"
                    segment.contains(":") -> {
                        val parts = segment.split(":", limit = 2)
                        "/storage/${parts[0]}/${parts[1]}"
                    }
                    else -> item.contentUri!!
                }
            }
            else -> return
        }
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.download_menu_copy_path), path))
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            ClintToast.show(this, getString(R.string.download_menu_path_copied), R.drawable.ic_copy_24)
        }
    }

    private fun openFile(item: DownloadItem) {
        val ext = item.filename.substringAfterLast('.').lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val uri = when {
            item.file != null -> FileProvider.getUriForFile(this, "$packageName.fileprovider", item.file!!)
            item.contentUri != null -> Uri.parse(item.contentUri)
            else -> return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {}
    }

    private fun showDownloadProperties(item: DownloadItem) {
        val view = layoutInflater.inflate(R.layout.dialog_download_properties, null)

        val resolvedPath = when {
            item.contentUri != null -> {
                val uri = Uri.parse(item.contentUri)
                val seg = uri.lastPathSegment ?: item.contentUri!!
                when {
                    seg.startsWith("primary:") -> "/storage/emulated/0/${seg.removePrefix("primary:")}"
                    seg.contains(":") -> { val p = seg.split(":", limit = 2); "/storage/${p[0]}/${p[1]}" }
                    else -> item.contentUri!!
                }
            }
            item.locationMode == com.jhaiian.clint.settings.fragments.DownloadSettingsFragment.MODE_CUSTOM -> {
                val treeUri = DownloadFileHelper.getSafTreeUri(this, item)
                if (treeUri != null) {
                    val docId = try {
                        android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                    } catch (_: Throwable) { null }
                    when {
                        docId != null && docId.startsWith("primary:") ->
                            "/storage/emulated/0/${docId.removePrefix("primary:")}/${item.filename}"
                        docId != null && docId.contains(":") -> {
                            val p = docId.split(":", limit = 2)
                            "/storage/${p[0]}/${p[1]}/${item.filename}"
                        }
                        else -> treeUri.toString()
                    }
                } else {
                    item.file?.absolutePath ?: getString(R.string.download_props_dash)
                }
            }
            item.file != null -> item.file!!.absolutePath
            else -> getString(R.string.download_props_dash)
        }

        val totalBytesStr = if (item.totalBytes > 0)
            getString(R.string.download_props_size_format, propFormatBytes(item.totalBytes), item.totalBytes)
        else getString(R.string.download_props_dash)

        val downloadedStr = if (item.bytesDownloaded > 0)
            getString(R.string.download_props_size_format, propFormatBytes(item.bytesDownloaded), item.bytesDownloaded)
        else getString(R.string.download_props_dash)

        val currentInProgressMs = if (item.activeStartedAt > 0L) System.currentTimeMillis() - item.activeStartedAt else 0L
        val totalActiveElapsedMs = item.activeElapsedMs + currentInProgressMs
        val activeElapsedSec = totalActiveElapsedMs / 1000L
        val activeTimeStr = if (activeElapsedSec > 0) propFormatElapsed(activeElapsedSec)
        else getString(R.string.download_props_dash)

        val avgSpeedStr = if (totalActiveElapsedMs > 0 && item.bytesDownloaded > 0) {
            val bps = item.bytesDownloaded * 1000L / totalActiveElapsedMs
            propFormatSpeed(bps)
        } else getString(R.string.download_props_dash)

        val dateAddedStr = if (item.startedAt > 0L) propFormatTimestamp(item.startedAt)
        else getString(R.string.download_props_dash)

        val dateCompletedStr = if (item.completedAt > 0L) propFormatTimestamp(item.completedAt)
        else getString(R.string.download_props_dash)

        view.findViewById<android.widget.TextView>(R.id.tv_prop_filename).text = item.filename
        view.findViewById<android.widget.TextView>(R.id.tv_prop_path).text = resolvedPath
        view.findViewById<android.widget.TextView>(R.id.tv_prop_size).text = totalBytesStr
        view.findViewById<android.widget.TextView>(R.id.tv_prop_downloaded).text = downloadedStr
        view.findViewById<android.widget.TextView>(R.id.tv_prop_url).text = item.url
        val pageStr = item.referer.ifEmpty { getString(R.string.download_props_dash) }
        view.findViewById<android.widget.TextView>(R.id.tv_prop_page).text = pageStr
        val dividerPage = view.findViewById<android.view.View>(R.id.divider_prop_page)
        val rowPage = view.findViewById<android.view.View>(R.id.row_prop_page)
        if (item.referer.isEmpty()) {
            dividerPage.visibility = android.view.View.GONE
            rowPage.visibility = android.view.View.GONE
        }
        view.findViewById<android.widget.TextView>(R.id.tv_prop_speed).text = avgSpeedStr
        view.findViewById<android.widget.TextView>(R.id.tv_prop_active_time).text = activeTimeStr
        view.findViewById<android.widget.TextView>(R.id.tv_prop_date_added).text = dateAddedStr
        view.findViewById<android.widget.TextView>(R.id.tv_prop_date_completed).text = dateCompletedStr
        view.findViewById<android.widget.TextView>(R.id.tv_prop_unmetered).text =
            if (item.unmeteredOnly) getString(R.string.download_props_yes) else getString(R.string.download_props_no)
        view.findViewById<android.widget.TextView>(R.id.tv_prop_resumable).text =
            if (item.resumable) getString(R.string.download_props_yes) else getString(R.string.download_props_no)

        fun copyToClipboard(value: String) {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("", value))
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                ClintToast.show(this, getString(R.string.download_props_copied), R.drawable.ic_copy_24)
            }
        }

        view.findViewById<android.view.View>(R.id.row_prop_filename).setOnClickListener { copyToClipboard(item.filename) }
        view.findViewById<android.view.View>(R.id.row_prop_path).setOnClickListener { copyToClipboard(resolvedPath) }
        view.findViewById<android.view.View>(R.id.row_prop_size).setOnClickListener { copyToClipboard(totalBytesStr) }
        view.findViewById<android.view.View>(R.id.row_prop_downloaded).setOnClickListener { copyToClipboard(downloadedStr) }
        view.findViewById<android.view.View>(R.id.row_prop_url).setOnClickListener { copyToClipboard(item.url) }
        if (item.referer.isNotEmpty()) {
            view.findViewById<android.view.View>(R.id.row_prop_page).setOnClickListener { copyToClipboard(item.referer) }
        }
        view.findViewById<android.view.View>(R.id.row_prop_speed).setOnClickListener { copyToClipboard(avgSpeedStr) }
        view.findViewById<android.view.View>(R.id.row_prop_active_time).setOnClickListener { copyToClipboard(activeTimeStr) }
        view.findViewById<android.view.View>(R.id.row_prop_date_added).setOnClickListener { copyToClipboard(dateAddedStr) }
        view.findViewById<android.view.View>(R.id.row_prop_date_completed).setOnClickListener { copyToClipboard(dateCompletedStr) }

        val tvMd5 = view.findViewById<android.widget.TextView>(R.id.tv_prop_md5)
        val tvSha256 = view.findViewById<android.widget.TextView>(R.id.tv_prop_sha256)
        val btnMd5 = view.findViewById<android.widget.Button>(R.id.btn_compute_md5)
        val btnSha256 = view.findViewById<android.widget.Button>(R.id.btn_compute_sha256)
        val rowMd5 = view.findViewById<android.view.View>(R.id.row_prop_md5)
        val rowSha256 = view.findViewById<android.view.View>(R.id.row_prop_sha256)
        val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())

        val canCompute = item.file != null && item.file!!.exists()
        if (!canCompute) {
            val noFileStr = getString(R.string.download_props_checksum_na_no_file)
            tvMd5.text = noFileStr
            tvSha256.text = noFileStr
            btnMd5.isEnabled = false
            btnSha256.isEnabled = false
        }

        btnMd5.setOnClickListener {
            if (!canCompute) return@setOnClickListener
            btnMd5.isEnabled = false
            btnMd5.text = getString(R.string.download_props_computing)
            Thread {
                val hash = runCatching { propComputeHash(item.file!!, "MD5") }.getOrElse { null }
                uiHandler.post {
                    if (hash != null) {
                        tvMd5.text = hash
                        btnMd5.visibility = android.view.View.GONE
                        rowMd5.setOnClickListener { copyToClipboard(hash) }
                    } else {
                        tvMd5.text = getString(R.string.download_props_checksum_na)
                        btnMd5.isEnabled = true
                        btnMd5.text = getString(R.string.download_props_compute)
                    }
                }
            }.start()
        }

        btnSha256.setOnClickListener {
            if (!canCompute) return@setOnClickListener
            btnSha256.isEnabled = false
            btnSha256.text = getString(R.string.download_props_computing)
            Thread {
                val hash = runCatching { propComputeHash(item.file!!, "SHA-256") }.getOrElse { null }
                uiHandler.post {
                    if (hash != null) {
                        tvSha256.text = hash
                        btnSha256.visibility = android.view.View.GONE
                        rowSha256.setOnClickListener { copyToClipboard(hash) }
                    } else {
                        tvSha256.text = getString(R.string.download_props_checksum_na)
                        btnSha256.isEnabled = true
                        btnSha256.text = getString(R.string.download_props_compute)
                    }
                }
            }.start()
        }

        val isComplete = item.status == DownloadStatus.COMPLETE

        MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.download_props_title))
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(getString(R.string.download_menu_share)) { _, _ -> shareFile(item) }
            .apply {
                if (isComplete) setPositiveButton(getString(R.string.action_open)) { _, _ -> handleOpenItem(item) }
            }
            .create().also { applyStatusBarFlagToDialog(it) }.show()
    }

    private fun openBrowserForRefreshLink(item: DownloadItem) {
        val intent = Intent(this, com.jhaiian.clint.browser.MainActivity::class.java).apply {
            putExtra(com.jhaiian.clint.browser.MainActivity.EXTRA_REFRESH_LINK_MODE, true)
            putExtra(com.jhaiian.clint.browser.MainActivity.EXTRA_REFRESH_LINK_DOWNLOAD_ID, item.id)
            putExtra(com.jhaiian.clint.browser.MainActivity.EXTRA_REFRESH_LINK_FILENAME, item.filename)
            putExtra(com.jhaiian.clint.browser.MainActivity.EXTRA_REFRESH_LINK_ORIGINAL_URL, item.url)
            putExtra(com.jhaiian.clint.browser.MainActivity.EXTRA_REFRESH_LINK_ORIGINAL_REFERER, item.referer)
        }
        startActivity(intent)
    }

    private fun showUpdateDownloadLinkDialog(item: DownloadItem) {
        val view = layoutInflater.inflate(R.layout.dialog_download_update_link, null)
        val til = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_update_link)
        val et = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_update_link)
        val pb = view.findViewById<android.widget.ProgressBar>(R.id.pb_update_link)
        et.setText(item.url)
        et.setSelection(et.text?.length ?: 0)
        val dialog = MaterialAlertDialogBuilder(this, getDialogTheme())
            .setTitle(getString(R.string.download_update_link_dialog_title))
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.download_update_link_dialog_positive), null)
            .create()
            .also { applyStatusBarFlagToDialog(it) }
        var fetchRunnable: Runnable? = null
        var verifiedUrl: String? = null
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                fetchRunnable?.let { handler.removeCallbacks(it) }
                verifiedUrl = null
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                val typed = s?.toString()?.trim() ?: ""
                if (typed.isEmpty()) {
                    til.error = null
                    til.helperText = null
                    pb.visibility = android.view.View.GONE
                    return
                }
                til.error = null
                til.helperText = null
                pb.visibility = android.view.View.VISIBLE
                val r = Runnable {
                    ClintDownloadManager.executor.submit {
                        try {
                            var remoteSize = -1L
                            val headRequest = okhttp3.Request.Builder().url(typed).head().build()
                            val headResponse = ClintDownloadManager.httpClient.newCall(headRequest).execute()
                            remoteSize = headResponse.header("Content-Length")?.toLongOrNull() ?: -1L
                            headResponse.close()
                            if (remoteSize < 0) {
                                val rangeRequest = okhttp3.Request.Builder().url(typed).get()
                                    .header("Range", "bytes=0-0").build()
                                val rangeResponse = ClintDownloadManager.httpClient.newCall(rangeRequest).execute()
                                val contentRange = rangeResponse.header("Content-Range")
                                if (contentRange != null) {
                                    remoteSize = contentRange.substringAfterLast("/").trim().toLongOrNull() ?: -1L
                                }
                                if (remoteSize < 0) {
                                    remoteSize = rangeResponse.header("Content-Length")?.toLongOrNull() ?: -1L
                                }
                                rangeResponse.body?.close()
                                rangeResponse.close()
                            }
                            handler.post {
                                pb.visibility = android.view.View.GONE
                                when {
                                    remoteSize < 0 -> {
                                        til.error = null
                                        til.helperText = getString(R.string.download_update_link_dialog_size_unverifiable)
                                        verifiedUrl = typed
                                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                                    }
                                    item.totalBytes <= 0 || remoteSize == item.totalBytes -> {
                                        til.error = null
                                        til.helperText = null
                                        verifiedUrl = typed
                                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                                    }
                                    else -> {
                                        til.helperText = null
                                        til.error = getString(R.string.download_update_link_dialog_size_mismatch, remoteSize, item.totalBytes)
                                        verifiedUrl = null
                                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            handler.post {
                                pb.visibility = android.view.View.GONE
                                til.helperText = null
                                til.error = getString(R.string.download_update_link_dialog_fetch_failed)
                                verifiedUrl = null
                                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                            }
                        }
                    }
                }
                fetchRunnable = r
                handler.postDelayed(r, 600)
            }
        }
        dialog.setOnShowListener {
            val btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btn.isEnabled = false
            btn.setOnClickListener {
                val url = verifiedUrl ?: return@setOnClickListener
                ClintDownloadManager.updateDownloadUrl(item.id, url)
                dialog.dismiss()
            }
            et.addTextChangedListener(textWatcher)
        }
        dialog.show()
        et.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun propComputeHash(file: java.io.File, algorithm: String): String {
        val digest = java.security.MessageDigest.getInstance(algorithm)
        java.io.FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun propFormatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun propFormatSpeed(bps: Long): String = when {
        bps >= 1_048_576 -> "%.2f MB/s".format(bps / 1_048_576.0)
        bps >= 1024 -> "%.2f KB/s".format(bps / 1024.0)
        else -> "$bps B/s"
    }

    private fun propFormatElapsed(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

    private fun propFormatTimestamp(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy  h:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }
}
