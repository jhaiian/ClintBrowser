package com.jhaiian.clint.quiver.engine

import android.content.Context
import java.io.File

/** File locations for the compiled engine cache. Internal storage only - never user-visible. */
object QuiverGuardPaths {

    private const val DATABASE_FILE_NAME = "quiver_guard_engine.dat"
    private const val TEMP_DATABASE_FILE_NAME = "quiver_guard_engine.tmp"
    private const val MANIFEST_FILE_NAME = "quiver_guard_manifest.json"

    /** The active, serialized adblock-rust engine, loaded on every app/WebView start. */
    fun databaseFile(context: Context): File = File(context.filesDir, DATABASE_FILE_NAME)

    /** Where a new compile writes to before being atomically promoted to [databaseFile]. */
    fun tempDatabaseFile(context: Context): File = File(context.filesDir, TEMP_DATABASE_FILE_NAME)

    /** Bookkeeping for [com.jhaiian.clint.quiver.QuiverGuardCompileHelper]'s dirty-checking. */
    fun manifestFile(context: Context): File = File(context.filesDir, MANIFEST_FILE_NAME)
}
