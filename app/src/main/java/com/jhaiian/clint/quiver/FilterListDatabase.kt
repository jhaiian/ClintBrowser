package com.jhaiian.clint.quiver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Persists filter list metadata across app restarts. This database stores only
// descriptive information about each list (name, URL, enabled state, download
// timestamps, etc.); the actual rule content is kept in a separate text file on
// disk managed by FilterListDownloader.
internal class FilterListDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE (
                $COL_ID           INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME         TEXT    NOT NULL,
                $COL_DOWNLOAD_URL TEXT    NOT NULL,
                $COL_ENABLED      INTEGER NOT NULL DEFAULT 0,
                $COL_FILE_PATH    TEXT,
                $COL_FILE_SIZE    INTEGER NOT NULL DEFAULT -1,
                $COL_DOWNLOADED_AT INTEGER NOT NULL DEFAULT 0,
                $COL_RULE_COUNT   INTEGER NOT NULL DEFAULT -1,
                $COL_IS_CUSTOM    INTEGER NOT NULL DEFAULT 0,
                $COL_COMPILED_AT  INTEGER NOT NULL DEFAULT 0,
                $COL_ETAG         TEXT,
                $COL_LAST_MODIFIED TEXT
            )"""
        )
        // Seed the table with the curated default lists. They start disabled and
        // are activated only after the user explicitly enables and compiles them.
        for ((name, url) in DEFAULT_FILTER_LISTS) {
            val cv = ContentValues()
            cv.put(COL_NAME, name)
            cv.put(COL_DOWNLOAD_URL, url)
            cv.put(COL_ENABLED, 0)
            cv.put(COL_IS_CUSTOM, 0)
            cv.put(COL_COMPILED_AT, 0)
            db.insert(TABLE, null, cv)
        }
    }

    // Migrations are additive column additions so existing data is preserved.
    // Each version guard is a separate if-block rather than fall-through cases
    // so a user upgrading multiple versions in one step applies every patch.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_IS_CUSTOM INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_COMPILED_AT INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 4) {
            // Insert new default lists added in this version. The existence check
            // prevents duplicates when upgrading from a version that already has them.
            for ((name, url) in NEW_IN_V4_FILTER_LISTS) {
                val exists = db.query(TABLE, arrayOf(COL_ID), "$COL_DOWNLOAD_URL = ?", arrayOf(url), null, null, null).use { it.count > 0 }
                if (!exists) {
                    val cv = ContentValues()
                    cv.put(COL_NAME, name)
                    cv.put(COL_DOWNLOAD_URL, url)
                    cv.put(COL_ENABLED, 0)
                    cv.put(COL_IS_CUSTOM, 0)
                    cv.put(COL_COMPILED_AT, 0)
                    db.insert(TABLE, null, cv)
                }
            }
        }
        if (oldVersion < 5) {
            // ETag and Last-Modified columns enable conditional HTTP requests during
            // update checks, reducing unnecessary downloads.
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_ETAG TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_LAST_MODIFIED TEXT DEFAULT NULL")
        }
    }

    fun getAllFilterLists(): List<FilterList> {
        val db = readableDatabase
        val cursor = db.query(TABLE, null, null, null, null, null, "$COL_ID ASC")
        val result = mutableListOf<FilterList>()
        while (cursor.moveToNext()) {
            // ETag and Last-Modified columns were added in version 5. Checking the
            // column index guards against reads on older schema versions.
            val etagIdx = cursor.getColumnIndex(COL_ETAG)
            val lastModifiedIdx = cursor.getColumnIndex(COL_LAST_MODIFIED)
            result.add(
                FilterList(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                    downloadUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOWNLOAD_URL)),
                    isEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ENABLED)) == 1,
                    localPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_PATH)),
                    fileSizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FILE_SIZE)),
                    downloadedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DOWNLOADED_AT)),
                    ruleCount = cursor.getLong(cursor.getColumnIndexOrThrow(COL_RULE_COUNT)),
                    isCustom = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_CUSTOM)) == 1,
                    compiledAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_COMPILED_AT)),
                    etag = if (etagIdx >= 0) cursor.getString(etagIdx) else null,
                    lastModified = if (lastModifiedIdx >= 0) cursor.getString(lastModifiedIdx) else null
                )
            )
        }
        cursor.close()
        return result
    }

    // Records metadata produced by a completed download. The ETag and Last-Modified
    // values are stored for use in future conditional requests.
    fun updateDownloadResult(
        id: Long,
        filePath: String,
        fileSizeBytes: Long,
        downloadedAt: Long,
        ruleCount: Long,
        etag: String? = null,
        lastModified: String? = null
    ) {
        val db = writableDatabase
        val cv = ContentValues()
        cv.put(COL_FILE_PATH, filePath)
        cv.put(COL_FILE_SIZE, fileSizeBytes)
        cv.put(COL_DOWNLOADED_AT, downloadedAt)
        cv.put(COL_RULE_COUNT, ruleCount)
        cv.put(COL_ETAG, etag)
        cv.put(COL_LAST_MODIFIED, lastModified)
        db.update(TABLE, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun updateEnabled(id: Long, enabled: Boolean) {
        val db = writableDatabase
        val cv = ContentValues()
        cv.put(COL_ENABLED, if (enabled) 1 else 0)
        db.update(TABLE, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    // Inserts a user-provided custom list entry and returns the auto-assigned row ID.
    // The list starts disabled and is not considered for compilation until explicitly enabled.
    fun addCustomFilterList(name: String, downloadUrl: String): Long {
        val db = writableDatabase
        val cv = ContentValues()
        cv.put(COL_NAME, name)
        cv.put(COL_DOWNLOAD_URL, downloadUrl)
        cv.put(COL_ENABLED, 0)
        cv.put(COL_IS_CUSTOM, 1)
        cv.put(COL_COMPILED_AT, 0)
        return db.insert(TABLE, null, cv)
    }

    fun deleteFilterList(id: Long) {
        val db = writableDatabase
        db.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
    }

    // Deletes multiple lists in a single transaction to avoid holding the write
    // lock open for each individual delete when removing many lists at once.
    fun deleteFilterLists(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                db.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Returns the raw enabled flag from the database without applying any in-memory
    // pending overrides. Used by the dirty-state tracker to detect unsaved changes.
    fun getRawEnabled(id: Long): Boolean? {
        val db = readableDatabase
        val cursor = db.query(TABLE, arrayOf(COL_ENABLED), "$COL_ID = ?", arrayOf(id.toString()), null, null, null)
        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getInt(it.getColumnIndexOrThrow(COL_ENABLED)) == 1
        }
    }

    // Returns true when at least one filter list is enabled and has been compiled
    // into the rule database. Used at startup to decide whether filtering is active.
    fun hasActiveFilterLists(): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE,
            arrayOf(COL_ID),
            "$COL_ENABLED = 1 AND $COL_COMPILED_AT > 0",
            null, null, null, null, "1"
        )
        return cursor.use { it.count > 0 }
    }

    // Returns IDs of all lists that have never been part of a compiled database.
    // The compiler uses this to skip re-reading lists that have not changed.
    fun getNeverCompiledIds(): List<Long> {
        val db = readableDatabase
        val cursor = db.query(TABLE, arrayOf(COL_ID), "$COL_COMPILED_AT = 0", null, null, null, null)
        val result = mutableListOf<Long>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(it.getLong(it.getColumnIndexOrThrow(COL_ID)))
            }
        }
        return result
    }

    // Returns IDs of user-added custom lists that have never been compiled. These
    // are candidates for cleanup when the user discards pending changes, because a
    // never-compiled custom list has no effect on the active rule database.
    fun getNeverCompiledCustomIds(): List<Long> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE, arrayOf(COL_ID),
            "$COL_IS_CUSTOM = 1 AND $COL_COMPILED_AT = 0",
            null, null, null, null
        )
        val result = mutableListOf<Long>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(it.getLong(it.getColumnIndexOrThrow(COL_ID)))
            }
        }
        return result
    }

    // Atomically persists the enabled state and compilation timestamp for all
    // lists included in a completed compile run. Wrapping in a transaction ensures
    // the database is never left in a partially committed state if the app is
    // killed mid-write.
    fun commitCompiledState(enabledStates: Map<Long, Boolean>, compiledAtMillis: Long) {
        if (enabledStates.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((id, enabled) in enabledStates) {
                val cv = ContentValues()
                cv.put(COL_ENABLED, if (enabled) 1 else 0)
                cv.put(COL_COMPILED_AT, compiledAtMillis)
                db.update(TABLE, cv, "$COL_ID = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val DB_NAME           = "quiver_guard.db"
        const val DB_VERSION        = 5
        const val TABLE             = "filter_lists"
        const val COL_ID            = "id"
        const val COL_NAME          = "name"
        const val COL_DOWNLOAD_URL  = "download_url"
        const val COL_ENABLED       = "enabled"
        const val COL_FILE_PATH     = "file_path"
        const val COL_FILE_SIZE     = "file_size"
        const val COL_DOWNLOADED_AT = "downloaded_at"
        const val COL_RULE_COUNT    = "rule_count"
        const val COL_IS_CUSTOM     = "is_custom"
        const val COL_COMPILED_AT   = "compiled_at"
        const val COL_ETAG          = "etag"
        const val COL_LAST_MODIFIED = "last_modified"

        private const val FANBOY_ANNOYANCE_URL = "https://secure.fanboy.co.nz/fanboy-annoyance.txt"
        private const val ADGUARD_MOBILE_ADS_URL = "https://filters.adtidy.org/extension/ublock/filters/11.txt"
        private const val ADGUARD_BASE_FILTER_URL = "https://filters.adtidy.org/extension/ublock/filters/2_without_easylist.txt"
        private const val ADGUARD_ANNOYANCES_URL = "https://filters.adtidy.org/extension/ublock/filters/14.txt"

        // Lists seeded into every fresh installation. All start disabled so the
        // user consciously opts in to which lists they want before a compile runs.
        private val DEFAULT_FILTER_LISTS = listOf(
            Pair("EasyList", "https://easylist.to/easylist/easylist.txt"),
            Pair("EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt"),
            Pair("Fanboy Annoyances", FANBOY_ANNOYANCE_URL),
            Pair("AdGuard Mobile Ads", ADGUARD_MOBILE_ADS_URL),
            Pair("AdGuard Base Filter", ADGUARD_BASE_FILTER_URL),
            Pair("AdGuard Annoyances", ADGUARD_ANNOYANCES_URL)
        )

        // Lists added in schema version 4 that must be inserted during migrations
        // for users upgrading from an older install.
        private val NEW_IN_V4_FILTER_LISTS = listOf(
            Pair("AdGuard Base Filter", ADGUARD_BASE_FILTER_URL),
            Pair("AdGuard Annoyances", ADGUARD_ANNOYANCES_URL)
        )
    }
}
