package com.jhaiian.clint.bookmarks

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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.history.HistoryFastScroller
import com.jhaiian.clint.ui.FaviconCache
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BookmarksAdapter(
    private val items: MutableList<Bookmark>,
    private val onOpen: (Bookmark) -> Unit,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : RecyclerView.Adapter<BookmarksAdapter.ViewHolder>(), HistoryFastScroller.SectionIndexer {

    private val allItems = mutableListOf<Bookmark>()
    private var filterQuery = ""
    private val selectedKeys = mutableSetOf<String>()
    private val selectedPositions = mutableSetOf<Int>()

    init {
        allItems.addAll(items)
    }

    var isInSelectionMode = false
        private set

    val selectedCount get() = selectedKeys.size

    override fun getSectionLetter(position: Int): String {
        if (position !in items.indices) return "#"
        val display = items[position].title.ifBlank { items[position].url }.trimStart()
        val first = display.firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString()
        else if (first.isDigit()) first.toString()
        else "#"
    }

    override fun getSectionItemCount() = items.size

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

        val iconTint = ColorStateList.valueOf(
            MaterialColors.getColor(holder.icon, R.attr.clintIconTint)
        )

        holder.title.text = item.title.ifBlank { item.url }
        holder.url.text = item.url.removePrefix("https://").removePrefix("http://")
        holder.url.visibility = View.VISIBLE

        holder.icon.setImageResource(R.drawable.ic_globe_24)
        ImageViewCompat.setImageTintList(holder.icon, iconTint)

        val faviconUrl = item.faviconUrl.ifBlank { FaviconCache.faviconUrlFor(item.url) }
        if (faviconUrl.isNotEmpty()) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(holder.itemView.context)
            val cacheOnly = prefs.getBoolean("data_saver_enabled", false) &&
                prefs.getBoolean("data_saver_disable_images", true)
            FaviconCache.load(holder.itemView.context, faviconUrl, cacheOnly) { bmp ->
                if (holder.bindingAdapterPosition == position && bmp != null) {
                    holder.icon.setImageBitmap(bmp)
                    ImageViewCompat.setImageTintList(holder.icon, null)
                }
            }
        }

        if (item.lastVisit > 0L) {
            holder.timestamp.text = formatTimestamp(item.lastVisit)
            holder.timestamp.visibility = View.VISIBLE
        } else {
            holder.timestamp.visibility = View.GONE
        }

        applyCardBackground(holder, isSelected)
        applyIconContainerBackground(holder)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
            if (isInSelectionMode) toggleSelection(pos) else onOpen(item)
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnLongClickListener true
            if (!isInSelectionMode) isInSelectionMode = true
            val key = items[pos].url
            if (key !in selectedKeys) {
                selectedKeys.add(key)
                selectedPositions.add(pos)
                notifyItemChanged(pos)
            }
            onSelectionChanged(selectedKeys.size)
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
        holder.iconContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(surfaceVariant)
        }
    }

    private fun applyCardBackground(holder: ViewHolder, isSelected: Boolean) {
        val density = holder.itemView.context.resources.displayMetrics.density
        val cornerRadius = 14f * density
        val cardColor = MaterialColors.getColor(holder.itemView, R.attr.clintCardBackground)
        val primaryColor = MaterialColors.getColor(holder.itemView, androidx.appcompat.R.attr.colorPrimary)
        val rippleColor = ColorUtils.setAlphaComponent(primaryColor, 52)
        val bgColor = if (isSelected) ColorUtils.blendARGB(cardColor, primaryColor, 0.22f) else cardColor
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
        holder.itemView.background = RippleDrawable(ColorStateList.valueOf(rippleColor), bgDrawable, mask)
    }

    private fun toggleSelection(position: Int) {
        val key = items[position].url
        if (key in selectedKeys) {
            selectedKeys.remove(key)
            selectedPositions.remove(position)
        } else {
            selectedKeys.add(key)
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
        onSelectionChanged(selectedKeys.size)
    }

    fun selectAll() {
        items.forEach { selectedKeys.add(it.url) }
        selectedPositions.clear()
        items.indices.forEach { selectedPositions.add(it) }
        notifyItemRangeChanged(0, items.size)
        onSelectionChanged(selectedKeys.size)
    }

    fun invertSelection() {
        val toAdd = items.indices.filter { items[it].url !in selectedKeys }
        val toRemove = items.indices.filter { items[it].url in selectedKeys }
        toRemove.forEach { selectedKeys.remove(items[it].url) }
        toAdd.forEach { selectedKeys.add(items[it].url) }
        selectedPositions.clear()
        items.forEachIndexed { i, item -> if (item.url in selectedKeys) selectedPositions.add(i) }
        notifyItemRangeChanged(0, items.size)
        onSelectionChanged(selectedKeys.size)
    }

    fun deselectAll() {
        selectedKeys.clear()
        selectedPositions.clear()
        notifyItemRangeChanged(0, items.size)
        onSelectionChanged(0)
    }

    fun exitSelectionMode() {
        isInSelectionMode = false
        selectedKeys.clear()
        selectedPositions.clear()
        notifyItemRangeChanged(0, items.size)
        onSelectionChanged(0)
    }

    fun getSelectedItems(): List<Bookmark> {
        return allItems.filter { it.url in selectedKeys }
    }

    fun removeSelectedItems() {
        allItems.removeAll { it.url in selectedKeys }
        selectedKeys.clear()
        selectedPositions.clear()
        isInSelectionMode = false
        applyFilter()
        onSelectionChanged(0)
    }

    fun updateItems(newItems: MutableList<Bookmark>) {
        allItems.clear()
        allItems.addAll(newItems)
        selectedKeys.clear()
        selectedPositions.clear()
        isInSelectionMode = false
        applyFilter()
    }

    fun setFilter(query: String) {
        filterQuery = query
        applyFilter()
    }

    private fun applyFilter() {
        items.clear()
        if (filterQuery.isBlank()) {
            items.addAll(allItems)
        } else {
            val q = filterQuery.trim().lowercase()
            allItems.filterTo(items) {
                it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
            }
        }
        selectedPositions.clear()
        items.forEachIndexed { i, item -> if (item.url in selectedKeys) selectedPositions.add(i) }
        notifyDataSetChanged()
    }
}
