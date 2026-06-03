package com.jhaiian.clint.downloads

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jhaiian.clint.R
import com.jhaiian.clint.history.HistoryFastScroller

class DownloadsTabFragment : Fragment() {

    companion object {
        private const val ARG_TAB_TYPE = "tab_type"

        fun newInstance(tabType: DownloadsTabType): DownloadsTabFragment {
            return DownloadsTabFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_TAB_TYPE, tabType)
                }
            }
        }
    }

    val tabType: DownloadsTabType by lazy {
        @Suppress("DEPRECATION")
        arguments?.getSerializable(ARG_TAB_TYPE) as DownloadsTabType
    }

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var fastScroller: HistoryFastScroller
    lateinit var adapter: DownloadsAdapter
        private set

    private var baseItems: List<DownloadItem> = emptyList()
    private var textFilter: String = ""

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (context as? DownloadsActivity)?.registerTabFragment(tabType, this)
    }

    override fun onDetach() {
        (activity as? DownloadsActivity)?.unregisterTabFragment(tabType)
        super.onDetach()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_downloads_tab, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.tab_recycler)
        emptyView = view.findViewById(R.id.tab_empty_view)
        fastScroller = view.findViewById(R.id.tab_fast_scroller)

        emptyView.setText(emptyStringRes())

        ViewCompat.setOnApplyWindowInsetsListener(recycler) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val fabExtra = (80 * resources.displayMetrics.density).toInt()
            v.setPadding(0, 0, 0, navBars.bottom + fabExtra)
            insets
        }

        val activity = requireActivity() as DownloadsActivity

        adapter = DownloadsAdapter(
            sharedSelection = activity.sharedSelection,
            onOpen = { item -> activity.handleOpenItem(item) },
            onPause = { id -> activity.lastRefreshMs = 0L; ClintDownloadManager.pause(activity, id) },
            onResume = { id -> activity.lastRefreshMs = 0L; ClintDownloadManager.resume(activity, id) },
            onRetry = { id -> activity.lastRefreshMs = 0L; ClintDownloadManager.retryFailed(activity, id) },
            onSelectionChanged = { count -> activity.onTabSelectionChanged(count) },
            onShowOptions = { item, anchor -> activity.showDownloadItemOptions(item, anchor) }
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        fastScroller.attach(recycler, adapter)

        val latestItems = activity.getItemsForTab(tabType)
        if (latestItems.isNotEmpty() || baseItems.isEmpty()) {
            baseItems = latestItems
        }
        applyFilter()
    }

    fun refreshItems(items: List<DownloadItem>) {
        baseItems = items
        if (isAdded && ::adapter.isInitialized) {
            applyFilter()
        }
    }

    fun setTextFilter(query: String) {
        textFilter = query
        if (isAdded && ::adapter.isInitialized) {
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filtered = if (textFilter.isBlank()) {
            baseItems
        } else {
            val q = textFilter.trim().lowercase()
            baseItems.filter { it.filename.lowercase().contains(q) }
        }
        adapter.refreshItems(filtered)
        fastScroller.notifyDataChanged()
        updateEmptyState()
    }

    fun syncSelectionUi() {
        if (isAdded && ::adapter.isInitialized) {
            adapter.syncSelectionUi()
        }
    }

    private fun updateEmptyState() {
        val hasItems = adapter.itemCount > 0
        emptyView.isVisible = !hasItems
        recycler.isVisible = hasItems
        fastScroller.visibility = if (hasItems) View.VISIBLE else View.GONE
    }

    fun setFastScrollerInteractive(interactive: Boolean) {
        if (::fastScroller.isInitialized) {
            fastScroller.isInteractive = interactive
        }
    }

    fun notifyFastScrollerDataChanged() {
        if (::fastScroller.isInitialized) {
            fastScroller.notifyDataChanged()
        }
    }

    val isInSelectionMode: Boolean
        get() = ::adapter.isInitialized && adapter.isInSelectionMode

    val selectedCount: Int
        get() = if (::adapter.isInitialized) adapter.selectedCount else 0

    fun selectAll() {
        if (::adapter.isInitialized) adapter.selectAll()
    }

    fun invertSelection() {
        if (::adapter.isInitialized) adapter.invertSelection()
    }

    fun deselectAll() {
        if (::adapter.isInitialized) adapter.deselectAll()
    }

    fun exitSelectionMode() {
        if (::adapter.isInitialized) adapter.exitSelectionMode()
    }

    fun getSelectedItems(): List<DownloadItem> =
        if (::adapter.isInitialized) adapter.getSelectedItems() else emptyList()

    fun getVisibleItemCount(): Int =
        if (::adapter.isInitialized) adapter.itemCount else 0

    private fun emptyStringRes(): Int = when (tabType) {
        DownloadsTabType.ALL -> R.string.downloads_empty
        DownloadsTabType.DOWNLOADING -> R.string.downloads_tab_empty_downloading
        DownloadsTabType.FINISHED -> R.string.downloads_tab_empty_finished
        DownloadsTabType.ERROR -> R.string.downloads_tab_empty_error
    }
}
