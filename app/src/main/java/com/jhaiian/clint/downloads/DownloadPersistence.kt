package com.jhaiian.clint.downloads

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.io.File

internal object DownloadPersistence {

    private const val LEGACY_PREFS_NAME = "clint_downloads_prefs"
    private const val LEGACY_KEY_DOWNLOADS = "saved_downloads"

    @Volatile private var db: DownloadDatabase? = null

    fun db(context: Context): DownloadDatabase {
        return db ?: synchronized(this) {
            db ?: DownloadDatabase(context.applicationContext).also {
                db = it
                migrateLegacyPrefs(context, it.writableDatabase)
            }
        }
    }

    private fun migrateLegacyPrefs(context: Context, writable: SQLiteDatabase) {
        val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(LEGACY_KEY_DOWNLOADS, null) ?: return
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val status = obj.getString("status")
                if (status == DownloadStatus.DOWNLOADING.name) continue
                val values = ContentValues().apply {
                    put(DownloadDatabase.COL_ID, obj.getInt("id"))
                    put(DownloadDatabase.COL_URL, obj.getString("url"))
                    put(DownloadDatabase.COL_FILENAME, obj.getString("filename"))
                    put(DownloadDatabase.COL_USER_AGENT, obj.optString("userAgent", ""))
                    put(DownloadDatabase.COL_REFERER, obj.optString("referer", ""))
                    put(DownloadDatabase.COL_COOKIES, obj.optString("cookies", ""))
                    put(DownloadDatabase.COL_BYTES_DOWNLOADED, obj.optLong("bytesDownloaded", 0L))
                    put(DownloadDatabase.COL_TOTAL_BYTES, obj.optLong("totalBytes", -1L))
                    put(DownloadDatabase.COL_STATUS, status)
                    val filePath = obj.optString("filePath", "")
                    if (filePath.isNotEmpty()) put(DownloadDatabase.COL_FILE_PATH, filePath)
                    val errorMsg = obj.optString("errorMessage", "")
                    if (errorMsg.isNotEmpty()) put(DownloadDatabase.COL_ERROR_MESSAGE, errorMsg)
                    put(DownloadDatabase.COL_RESUMABLE, 0)
                }
                writable.insertWithOnConflict(
                    DownloadDatabase.TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE
                )
            }
        }
        prefs.edit().remove(LEGACY_KEY_DOWNLOADS).apply()
    }

    fun loadDownloads(context: Context): List<DownloadItem> {
        val cursor = db(context).readableDatabase.query(
            DownloadDatabase.TABLE,
            null,
            null, null, null, null,
            "${DownloadDatabase.COL_ID} DESC"
        )
        val loaded = mutableListOf<DownloadItem>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getInt(it.getColumnIndexOrThrow(DownloadDatabase.COL_ID))
                val statusStr = it.getString(it.getColumnIndexOrThrow(DownloadDatabase.COL_STATUS))
                var status = runCatching { DownloadStatus.valueOf(statusStr) }
                    .getOrDefault(DownloadStatus.FAILED)
                if (status == DownloadStatus.MOVING
                    || status == DownloadStatus.CONNECTING
                    || status == DownloadStatus.RETRYING
                    || status == DownloadStatus.ALLOCATING
                    || status == DownloadStatus.DOWNLOADING
                ) status = DownloadStatus.FAILED
                val filePath = it.getString(it.getColumnIndexOrThrow(DownloadDatabase.COL_FILE_PATH))
                val resumableIdx = it.getColumnIndex(DownloadDatabase.COL_RESUMABLE)
                val resumable = if (resumableIdx >= 0) it.getInt(resumableIdx) != 0 else false
                val contentUriIdx = it.getColumnIndex(DownloadDatabase.COL_CONTENT_URI)
                val contentUri = if (contentUriIdx >= 0) it.getString(contentUriIdx) else null
                val retryEnabledIdx = it.getColumnIndex(DownloadDatabase.COL_RETRY_ENABLED)
                val retryEnabled = if (retryEnabledIdx >= 0) it.getInt(retryEnabledIdx) != 0 else true
                val unmeteredOnlyIdx = it.getColumnIndex(DownloadDatabase.COL_UNMETERED_ONLY)
                val unmeteredOnly = if (unmeteredOnlyIdx >= 0) it.getInt(unmeteredOnlyIdx) != 0 else false
                val splitPartsIdx = it.getColumnIndex(DownloadDatabase.COL_SPLIT_PARTS)
                val splitParts = if (splitPartsIdx >= 0) it.getInt(splitPartsIdx) else 32
                val multithreadingIdx = it.getColumnIndex(DownloadDatabase.COL_MULTITHREADING)
                val multithreadingParts = if (multithreadingIdx >= 0) it.getInt(multithreadingIdx) else 4
                val locationModeIdx = it.getColumnIndex(DownloadDatabase.COL_LOCATION_MODE)
                val locationMode = if (locationModeIdx >= 0) it.getString(locationModeIdx) ?: "default" else "default"
                val customLocUriIdx = it.getColumnIndex(DownloadDatabase.COL_CUSTOM_LOC_URI)
                val customLocationUri = if (customLocUriIdx >= 0) it.getString(customLocUriIdx) else null
                val item = DownloadItem(
                    id = id,
                    url = it.getString(it.getColumnIndexOrThrow(DownloadDatabase.COL_URL)),
                    filename = it.getString(it.getColumnIndexOrThrow(DownloadDatabase.COL_FILENAME)),
                    userAgent = it.getString(it.getColumnIndexOrThrow(DownloadDatabase.COL_USER_AGENT)),
                    referer = it.getString(it.getColumnIndexOrThrow(DownloadDatabase.COL_REFERER)),
                    cookies = it.getString(it.getColumnIndexOrThrow(DownloadDatabase.COL_COOKIES)),
                    bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadDatabase.COL_BYTES_DOWNLOADED)),
                    totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadDatabase.COL_TOTAL_BYTES)),
                    status = status,
                    file = if (filePath != null) File(filePath) else null,
                    errorMessage = it.getString(it.getColumnIndexOrThrow(DownloadDatabase.COL_ERROR_MESSAGE)),
                    startedAt = it.getLong(it.getColumnIndexOrThrow(DownloadDatabase.COL_STARTED_AT)),
                    activeElapsedMs = run {
                        val idx = it.getColumnIndex(DownloadDatabase.COL_ACTIVE_ELAPSED_MS)
                        if (idx >= 0) it.getLong(idx) else 0L
                    },
                    completedAt = run {
                        val idx = it.getColumnIndex(DownloadDatabase.COL_COMPLETED_AT)
                        if (idx >= 0) it.getLong(idx) else 0L
                    },
                    resumable = resumable,
                    contentUri = contentUri,
                    retryEnabled = retryEnabled,
                    unmeteredOnly = unmeteredOnly,
                    splitParts = splitParts,
                    multithreadingParts = multithreadingParts,
                    locationMode = locationMode,
                    customLocationUri = customLocationUri
                )
                loaded.add(item)
            }
        }
        return loaded
    }

    fun persistDownload(context: Context, item: DownloadItem, removedIds: Set<Int>) {
        if (item.id in removedIds) return
        val values = ContentValues().apply {
            put(DownloadDatabase.COL_ID, item.id)
            put(DownloadDatabase.COL_URL, item.url)
            put(DownloadDatabase.COL_FILENAME, item.filename)
            put(DownloadDatabase.COL_USER_AGENT, item.userAgent)
            put(DownloadDatabase.COL_REFERER, item.referer)
            put(DownloadDatabase.COL_COOKIES, item.cookies)
            put(DownloadDatabase.COL_BYTES_DOWNLOADED, item.bytesDownloaded)
            put(DownloadDatabase.COL_TOTAL_BYTES, item.totalBytes)
            put(DownloadDatabase.COL_STATUS, item.status.name)
            put(DownloadDatabase.COL_RESUMABLE, if (item.resumable) 1 else 0)
            if (item.file != null) put(DownloadDatabase.COL_FILE_PATH, item.file!!.absolutePath)
            else putNull(DownloadDatabase.COL_FILE_PATH)
            if (item.errorMessage != null) put(DownloadDatabase.COL_ERROR_MESSAGE, item.errorMessage)
            else putNull(DownloadDatabase.COL_ERROR_MESSAGE)
            put(DownloadDatabase.COL_STARTED_AT, item.startedAt)
            put(DownloadDatabase.COL_ACTIVE_ELAPSED_MS, item.activeElapsedMs)
            put(DownloadDatabase.COL_COMPLETED_AT, item.completedAt)
            if (item.contentUri != null) put(DownloadDatabase.COL_CONTENT_URI, item.contentUri)
            else putNull(DownloadDatabase.COL_CONTENT_URI)
            put(DownloadDatabase.COL_RETRY_ENABLED, if (item.retryEnabled) 1 else 0)
            put(DownloadDatabase.COL_UNMETERED_ONLY, if (item.unmeteredOnly) 1 else 0)
            put(DownloadDatabase.COL_SPLIT_PARTS, item.splitParts)
            put(DownloadDatabase.COL_MULTITHREADING, item.multithreadingParts)
            put(DownloadDatabase.COL_LOCATION_MODE, item.locationMode)
            if (item.customLocationUri != null) put(DownloadDatabase.COL_CUSTOM_LOC_URI, item.customLocationUri)
            else putNull(DownloadDatabase.COL_CUSTOM_LOC_URI)
        }
        db(context).writableDatabase.insertWithOnConflict(
            DownloadDatabase.TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deletePersistedDownload(context: Context, id: Int) {
        db(context).writableDatabase.delete(
            DownloadDatabase.TABLE,
            "${DownloadDatabase.COL_ID} = ?",
            arrayOf(id.toString())
        )
    }
}
