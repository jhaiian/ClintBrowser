package com.jhaiian.clint.bookmarks

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray

object BookmarkManager {

    private const val LEGACY_PREFS_NAME = "clint_bookmarks"
    private const val LEGACY_KEY = "bookmarks"

    @Volatile private var db: BookmarkDatabase? = null

    private fun db(context: Context): BookmarkDatabase {
        return db ?: synchronized(this) {
            db ?: BookmarkDatabase(context.applicationContext).also {
                db = it
                migrateLegacyPrefs(context, it.writableDatabase)
            }
        }
    }

    private fun migrateLegacyPrefs(context: Context, writable: SQLiteDatabase) {
        val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(LEGACY_KEY, null) ?: return
        runCatching {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val values = ContentValues().apply {
                    put(BookmarkDatabase.COL_URL, obj.getString("url"))
                    put(BookmarkDatabase.COL_TITLE, obj.optString("title", ""))
                    put(BookmarkDatabase.COL_FAVICON_URL, obj.optString("faviconUrl", ""))
                    put(BookmarkDatabase.COL_ADDED_AT, obj.optLong("addedAt", System.currentTimeMillis()))
                    put(BookmarkDatabase.COL_LAST_VISIT, obj.optLong("lastVisit", 0L))
                }
                writable.insertWithOnConflict(BookmarkDatabase.TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
        prefs.edit().remove(LEGACY_KEY).apply()
    }

    fun getAll(context: Context): MutableList<Bookmark> {
        val cursor = db(context).readableDatabase.query(
            BookmarkDatabase.TABLE,
            arrayOf(BookmarkDatabase.COL_URL, BookmarkDatabase.COL_TITLE,
                BookmarkDatabase.COL_FAVICON_URL, BookmarkDatabase.COL_LAST_VISIT,
                BookmarkDatabase.COL_ADDED_AT),
            null, null, null, null,
            "${BookmarkDatabase.COL_ADDED_AT} DESC"
        )
        val list = mutableListOf<Bookmark>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(Bookmark(
                    url = it.getString(0),
                    title = it.getString(1),
                    faviconUrl = it.getString(2),
                    lastVisit = it.getLong(3),
                    addedAt = it.getLong(4)
                ))
            }
        }
        return list
    }

    fun add(context: Context, bookmark: Bookmark) {
        val values = ContentValues().apply {
            put(BookmarkDatabase.COL_URL, bookmark.url)
            put(BookmarkDatabase.COL_TITLE, bookmark.title)
            put(BookmarkDatabase.COL_FAVICON_URL, bookmark.faviconUrl)
            put(BookmarkDatabase.COL_ADDED_AT, System.currentTimeMillis())
            put(BookmarkDatabase.COL_LAST_VISIT, bookmark.lastVisit)
        }
        db(context).writableDatabase.insertWithOnConflict(
            BookmarkDatabase.TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun remove(context: Context, url: String) {
        db(context).writableDatabase.delete(
            BookmarkDatabase.TABLE,
            "${BookmarkDatabase.COL_URL} = ?",
            arrayOf(url)
        )
    }

    fun updateLastVisit(context: Context, url: String) {
        val values = ContentValues().apply {
            put(BookmarkDatabase.COL_LAST_VISIT, System.currentTimeMillis())
        }
        db(context).writableDatabase.update(
            BookmarkDatabase.TABLE,
            values,
            "${BookmarkDatabase.COL_URL} = ?",
            arrayOf(url)
        )
    }

    fun search(context: Context, query: String): List<Bookmark> {
        val q = query.trim()
        if (q.isBlank()) return getAll(context)
        val cursor = db(context).readableDatabase.query(
            BookmarkDatabase.TABLE,
            arrayOf(BookmarkDatabase.COL_URL, BookmarkDatabase.COL_TITLE,
                BookmarkDatabase.COL_FAVICON_URL, BookmarkDatabase.COL_LAST_VISIT,
                BookmarkDatabase.COL_ADDED_AT),
            "${BookmarkDatabase.COL_URL} LIKE ? OR ${BookmarkDatabase.COL_TITLE} LIKE ?",
            arrayOf("%$q%", "%$q%"),
            null, null,
            "${BookmarkDatabase.COL_ADDED_AT} DESC"
        )
        val list = mutableListOf<Bookmark>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(Bookmark(
                    url = it.getString(0),
                    title = it.getString(1),
                    faviconUrl = it.getString(2),
                    lastVisit = it.getLong(3),
                    addedAt = it.getLong(4)
                ))
            }
        }
        return list
    }

    fun isBookmarked(context: Context, url: String): Boolean {
        val cursor = db(context).readableDatabase.query(
            BookmarkDatabase.TABLE,
            arrayOf(BookmarkDatabase.COL_ID),
            "${BookmarkDatabase.COL_URL} = ?",
            arrayOf(url),
            null, null, null, "1"
        )
        return cursor.use { it.moveToFirst() }
    }
}
