package com.jhaiian.clint.history

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
import com.jhaiian.clint.ui.FaviconCache
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryAdapter(
    private val items: MutableList<HistoryItem>,
    private val onOpen: (HistoryItem) -> Unit,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>(), HistoryFastScroller.SectionIndexer {
    override fun getSectionLetter(position: Int): String {
        if (position !in items.indices) return "#"
        val item = items[position]
        val display = item.title.ifBlank { item.query }.trimStart()
        val first = display.firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else if (first.isDigit()) first.toString() else "#"
    }

    override fun getSectionItemCount() = items.size

    private val allItems = mutableListOf<HistoryItem>()
    private var filterQuery = ""
    private val selectedPositions = mutableSetOf<Int>()

    init {
        allItems.addAll(items)
    }

    var isInSelectionMode = false
        private set

    val selectedCount get() = selectedPositions.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconContainer: FrameLayout = view.findViewById(R.id.history_icon_container)
        val icon: ImageView = view.findViewById(R.id.history_icon)
        val title: TextView = view.findViewById(R.id.history_title)
        val url: TextView = view.findViewById(R.id.history_url)
        val timestamp: TextView = view.findViewById(R.id.history_timestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isSelected = selectedPositions.contains(position)
        val isUrl = item.query.startsWith("http")

        val iconTint = ColorStateList.valueOf(
            MaterialColors.getColor(holder.icon, R.attr.clintIconTint)
        )

        if (isUrl) {
            holder.title.text = item.title.ifBlank { item.query }
            holder.url.text = item.query.removePrefix("https://").removePrefix("http://")
            holder.url.visibility = View.VISIBLE
            holder.icon.setImageResource(R.drawable.ic_globe_24)
            ImageViewCompat.setImageTintList(holder.icon, iconTint)
            val faviconUrl = FaviconCache.faviconUrlFor(item.query)
            if (faviconUrl.isNotEmpty()) {
                FaviconCache.load(holder.itemView.context, faviconUrl, cacheOnly = false) { bmp ->
                    if (holder.bindingAdapterPosition == position && bmp != null) {
                        holder.icon.setImageBitmap(bmp)
                        ImageViewCompat.setImageTintList(holder.icon, null)
                    }
                }
            }
        } else {
            holder.title.text = item.query
            holder.url.visibility = View.GONE
            holder.icon.setImageResource(R.drawable.ic_search_24)
            ImageViewCompat.setImageTintList(holder.icon, iconTint)
        }

        if (item.timestamp > 0L) {
            holder.timestamp.text = formatTimestamp(item.timestamp)
            holder.timestamp.visibility = View.VISIBLE
        } else {
            holder.timestamp.visibility = View.GONE
        }

        applyCardBackground(holder, isSelected)
        applyIconContainerBackground(holder)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
            if (isInSelectionMode) {
                toggleSelection(pos)
            } else {
                onOpen(item)
            }
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnLongClickListener true
            if (!isInSelectionMode) {
                isInSelectionMode = true
            }
            if (!selectedPositions.contains(pos)) {
                selectedPositions.add(pos)
                notifyItemChanged(pos)
            }
            onSelectionChanged(selectedPositions.size)
            true
        }
    }

    private fun formatTimestamp(millis: Long): String {
        val itemCal = Calendar.getInstance().apply { timeInMillis = millis }
        val nowCal = Calendar.getInstance()

        val isSameDay = itemCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                itemCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = itemCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                itemCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

        val timePart = SimpleDateFormat("h:mm a", Locale.getDefault()).format(itemCal.time)

        return when {
            isSameDay -> timePart
            isYesterday -> "Yesterday, $timePart"
            itemCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) ->
                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(itemCal.time)
            else ->
                SimpleDateFormat("MMM d yyyy, h:mm a", Locale.getDefault()).format(itemCal.time)
        }
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
        for (i in items.indices) selectedPositions.add(i)
        notifyItemRangeChanged(0, items.size)
        onSelectionChanged(selectedPositions.size)
    }

    fun invertSelection() {
        val newSelection = mutableSetOf<Int>()
        for (i in items.indices) {
            if (!selectedPositions.contains(i)) newSelection.add(i)
        }
        selectedPositions.clear()
        selectedPositions.addAll(newSelection)
        notifyItemRangeChanged(0, items.size)
        onSelectionChanged(selectedPositions.size)
    }

    fun deselectAll() {
        selectedPositions.clear()
        notifyItemRangeChanged(0, items.size)
        onSelectionChanged(0)
    }

    fun exitSelectionMode() {
        isInSelectionMode = false
        selectedPositions.clear()
        notifyItemRangeChanged(0, items.size)
        onSelectionChanged(0)
    }

    fun getSelectedItems(): List<HistoryItem> {
        return selectedPositions.sorted().map { items[it] }
    }

    fun removeSelectedItems() {
        val sortedDesc = selectedPositions.sortedDescending()
        for (pos in sortedDesc) {
            items.removeAt(pos)
        }
        selectedPositions.clear()
        isInSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun updateItems(newItems: MutableList<HistoryItem>) {
        allItems.clear()
        allItems.addAll(newItems)
        selectedPositions.clear()
        isInSelectionMode = false
        applyFilter()
    }

    fun setFilter(query: String) {
        filterQuery = query
        selectedPositions.clear()
        isInSelectionMode = false
        applyFilter()
    }

    private fun applyFilter() {
        items.clear()
        if (filterQuery.isBlank()) {
            items.addAll(allItems)
        } else {
            val q = filterQuery.trim().lowercase()
            allItems.filterTo(items) {
                it.title.lowercase().contains(q) || it.query.lowercase().contains(q)
            }
        }
        notifyDataSetChanged()
    }

    fun removeAt(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun clear() {
        val size = items.size
        items.clear()
        selectedPositions.clear()
        isInSelectionMode = false
        notifyItemRangeRemoved(0, size)
    }
}
