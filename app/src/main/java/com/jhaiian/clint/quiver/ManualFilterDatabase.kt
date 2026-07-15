package com.jhaiian.clint.quiver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

// Persists the user's manually authored filter rules. Kept separate from FilterListDatabase
// since a manual rule has no download URL, ETag, or file-size metadata to track, only its text
// and when it was added.
internal class ManualFilterDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE (
                $COL_ID         INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_RULE_TEXT  TEXT    NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No schema changes yet. Future versions add one guarded ALTER TABLE block per
        // version bump here, the same way FilterListDatabase does.
    }

    fun getAllRules(): List<ManualFilterRule> {
        val cursor = readableDatabase.query(
            TABLE, null, null, null, null, null, "$COL_CREATED_AT ASC, $COL_ID ASC"
        )
        val result = mutableListOf<ManualFilterRule>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(
                    ManualFilterRule(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        ruleText = it.getString(it.getColumnIndexOrThrow(COL_RULE_TEXT)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COL_CREATED_AT))
                    )
                )
            }
        }
        return result
    }

    fun ruleTextExists(text: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE, arrayOf(COL_ID), "$COL_RULE_TEXT = ?", arrayOf(text), null, null, null
        )
        return cursor.use { it.count > 0 }
    }

    // Inserts every non-blank, non-duplicate line from a bulk paste in one transaction. Lines
    // already present in the table, or repeated within the same paste, are skipped so pasting
    // the same block twice is harmless instead of creating duplicate rows. Returns how many
    // rows were actually inserted.
    fun addRules(texts: List<String>): Int {
        if (texts.isEmpty()) return 0
        val db = writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            val seenThisBatch = mutableSetOf<String>()
            for (text in texts) {
                if (!seenThisBatch.add(text)) continue
                if (ruleTextExists(text)) continue
                val cv = ContentValues()
                cv.put(COL_RULE_TEXT, text)
                cv.put(COL_CREATED_AT, System.currentTimeMillis())
                if (db.insert(TABLE, null, cv) > 0) inserted++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun updateRuleText(id: Long, text: String) {
        val cv = ContentValues()
        cv.put(COL_RULE_TEXT, text)
        writableDatabase.update(TABLE, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun deleteRule(id: Long) {
        writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
    }

    companion object {
        const val DB_NAME = "quiver_guard_manual_filter.db"
        const val DB_VERSION = 1
        const val TABLE = "manual_filter_rules"
        const val COL_ID = "id"
        const val COL_RULE_TEXT = "rule_text"
        const val COL_CREATED_AT = "created_at"

        private const val RULES_DIR_NAME = "quiver_guard"
        private const val RULES_FILE_NAME = "manual_filter_rules.txt"

        // On-disk mirror of the rule set, one rule per line, handed to QuiverGuardCompiler the
        // same way a downloaded filter list's local file is. This is what lets manual rules
        // compile through the exact same native parsing path with no JNI changes.
        fun rulesFile(context: Context): File =
            File(File(context.applicationContext.filesDir, RULES_DIR_NAME), RULES_FILE_NAME)

        fun writeRulesFile(context: Context, rules: List<ManualFilterRule>) {
            val target = rulesFile(context)
            target.parentFile?.mkdirs()
            target.writeText(rules.joinToString("\n") { it.ruleText })
        }
    }
}
