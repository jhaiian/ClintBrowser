package com.jhaiian.clint.downloads

import com.jhaiian.clint.util.formatFileSize
import com.jhaiian.clint.util.measurementSystemEpoch

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.jhaiian.clint.R
import com.jhaiian.clint.history.HistoryFastScroller
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DownloadsAdapter(
    private val sharedSelection: SharedSelectionState,
    private val onOpen: (DownloadItem) -> Unit,
    private val onPause: (Int) -> Unit,
    private val onResume: (Int) -> Unit,
    private val onRetry: (Int) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onShowOptions: (DownloadItem, View) -> Unit
) : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>(), HistoryFastScroller.SectionIndexer {

    private val allItems = mutableListOf<DownloadItem>()
    private val items = mutableListOf<DownloadItem>()
    private var filterQuery = ""
    private var boundFormatEpoch = measurementSystemEpoch

    val isInSelectionMode: Boolean get() = sharedSelection.isActive
    val selectedCount: Int get() = sharedSelection.count

    private companion object {
        const val PAYLOAD_PROGRESS = "progress"
        const val PAYLOAD_SELECTION = "selection"
    }

    private class DownloadDiffCallback(
        private val oldList: List<DownloadItem>,
        private val newList: List<DownloadItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].id == newList[newItemPosition].id

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val o = oldList[oldItemPosition]
            val n = newList[newItemPosition]
            return o.status == n.status &&
                o.bytesDownloaded == n.bytesDownloaded &&
                o.totalBytes == n.totalBytes &&
                o.speedBytesPerSec == n.speedBytesPerSec &&
                o.resumable == n.resumable &&
                o.filename == n.filename &&
                o.errorMessage == n.errorMessage &&
                o.startedAt == n.startedAt &&
                o.copyProgress == n.copyProgress &&
                o.allocationProgress == n.allocationProgress &&
                o.retryDelaySec == n.retryDelaySec &&
                o.waitingForUnmetered == n.waitingForUnmetered &&
                o.waitingForNetwork == n.waitingForNetwork &&
                o.waitingForSchedule == n.waitingForSchedule &&
                o.waitingForCustomSchedule == n.waitingForCustomSchedule &&
                o.activeElapsedMs == n.activeElapsedMs
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val o = oldList[oldItemPosition]
            val n = newList[newItemPosition]
            val isActiveProgress = n.status == DownloadStatus.DOWNLOADING ||
                n.status == DownloadStatus.COPYING_TEMP ||
                n.status == DownloadStatus.DELETING_TEMP ||
                n.status == DownloadStatus.CONNECTING ||
                n.status == DownloadStatus.RETRYING ||
                n.status == DownloadStatus.ALLOCATING
            return if (o.status == n.status && isActiveProgress && o.resumable == n.resumable && o.filename == n.filename) {
                PAYLOAD_PROGRESS
            } else null
        }
    }

    override fun getSectionLetter(position: Int): String {
        if (position !in items.indices) return "#"
        val first = items[position].filename.trimStart().firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString()
        else if (first.isDigit()) first.toString()
        else "#"
    }

    override fun getSectionItemCount() = items.size

    fun refreshItems(newItems: List<DownloadItem>) {
        allItems.clear()
        allItems.addAll(newItems)
        applyFilter()
    }

    fun updateItems(newItems: List<DownloadItem>) {
        allItems.clear()
        allItems.addAll(newItems)
        applyFilter()
    }

    fun setFilter(query: String) {
        filterQuery = query
        applyFilter()
    }

    private fun applyFilter() {
        val oldItems = items.toList()
        items.clear()
        if (filterQuery.isBlank()) {
            items.addAll(allItems)
        } else {
            val q = filterQuery.trim().lowercase()
            allItems.filterTo(items) { it.filename.lowercase().contains(q) }
        }
        val epochChanged = boundFormatEpoch != measurementSystemEpoch
        boundFormatEpoch = measurementSystemEpoch
        if (epochChanged) {
            notifyDataSetChanged()
        } else {
            val diffResult = DiffUtil.calculateDiff(DownloadDiffCallback(oldItems, items))
            diffResult.dispatchUpdatesTo(this)
        }
    }

    fun syncSelectionUi() {
        notifyItemRangeChanged(0, items.size, PAYLOAD_SELECTION)
    }

    fun selectAll() {
        items.forEach { sharedSelection.ids.add(it.id) }
        if (items.isNotEmpty()) sharedSelection.isActive = true
        notifyItemRangeChanged(0, items.size, PAYLOAD_SELECTION)
        onSelectionChanged(sharedSelection.count)
    }

    fun invertSelection() {
        val toAdd = items.filter { it.id !in sharedSelection.ids }.map { it.id }
        val toRemove = items.filter { it.id in sharedSelection.ids }.map { it.id }
        sharedSelection.ids.removeAll(toRemove.toSet())
        sharedSelection.ids.addAll(toAdd)
        notifyItemRangeChanged(0, items.size, PAYLOAD_SELECTION)
        onSelectionChanged(sharedSelection.count)
    }

    fun deselectAll() {
        sharedSelection.ids.clear()
        notifyItemRangeChanged(0, items.size, PAYLOAD_SELECTION)
        onSelectionChanged(0)
    }

    fun exitSelectionMode() {
        sharedSelection.clear()
        notifyItemRangeChanged(0, items.size, PAYLOAD_SELECTION)
        onSelectionChanged(0)
    }

    fun getSelectedItems(): List<DownloadItem> =
        allItems.filter { it.id in sharedSelection.ids }

    fun removeSelectedItems() {
        allItems.removeAll { it.id in sharedSelection.ids }
        sharedSelection.clear()
        applyFilter()
        onSelectionChanged(0)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: DownloadProgressCardView = view as DownloadProgressCardView
        val typeIcon: ImageView = view.findViewById(R.id.download_type_icon)
        val filename: TextView = view.findViewById(R.id.download_filename)
        val resumableBadge: TextView = view.findViewById(R.id.download_resumable)
        val status: TextView = view.findViewById(R.id.download_status)
        val meta: TextView = view.findViewById(R.id.download_meta)
        val retryHint: TextView = view.findViewById(R.id.download_retry_hint)
        val btnPause: ImageView = view.findViewById(R.id.download_pause)
        val btnMore: ImageView = view.findViewById(R.id.download_more)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val hasProgress = payloads.any { it == PAYLOAD_PROGRESS }
        val hasSelection = payloads.any { it == PAYLOAD_SELECTION }
        if (!hasProgress && !hasSelection) {
            onBindViewHolder(holder, position)
            return
        }
        val item = items[position]
        val isSelected = item.id in sharedSelection.ids
        if (hasProgress || hasSelection) bindProgress(holder, item)
        if (hasSelection) applyCardBackground(holder, isSelected)
        if (isSelected) holder.card.clearProgress()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isSelected = item.id in sharedSelection.ids

        holder.filename.text = item.filename
        holder.typeIcon.setImageResource(fileTypeIconRes(item.filename))
        holder.retryHint.visibility = View.GONE
        bindProgress(holder, item)
        if (isSelected) holder.card.clearProgress()
        applyCardBackground(holder, isSelected)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
            val current = items[pos]
            if (sharedSelection.isActive) {
                toggleSelection(pos, current.id)
            } else {
                onOpen(current)
            }
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnLongClickListener true
            val current = items[pos]
            if (!sharedSelection.isActive) sharedSelection.isActive = true
            if (current.id !in sharedSelection.ids) {
                sharedSelection.ids.add(current.id)
                notifyItemChanged(pos, PAYLOAD_SELECTION)
            }
            onSelectionChanged(sharedSelection.count)
            true
        }
    }

    private fun bindProgress(holder: ViewHolder, item: DownloadItem) {
        val ctx = holder.itemView.context
        when (item.status) {
            DownloadStatus.QUEUED -> {
                holder.status.text = ""
                holder.card.clearProgress()
                holder.meta.text = ctx.getString(R.string.download_status_queued)
                holder.meta.visibility = View.VISIBLE
                holder.resumableBadge.visibility = View.GONE
                holder.btnPause.setImageResource(R.drawable.ic_pause_24)
                holder.btnPause.contentDescription = ctx.getString(R.string.download_pause_desc)
                holder.btnPause.visibility = View.VISIBLE
                holder.btnPause.setOnClickListener { onPause(item.id) }
                holder.btnMore.visibility = View.GONE
            }
            DownloadStatus.ALLOCATING -> {
                val pct = item.allocationProgress
                holder.status.text = if (item.totalBytes > 0) formatFileSize(item.totalBytes) else ""
                holder.meta.text = ctx.getString(R.string.download_status_allocating, pct)
                holder.meta.visibility = View.VISIBLE
                if (pct > 0) holder.card.setDownloadProgress(pct) else holder.card.setIndeterminate()
                holder.resumableBadge.visibility = View.GONE
                holder.btnPause.visibility = View.GONE
                holder.btnMore.visibility = View.GONE
            }
            DownloadStatus.CONNECTING -> {
                holder.status.text = if (item.totalBytes > 0) formatFileSize(item.totalBytes) else ""
                holder.meta.text = ctx.getString(R.string.download_status_connecting)
                holder.meta.visibility = View.VISIBLE
                holder.card.setIndeterminate()
                holder.resumableBadge.visibility = View.GONE
                holder.btnPause.setImageResource(R.drawable.ic_pause_24)
                holder.btnPause.contentDescription = ctx.getString(R.string.download_pause_desc)
                holder.btnPause.visibility = View.VISIBLE
                holder.btnPause.setOnClickListener { onPause(item.id) }
                holder.btnMore.visibility = View.GONE
            }
            DownloadStatus.RETRYING -> {
                val delaySec = item.retryDelaySec
                val pct = item.progressPercent
                holder.status.text = when {
                    item.bytesDownloaded > 0 && pct >= 0 && item.totalBytes > 0 ->
                        ctx.getString(R.string.download_status_progress, pct, formatFileSize(item.bytesDownloaded), formatFileSize(item.totalBytes))
                    item.bytesDownloaded > 0 && pct >= 0 ->
                        ctx.getString(R.string.download_status_progress_unknown_total, pct, formatFileSize(item.bytesDownloaded))
                    item.bytesDownloaded > 0 ->
                        ctx.getString(R.string.download_status_progress_indeterminate, formatFileSize(item.bytesDownloaded))
                    item.totalBytes > 0 -> formatFileSize(item.totalBytes)
                    else -> ""
                }
                holder.meta.text = if (delaySec > 0)
                    ctx.getString(R.string.download_status_retrying_in, delaySec)
                else
                    ctx.getString(R.string.download_status_retrying)
                holder.meta.visibility = View.VISIBLE
                holder.card.setIndeterminate()
                holder.resumableBadge.visibility = View.GONE
                holder.btnPause.setImageResource(R.drawable.ic_pause_24)
                holder.btnPause.contentDescription = ctx.getString(R.string.download_pause_desc)
                holder.btnPause.visibility = View.VISIBLE
                holder.btnPause.setOnClickListener { onPause(item.id) }
                holder.btnMore.visibility = View.GONE
                if (item.retryAttempt >= 5 || item.lastErrorWasServerError) {
                    holder.retryHint.text = ctx.getString(R.string.download_retry_hint)
                    holder.retryHint.visibility = View.VISIBLE
                }
            }
            DownloadStatus.DOWNLOADING -> {
                val pct = item.progressPercent
                val downloaded = formatFileSize(item.bytesDownloaded)
                holder.status.text = if (pct >= 0) {
                    if (item.totalBytes > 0)
                        ctx.getString(R.string.download_status_progress, pct, downloaded, formatFileSize(item.totalBytes))
                    else
                        ctx.getString(R.string.download_status_progress_unknown_total, pct, downloaded)
                } else {
                    ctx.getString(R.string.download_status_progress_indeterminate, downloaded)
                }
                if (pct >= 0) holder.card.setDownloadProgress(pct) else holder.card.setIndeterminate()
                val metaText = buildSpeedEtaText(ctx, item)
                if (metaText != null) {
                    holder.meta.text = metaText
                    holder.meta.visibility = View.VISIBLE
                } else {
                    holder.meta.visibility = View.GONE
                }
                bindResumableBadge(holder, item)
                holder.btnPause.setImageResource(R.drawable.ic_pause_24)
                holder.btnPause.contentDescription = ctx.getString(R.string.download_pause_desc)
                holder.btnPause.visibility = if (item.resumable) View.VISIBLE else View.GONE
                holder.btnPause.setOnClickListener { onPause(item.id) }
                holder.btnMore.visibility = View.GONE
            }
            DownloadStatus.PAUSED -> {
                val pct = item.progressPercent
                holder.status.text = if (pct >= 0) {
                    if (item.totalBytes > 0)
                        ctx.getString(R.string.download_status_progress, pct, formatFileSize(item.bytesDownloaded), formatFileSize(item.totalBytes))
                    else
                        ctx.getString(R.string.download_status_progress_unknown_total, pct, formatFileSize(item.bytesDownloaded))
                } else {
                    ctx.getString(R.string.download_status_progress_indeterminate, formatFileSize(item.bytesDownloaded))
                }
                if (pct >= 0) holder.card.setDownloadProgress(pct) else holder.card.clearProgress()
                val pausedLabel = when {
                    item.waitingForNetwork -> ctx.getString(R.string.download_paused_waiting_network)
                    item.waitingForUnmetered -> ctx.getString(R.string.download_paused_waiting_unmetered)
                    item.waitingForSchedule -> ctx.getString(R.string.download_paused_waiting_schedule)
                    item.waitingForCustomSchedule -> ctx.getString(
                        R.string.download_paused_waiting_custom_schedule,
                        formatScheduledDateTime(ctx, item.scheduledStartAtMillis)
                    )
                    else -> ctx.getString(R.string.download_paused)
                }
                val elapsedSec = item.activeElapsedMs / 1000L
                holder.meta.text = if (elapsedSec >= 1L)
                    "$pausedLabel  \u2022  ${formatElapsed(elapsedSec)}"
                else
                    pausedLabel
                holder.meta.visibility = View.VISIBLE
                bindResumableBadge(holder, item)
                if (item.waitingForUnmetered || item.waitingForNetwork || item.waitingForSchedule || item.waitingForCustomSchedule) {
                    holder.btnPause.setImageResource(R.drawable.ic_pause_24)
                    holder.btnPause.contentDescription = ctx.getString(R.string.download_pause_desc)
                    holder.btnPause.visibility = View.VISIBLE
                    holder.btnPause.setOnClickListener { onPause(item.id) }
                } else {
                    holder.btnPause.setImageResource(R.drawable.ic_play_arrow_24)
                    holder.btnPause.contentDescription = ctx.getString(R.string.download_resume_desc)
                    holder.btnPause.visibility = View.VISIBLE
                    holder.btnPause.setOnClickListener { onResume(item.id) }
                }
                holder.btnMore.visibility = View.GONE
            }
            DownloadStatus.COMPLETE -> {
                val sizeStr = formatFileSize(item.bytesDownloaded)
                holder.status.text = if (item.startedAt > 0L)
                    ctx.getString(R.string.download_info_with_time, sizeStr, formatTimestamp(item.startedAt))
                else
                    sizeStr
                holder.card.setDownloadProgress(100)
                val elapsedSec = item.activeElapsedMs / 1000L
                holder.meta.text = if (elapsedSec >= 1L)
                    "${ctx.getString(R.string.download_status_complete_label)}  \u2022  ${formatElapsed(elapsedSec)}"
                else
                    ctx.getString(R.string.download_status_complete_label)
                holder.meta.visibility = View.VISIBLE
                holder.resumableBadge.visibility = View.GONE
                holder.btnPause.visibility = View.GONE
                holder.btnMore.visibility = View.VISIBLE
                holder.btnMore.setOnClickListener { v -> onShowOptions(item, v) }
            }
            DownloadStatus.FAILED -> {
                val errorStr = item.errorMessage ?: ctx.getString(R.string.download_error_unknown)
                holder.status.text = if (item.startedAt > 0L)
                    ctx.getString(R.string.download_info_with_time, errorStr, formatTimestamp(item.startedAt))
                else
                    errorStr
                holder.card.clearProgress()
                holder.meta.text = ctx.getString(R.string.download_status_failed_label)
                holder.meta.visibility = View.VISIBLE
                holder.resumableBadge.visibility = View.GONE
                holder.btnPause.setImageResource(R.drawable.ic_play_arrow_24)
                holder.btnPause.contentDescription = ctx.getString(R.string.download_resume_desc)
                holder.btnPause.visibility = View.VISIBLE
                holder.btnPause.setOnClickListener { onRetry(item.id) }
                holder.btnMore.visibility = View.GONE
                if (item.lastErrorWasServerError) {
                    holder.retryHint.text = ctx.getString(R.string.download_retry_hint)
                    holder.retryHint.visibility = View.VISIBLE
                }
            }
            DownloadStatus.COPYING_TEMP -> {
                val pct = item.copyProgress
                holder.status.text = if (item.totalBytes > 0) formatFileSize(item.totalBytes) else ""
                holder.meta.text = ctx.getString(R.string.download_status_copying_temp, pct)
                holder.meta.visibility = View.VISIBLE
                holder.card.setDownloadProgress(pct)
                holder.resumableBadge.visibility = View.GONE
                holder.btnPause.visibility = View.GONE
                holder.btnMore.visibility = View.GONE
            }
            DownloadStatus.DELETING_TEMP -> {
                holder.status.text = if (item.totalBytes > 0) formatFileSize(item.totalBytes) else ""
                holder.meta.text = ctx.getString(R.string.download_status_deleting_temp)
                holder.meta.visibility = View.VISIBLE
                holder.card.setIndeterminate()
                holder.resumableBadge.visibility = View.GONE
                holder.btnPause.visibility = View.GONE
                holder.btnMore.visibility = View.GONE
            }
        }
    }

    private fun bindResumableBadge(holder: ViewHolder, item: DownloadItem) {
        val ctx = holder.itemView.context
        holder.resumableBadge.text = if (item.resumable)
            ctx.getString(R.string.download_resumable_yes)
        else
            ctx.getString(R.string.download_resumable_no)
        holder.resumableBadge.visibility = View.VISIBLE
    }

    private fun applyCardBackground(holder: ViewHolder, isSelected: Boolean) {
        val density = holder.itemView.context.resources.displayMetrics.density
        val cornerRadius = 14f * density
        val cardColor = MaterialColors.getColor(holder.card, R.attr.clintCardBackground)
        val primaryColor = MaterialColors.getColor(holder.card, androidx.appcompat.R.attr.colorPrimary)
        val rippleColor = ColorUtils.setAlphaComponent(primaryColor, 52)
        val bgColor = if (isSelected) ColorUtils.blendARGB(cardColor, primaryColor, 0.55f) else cardColor
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

    private fun toggleSelection(position: Int, id: Int) {
        if (id in sharedSelection.ids) sharedSelection.ids.remove(id) else sharedSelection.ids.add(id)
        notifyItemChanged(position, PAYLOAD_SELECTION)
        onSelectionChanged(sharedSelection.count)
    }

    private fun buildSpeedEtaText(ctx: android.content.Context, item: DownloadItem): String? {
        val speed = item.speedBytesPerSec
        val elapsedMs = item.activeElapsedMs + if (item.activeStartedAt > 0L) System.currentTimeMillis() - item.activeStartedAt else 0L
        val elapsedStr = if (elapsedMs >= 1000L) formatElapsed(elapsedMs / 1000L) else null
        if (speed <= 0L) return elapsedStr
        val remaining = item.totalBytes - item.bytesDownloaded
        val speedEta = if (item.totalBytes <= 0L || remaining <= 0L)
            ctx.getString(R.string.download_speed_only, formatFileSize(speed))
        else {
            // The ETA uses the average speed rather than the current reading above, since the
            // current speed alone can swing the remaining-time estimate wildly on a connection
            // that briefly speeds up or stalls.
            val etaSpeed = item.averageSpeedBytesPerSec().takeIf { it > 0L } ?: speed
            ctx.getString(R.string.download_speed_eta, formatFileSize(speed), formatEta(ctx, remaining / etaSpeed))
        }
        return if (elapsedStr != null) "$speedEta  \u2022  $elapsedStr" else speedEta
    }

    private fun formatElapsed(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

    private fun formatEta(ctx: android.content.Context, seconds: Long): String = when {
        seconds < 60 -> ctx.getString(R.string.download_eta_seconds, seconds)
        seconds < 3600 -> ctx.getString(R.string.download_eta_minutes, seconds / 60, seconds % 60)
        else -> ctx.getString(R.string.download_eta_hours, seconds / 3600, (seconds % 3600) / 60)
    }

    private fun formatTimestamp(millis: Long): String {
        val itemCal = Calendar.getInstance().apply { timeInMillis = millis }
        val nowCal = Calendar.getInstance()
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isSameDay = itemCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                itemCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
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

    private fun fileTypeIconRes(filename: String): Int {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "tiff", "tif",
            "heic", "heif", "avif", "svg", "ico", "raw", "cr2", "nef",
            "orf", "arw", "dng" -> R.drawable.ic_file_image_24
            "mp4", "mkv", "webm", "avi", "mov", "wmv", "flv", "ts",
            "m4v", "3gp", "3g2", "rmvb", "vob", "ogv", "mts", "m2ts",
            "divx", "xvid", "f4v", "asf", "mpg", "mpeg", "m2v" -> R.drawable.ic_file_video_24
            "apk", "apks", "apkm", "xapk", "apkz" -> R.drawable.ic_file_apk_24
            "mp3", "wav", "flac", "ogg", "m4a", "aac", "opus", "wma",
            "aiff", "aif", "alac", "ape", "mka", "mid", "midi",
            "amr", "caf", "dsd", "dsf", "dff", "ra", "rm" -> R.drawable.ic_file_audio_24
            "zip", "7z", "rar", "gz", "tar", "bz2", "xz", "lz4",
            "zst", "br", "cab", "iso", "tgz", "tbz2", "txz",
            "z", "lzma", "lzh", "arj", "ace", "sit" -> R.drawable.ic_file_zip_24
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "odt", "ods", "odp", "odg", "odf", "rtf", "csv",
            "txt", "md", "markdown", "log", "json", "xml", "html",
            "htm", "epub", "mobi", "azw", "azw3", "djvu", "pages",
            "numbers", "key", "tex", "srt", "vtt", "ass", "sub" -> R.drawable.ic_file_document_24
            else -> R.drawable.ic_file_other_24
        }
    }

}
