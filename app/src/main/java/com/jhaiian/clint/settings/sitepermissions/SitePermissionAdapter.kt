package com.jhaiian.clint.settings.sitepermissions

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.ui.FaviconCache

class SitePermissionAdapter(
    private val onSelectionChanged: (selectedCount: Int) -> Unit,
    private val stateToLabel: ((state: String, context: android.content.Context) -> Pair<String, Int>)? = null
) : RecyclerView.Adapter<SitePermissionAdapter.ViewHolder>(), SitePermissionFastScroller.SectionIndexer {

    enum class SortKey { TITLE, DATE_ADDED }
    enum class SortOrder { ASCENDING, DESCENDING }

    private val allItems: MutableList<Triple<String, String, Long>> = mutableListOf()
    private val displayedItems: MutableList<Triple<String, String, Long>> = mutableListOf()
    private val selectedPositions = mutableSetOf<Int>()
    private var filterQuery = ""

    var sortKey = SortKey.DATE_ADDED
    var sortOrder = SortOrder.DESCENDING

    var isInSelectionMode = false
        private set

    val selectedCount get() = selectedPositions.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconContainer: FrameLayout = view.findViewById(R.id.icon_container)
        val iconView: ImageView = view.findViewById(R.id.icon_view)
        val tvOrigin: TextView = view.findViewById(R.id.tv_origin)
        val tvState: TextView = view.findViewById(R.id.tv_state)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_site_permission, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (origin, state, _) = displayedItems[position]
        val context = holder.itemView.context
        val isSelected = selectedPositions.contains(position)

        holder.tvOrigin.text = origin

        val iconTint = ColorStateList.valueOf(
            MaterialColors.getColor(holder.iconView, R.attr.clintIconTint)
        )
        holder.iconView.setImageResource(R.drawable.ic_globe_24)
        ImageViewCompat.setImageTintList(holder.iconView, iconTint)

        val faviconUrl = FaviconCache.faviconUrlFor("https://$origin")
        if (faviconUrl.isNotEmpty()) {
            FaviconCache.load(context, faviconUrl, cacheOnly = false) { bmp ->
                if (holder.bindingAdapterPosition == position && bmp != null) {
                    holder.iconView.setImageBitmap(bmp)
                    ImageViewCompat.setImageTintList(holder.iconView, null)
                }
            }
        }

        if (stateToLabel != null) {
            val (label, color) = stateToLabel.invoke(state, context)
            holder.tvState.text = label
            holder.tvState.setTextColor(color)
        } else if (state == SitePermissionDatabase.STATE_ALLOW) {
            holder.tvState.text = context.getString(R.string.site_permission_state_allowed)
            val color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0)
            holder.tvState.setTextColor(color)
        } else {
            holder.tvState.text = context.getString(R.string.site_permission_state_denied)
            val color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorError, 0)
            holder.tvState.setTextColor(color)
        }

        applyIconContainerBackground(holder)
        applyCardBackground(holder, isSelected)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
            if (isInSelectionMode) toggleSelection(pos)
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnLongClickListener true
            if (!isInSelectionMode) isInSelectionMode = true
            if (!selectedPositions.contains(pos)) {
                selectedPositions.add(pos)
                notifyItemChanged(pos)
            }
            onSelectionChanged(selectedPositions.size)
            true
        }
    }

    override fun getItemCount(): Int = displayedItems.size

    override fun getSectionLetter(position: Int): String {
        if (position !in displayedItems.indices) return ""
        val origin = displayedItems[position].first
        return when (sortKey) {
            SortKey.TITLE -> origin.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            SortKey.DATE_ADDED -> "#"
        }
    }

    override fun getSectionItemCount(): Int = displayedItems.size

    fun updateItems(newItems: List<Triple<String, String, Long>>) {
        allItems.clear()
        allItems.addAll(newItems)
        selectedPositions.clear()
        isInSelectionMode = false
        applyFilterAndSort()
    }

    fun setFilter(query: String) {
        filterQuery = query
        selectedPositions.clear()
        isInSelectionMode = false
        applyFilterAndSort()
    }

    fun applySortAndRefresh() {
        selectedPositions.clear()
        isInSelectionMode = false
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        val filtered = if (filterQuery.isBlank()) {
            allItems.toMutableList()
        } else {
            val lower = filterQuery.trim().lowercase()
            allItems.filter { (origin, _, _) -> origin.lowercase().contains(lower) }.toMutableList()
        }

        val sorted = when (sortKey) {
            SortKey.TITLE -> if (sortOrder == SortOrder.ASCENDING)
                filtered.sortedBy { it.first.lowercase() }
            else
                filtered.sortedByDescending { it.first.lowercase() }
            SortKey.DATE_ADDED -> if (sortOrder == SortOrder.ASCENDING)
                filtered.sortedBy { it.third }
            else
                filtered.sortedByDescending { it.third }
        }

        displayedItems.clear()
        displayedItems.addAll(sorted)
        notifyDataSetChanged()
    }

    private fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
        onSelectionChanged(selectedPositions.size)
    }

    fun selectAll() {
        selectedPositions.clear()
        for (i in displayedItems.indices) selectedPositions.add(i)
        notifyItemRangeChanged(0, displayedItems.size)
        onSelectionChanged(selectedPositions.size)
    }

    fun invertSelection() {
        val newSelection = mutableSetOf<Int>()
        for (i in displayedItems.indices) {
            if (!selectedPositions.contains(i)) newSelection.add(i)
        }
        selectedPositions.clear()
        selectedPositions.addAll(newSelection)
        notifyItemRangeChanged(0, displayedItems.size)
        onSelectionChanged(selectedPositions.size)
    }

    fun deselectAll() {
        selectedPositions.clear()
        notifyItemRangeChanged(0, displayedItems.size)
        onSelectionChanged(0)
    }

    fun exitSelectionMode() {
        isInSelectionMode = false
        selectedPositions.clear()
        notifyItemRangeChanged(0, displayedItems.size)
        onSelectionChanged(0)
    }

    fun getSelectedOrigins(): List<String> {
        return selectedPositions.sorted().map { displayedItems[it].first }
    }

    fun removeSelectedItems() {
        val origins = getSelectedOrigins().toSet()
        allItems.removeAll { it.first in origins }
        selectedPositions.clear()
        isInSelectionMode = false
        applyFilterAndSort()
        onSelectionChanged(0)
    }

    private fun applyIconContainerBackground(holder: ViewHolder) {
        val surfaceVariant = MaterialColors.getColor(holder.itemView, R.attr.clintSurfaceVariant)
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(surfaceVariant)
        }
        holder.iconContainer.background = circle
    }

    private fun applyCardBackground(holder: ViewHolder, isSelected: Boolean) {
        val density = holder.itemView.context.resources.displayMetrics.density
        val cornerRadius = 14f * density
        val cardColor = MaterialColors.getColor(holder.itemView, R.attr.clintCardBackground)
        val primaryColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary)
        val rippleColor = ColorUtils.setAlphaComponent(primaryColor, 52)

        val bgColor = if (isSelected) {
            ColorUtils.blendARGB(cardColor, primaryColor, 0.22f)
        } else {
            cardColor
        }

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(bgColor)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(primaryColor)
        }
        holder.itemView.background = RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            bgDrawable,
            mask
        )
    }
}
