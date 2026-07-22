package com.jhaiian.clint.quiver

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.util.formatFileSize
import java.text.NumberFormat
import java.util.Date

// Rule count and enabled badge shown on the pinned Manual Filter row. FilterListAdapter owns
// this separately from FilterList since manual rules are never rows in FilterListDatabase.
data class ManualFilterSummary(val ruleCount: Int, val isEnabled: Boolean)

// RecyclerView adapter for the filter list screen. Supports:
//   - A pinned "Manual Filter" row at position 0 that opens the custom-rules screen
//   - Multi-select with long-press entry point
//   - Inline search filtering
//   - Sort by title (A–Z / Z–A) or download date
//   - Per-item downloading indicator
//   - Master-enabled dimming when Quiver Guard is toggled off globally
//   - Interaction locking during compile and update runs
//   - Per-item overflow menu (check/force update, remove, copy, share)
//
// Position 0 is always the Manual Filter row and is excluded from sorting, searching,
// multi-select, and fast-scroll section indexing. Every method below that reads or notifies a
// RecyclerView position for a FilterList works in "displayedItems index" terms and adds 1 to
// convert to the real adapter position.
class FilterListAdapter(
    private val onItemClick: (FilterList) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onShowOptions: (FilterList, View) -> Unit,
    private val onManualFilterClick: () -> Unit
) : RecyclerView.Adapter<FilterListAdapter.BaseViewHolder>(), FilterListFastScroller.SectionIndexer {

    enum class SortKey { TITLE, DATE_DOWNLOADED }
    enum class SortOrder { ASCENDING, DESCENDING }

    private companion object {
        const val VIEW_TYPE_MANUAL_FILTER = 0
        const val VIEW_TYPE_FILTER_LIST = 1
    }

    // allItems holds the full unfiltered dataset; displayedItems is the current
    // filtered+sorted view that the adapter draws from. They are kept in sync by
    // applyFilterAndSort() so filtering is always applied on top of the full set.
    private val allItems: MutableList<FilterList> = mutableListOf()
    private val displayedItems: MutableList<FilterList> = mutableListOf()

    // selectedIds and selectedPositions are kept in sync by applyFilterAndSort()
    // whenever the display set changes. Using both structures allows O(1) ID lookup
    // (selectedIds) and O(1) position binding (selectedPositions).
    private val selectedIds = mutableSetOf<Long>()
    private val selectedPositions = mutableSetOf<Int>()

    private var filterQuery = ""
    private var masterEnabled: Boolean = true
    private val downloadingIds = mutableSetOf<Long>()
    // When locked, tap and long-press gestures are swallowed so the user cannot
    // change list states while a compile or update is in progress.
    private var interactionLocked = false

    private var manualFilterSummary = ManualFilterSummary(ruleCount = 0, isEnabled = false)

    var sortKey = SortKey.TITLE
    var sortOrder = SortOrder.ASCENDING

    var isInSelectionMode = false
        private set

    val selectedCount get() = selectedIds.size

    sealed class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class FilterListViewHolder(view: View) : BaseViewHolder(view) {
        val name: TextView = view.findViewById(R.id.filter_list_name)
        val status: TextView = view.findViewById(R.id.filter_list_status)
        val switch: Switch = view.findViewById(R.id.filter_list_switch)
        val row: View = view.findViewById(R.id.filter_list_row)
        val btnMore: ImageView = view.findViewById(R.id.filter_list_more)
    }

    inner class ManualFilterViewHolder(view: View) : BaseViewHolder(view) {
        val row: View = view.findViewById(R.id.manual_filter_row)
        val status: TextView = view.findViewById(R.id.manual_filter_status)
    }

    override fun getItemViewType(position: Int): Int =
        if (position == 0) VIEW_TYPE_MANUAL_FILTER else VIEW_TYPE_FILTER_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return if (viewType == VIEW_TYPE_MANUAL_FILTER) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_manual_filter_row, parent, false)
            ManualFilterViewHolder(v)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_filter_list, parent, false)
            FilterListViewHolder(v)
        }
    }

    override fun getItemCount() = displayedItems.size + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        when (holder) {
            is ManualFilterViewHolder -> bindManualFilterRow(holder)
            is FilterListViewHolder -> bindFilterListRow(holder, position - 1)
        }
    }

    private fun bindManualFilterRow(holder: ManualFilterViewHolder) {
        val context = holder.itemView.context
        holder.status.text = when {
            manualFilterSummary.ruleCount == 0 ->
                context.getString(R.string.quiver_guard_manual_filter_status_empty)
            manualFilterSummary.isEnabled -> context.getString(
                R.string.quiver_guard_manual_filter_status_enabled,
                NumberFormat.getNumberInstance().format(manualFilterSummary.ruleCount)
            )
            else -> context.getString(
                R.string.quiver_guard_manual_filter_status_disabled,
                NumberFormat.getNumberInstance().format(manualFilterSummary.ruleCount)
            )
        }
        holder.itemView.alpha = if (masterEnabled) 1f else 0.38f
        holder.row.setOnClickListener {
            if (masterEnabled && !interactionLocked) onManualFilterClick()
        }
    }

    private fun bindFilterListRow(holder: FilterListViewHolder, displayedIndex: Int) {
        val item = displayedItems[displayedIndex]
        val isDownloading = downloadingIds.contains(item.id)
        val isSelected = selectedPositions.contains(displayedIndex)

        holder.name.text = item.name

        // Build a human-readable status line that reflects the most informative
        // state available: downloading > downloaded-with-rules > downloaded > not-downloaded.
        holder.status.text = when {
            isDownloading -> holder.itemView.context.getString(R.string.filter_list_status_downloading)
            item.isDownloaded && item.ruleCount >= 0 -> holder.itemView.context.getString(
                R.string.filter_list_status_downloaded_with_rules,
                NumberFormat.getNumberInstance().format(item.ruleCount),
                formatFileSize(item.fileSizeBytes),
                DateFormat.getMediumDateFormat(holder.itemView.context).format(Date(item.downloadedAt))
            )
            item.isDownloaded -> holder.itemView.context.getString(
                R.string.filter_list_status_downloaded,
                formatFileSize(item.fileSizeBytes),
                DateFormat.getMediumDateFormat(holder.itemView.context).format(Date(item.downloadedAt))
            )
            else -> holder.itemView.context.getString(R.string.filter_list_status_not_downloaded)
        }

        holder.switch.isChecked = item.isEnabled
        holder.switch.visibility = View.VISIBLE

        val effectivelyClickable = masterEnabled && !isDownloading && !interactionLocked
        // Dim non-interactive rows to communicate that they cannot be acted on.
        holder.row.alpha = when {
            !masterEnabled -> 0.38f
            isDownloading -> 0.6f
            else -> 1f
        }
        holder.switch.alpha = if (!masterEnabled) 0.38f else 1f

        applyRowBackground(holder, isSelected)

        holder.btnMore.visibility = View.VISIBLE
        holder.btnMore.setOnClickListener { v ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt() || pos - 1 !in displayedItems.indices) return@setOnClickListener
            onShowOptions(displayedItems[pos - 1], v)
        }

        holder.row.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
            if (interactionLocked) return@setOnClickListener
            if (isInSelectionMode) {
                toggleSelection(pos - 1)
            } else {
                if (effectivelyClickable) onItemClick(displayedItems[pos - 1])
            }
        }

        // Long-press enters selection mode and selects the pressed item immediately.
        holder.row.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnLongClickListener true
            if (interactionLocked) return@setOnLongClickListener true
            if (!isInSelectionMode) isInSelectionMode = true
            val displayedIndexAtPos = pos - 1
            val id = displayedItems[displayedIndexAtPos].id
            if (id !in selectedIds) {
                selectedIds.add(id)
                selectedPositions.add(displayedIndexAtPos)
                notifyItemChanged(pos)
            }
            onSelectionChanged(selectedIds.size)
            true
        }
    }

    // Returns the first uppercase character of the list name as the fast-scroller
    // section letter when sorted alphabetically. Falls back to '#' for non-letter
    // starts and for date-sorted lists where sections are not meaningful. Position is a
    // displayedItems index (see getAdapterPositionOffset), not a raw adapter position.
    override fun getSectionLetter(position: Int): String {
        if (position !in displayedItems.indices) return ""
        return when (sortKey) {
            SortKey.TITLE -> displayedItems[position].name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            SortKey.DATE_DOWNLOADED -> "#"
        }
    }

    override fun getSectionItemCount(): Int = displayedItems.size

    // The Manual Filter row occupies adapter position 0, so a displayedItems index must be
    // shifted by 1 to land on the matching RecyclerView position.
    override fun getAdapterPositionOffset(): Int = 1

    // Replaces the full dataset and resets all selection and search state.
    fun updateItems(newItems: List<FilterList>) {
        allItems.clear()
        allItems.addAll(newItems)
        selectedIds.clear()
        selectedPositions.clear()
        isInSelectionMode = false
        applyFilterAndSort()
    }

    // Updates the pinned row's rule count and enabled badge without touching the filter list
    // dataset or any selection, search, or sort state.
    fun setManualFilterSummary(summary: ManualFilterSummary) {
        if (manualFilterSummary == summary) return
        manualFilterSummary = summary
        notifyItemChanged(0)
    }

    fun setFilter(query: String) {
        filterQuery = query
        applyFilterAndSort()
    }

    fun applySortAndRefresh() {
        applyFilterAndSort()
    }

    // Applies the current search filter and sort parameters to allItems and
    // refreshes the display list. Selected positions are recomputed after the
    // sort so that the highlighting overlay aligns with the new item positions.
    private fun applyFilterAndSort() {
        val filtered = if (filterQuery.isBlank()) {
            allItems.toMutableList()
        } else {
            val lower = filterQuery.trim().lowercase()
            allItems.filter { it.name.lowercase().contains(lower) }.toMutableList()
        }

        val sorted = when (sortKey) {
            SortKey.TITLE -> if (sortOrder == SortOrder.ASCENDING)
                filtered.sortedBy { it.name.lowercase() }
            else
                filtered.sortedByDescending { it.name.lowercase() }
            SortKey.DATE_DOWNLOADED -> if (sortOrder == SortOrder.ASCENDING)
                filtered.sortedBy { it.downloadedAt }
            else
                filtered.sortedByDescending { it.downloadedAt }
        }

        displayedItems.clear()
        displayedItems.addAll(sorted)
        // Re-derive selectedPositions from selectedIds after the sort.
        selectedPositions.clear()
        displayedItems.forEachIndexed { i, item -> if (item.id in selectedIds) selectedPositions.add(i) }
        notifyDataSetChanged()
    }

    private fun toggleSelection(displayedIndex: Int) {
        val id = displayedItems[displayedIndex].id
        if (id in selectedIds) {
            selectedIds.remove(id)
            selectedPositions.remove(displayedIndex)
        } else {
            selectedIds.add(id)
            selectedPositions.add(displayedIndex)
        }
        notifyItemChanged(displayedIndex + 1)
        onSelectionChanged(selectedIds.size)
    }

    fun selectAll() {
        displayedItems.forEach { selectedIds.add(it.id) }
        selectedPositions.clear()
        displayedItems.indices.forEach { selectedPositions.add(it) }
        notifyItemRangeChanged(1, displayedItems.size)
        onSelectionChanged(selectedIds.size)
    }

    fun invertSelection() {
        val toAdd = displayedItems.indices.filter { displayedItems[it].id !in selectedIds }
        val toRemove = displayedItems.indices.filter { displayedItems[it].id in selectedIds }
        toRemove.forEach { selectedIds.remove(displayedItems[it].id) }
        toAdd.forEach { selectedIds.add(displayedItems[it].id) }
        selectedPositions.clear()
        displayedItems.forEachIndexed { i, item -> if (item.id in selectedIds) selectedPositions.add(i) }
        notifyItemRangeChanged(1, displayedItems.size)
        onSelectionChanged(selectedIds.size)
    }

    fun deselectAll() {
        selectedIds.clear()
        selectedPositions.clear()
        notifyItemRangeChanged(1, displayedItems.size)
        onSelectionChanged(0)
    }

    fun exitSelectionMode() {
        isInSelectionMode = false
        selectedIds.clear()
        selectedPositions.clear()
        notifyItemRangeChanged(1, displayedItems.size)
        onSelectionChanged(0)
    }

    fun getSelectedIds(): List<Long> = selectedIds.toList()

    // Returns the selected FilterList objects in the same order they currently
    // appear in the displayed (filtered and sorted) list, so callers that build
    // user-facing text from this list (e.g. clipboard or share content) match
    // the order the user sees on screen.
    fun getSelectedItems(): List<FilterList> = displayedItems.filter { it.id in selectedIds }

    // Removes selected items from both allItems and the display list in one pass
    // and exits selection mode. applyFilterAndSort rebuilds the display list so
    // position indices are consistent after the removal.
    fun removeSelectedItems() {
        allItems.removeAll { it.id in selectedIds }
        selectedIds.clear()
        selectedPositions.clear()
        isInSelectionMode = false
        applyFilterAndSort()
        onSelectionChanged(0)
    }

    // Removes a single item identified by ID, used by the per-row overflow
    // menu's Remove action. Unlike removeSelectedItems(), this does not touch
    // selection mode or fire onSelectionChanged. Note the overflow button now
    // remains active during selection mode too, so this can be reached either way.
    fun removeItem(id: Long) {
        allItems.removeAll { it.id == id }
        selectedIds.remove(id)
        applyFilterAndSort()
    }

    fun setMasterEnabled(enabled: Boolean) {
        if (masterEnabled == enabled) return
        masterEnabled = enabled
        notifyItemRangeChanged(0, displayedItems.size + 1)
    }

    fun setInteractionLocked(locked: Boolean) {
        interactionLocked = locked
    }

    fun setDownloading(filterListId: Long, downloading: Boolean) {
        val changed = if (downloading) downloadingIds.add(filterListId) else downloadingIds.remove(filterListId)
        if (!changed) return
        val index = displayedItems.indexOfFirst { it.id == filterListId }
        if (index != -1) notifyItemChanged(index + 1)
    }

    // Updates a single item in both the master and display lists without
    // rebuilding the whole dataset, to avoid unnecessary scroll-position changes.
    fun updateItem(updated: FilterList) {
        val allIndex = allItems.indexOfFirst { it.id == updated.id }
        if (allIndex != -1) allItems[allIndex] = updated
        val displayIndex = displayedItems.indexOfFirst { it.id == updated.id }
        if (displayIndex != -1) {
            displayedItems[displayIndex] = updated
            notifyItemChanged(displayIndex + 1)
        }
    }

    fun addItem(item: FilterList) {
        allItems.add(item)
        applyFilterAndSort()
    }

    // Applies a rounded-rectangle card background with a ripple overlay. Selected
    // items are tinted by blending the card colour with the primary colour.
    private fun applyRowBackground(holder: FilterListViewHolder, isSelected: Boolean) {
        val primaryColor = MaterialColors.getColor(holder.itemView, androidx.appcompat.R.attr.colorPrimary)
        val cardColor = MaterialColors.getColor(holder.itemView, R.attr.clintCardBackground)
        val rippleColor = ColorUtils.setAlphaComponent(primaryColor, 52)
        val bgColor = if (isSelected) ColorUtils.blendARGB(cardColor, primaryColor, 0.22f) else cardColor
        val radiusPx = holder.itemView.resources.displayMetrics.density * 14f
        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(bgColor)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(primaryColor)
        }
        holder.row.background = RippleDrawable(ColorStateList.valueOf(rippleColor), bgDrawable, mask)
    }
}
