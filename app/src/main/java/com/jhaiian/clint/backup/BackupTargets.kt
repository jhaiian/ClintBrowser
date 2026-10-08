package com.jhaiian.clint.backup

import android.content.Context
import com.jhaiian.clint.blocker.WebsiteBlockerCategoryDatabase
import com.jhaiian.clint.blocker.additional.AdditionalWebsitesDatabase
import com.jhaiian.clint.blocker.engine.WebsiteBlockerPaths
import com.jhaiian.clint.bookmarks.BookmarkDatabase
import com.jhaiian.clint.browser.home.homepageImageDir
import com.jhaiian.clint.downloads.DownloadDatabase
import com.jhaiian.clint.history.SearchHistoryDatabase
import com.jhaiian.clint.profiles.ProfileDatabase
import com.jhaiian.clint.quiver.FilterListDatabase
import com.jhaiian.clint.quiver.ManualFilterDatabase
import com.jhaiian.clint.quiver.engine.QuiverGuardPaths
import com.jhaiian.clint.settings.sitepermissions.SitePermissionDatabase
import com.jhaiian.clint.shortcuts.ShortcutDatabase
import com.jhaiian.clint.shortcuts.ShortcutIconStore
import com.jhaiian.clint.shortcuts.ShortcutTabDatabase
import com.jhaiian.clint.tabs.TabDatabase
import com.jhaiian.clint.tabs.TabThumbnailCache
import com.jhaiian.clint.userscripts.UserScriptDatabase
import java.io.File

enum class BackupEntryType { DATABASE, PREFS, DIRECTORY }

data class BackupEntryTarget(
    val id: String,
    val category: BackupCategory,
    val type: BackupEntryType,
    val zipPath: String,
    val file: (Context) -> File
)

object BackupTargets {

    const val LEGACY_DOWNLOAD_PREFS_NAME = "clint_downloads_prefs"
    const val LEGACY_BOOKMARK_PREFS_NAME = "clint_bookmarks"
    const val UPDATE_PREFS_NAME = "update_prefs"
    const val USER_SCRIPT_GM_PREFS_NAME = "user_script_gm_values"
    const val PROFILE_COOKIES_ID_PREFIX = "cookies_profile:"
    const val PROFILE_COOKIES_ZIP_PREFIX = "profiles/cookies/"
    private val SAFE_PROFILE_DIR = Regex("[A-Za-z0-9_.\\- ]{1,64}")
    const val QUIVER_GUARD_FILES_DIR_NAME = "quiver_guard"

    private fun Context.prefsFile(name: String): File = File(applicationInfo.dataDir, "shared_prefs/$name.xml")

    private fun defaultPrefsFile(context: Context): File =
        context.prefsFile("${context.packageName}_preferences")

    private fun webviewCookiesFile(context: Context): File? {
        val dataDir = context.applicationInfo.dataDir
        val candidates = listOf(
            File(dataDir, "app_webview/Default/Cookies"),
            File(dataDir, "app_webview/Cookies")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0 }
    }

    val ALL: List<BackupEntryTarget> = listOf(
        BackupEntryTarget("settings_default_prefs", BackupCategory.SETTINGS, BackupEntryType.PREFS, "settings/default_prefs.xml") { defaultPrefsFile(it) },
        BackupEntryTarget("settings_homepage_image", BackupCategory.SETTINGS, BackupEntryType.DIRECTORY, "settings/homepage_image") { homepageImageDir(it) },
        BackupEntryTarget("settings_profiles_db", BackupCategory.PROFILES, BackupEntryType.DATABASE, "profiles/${ProfileDatabase.DB_NAME}") { it.getDatabasePath(ProfileDatabase.DB_NAME) },

        BackupEntryTarget("tabs_db", BackupCategory.TABS, BackupEntryType.DATABASE, "tabs/${TabDatabase.DB_NAME}") { it.getDatabasePath(TabDatabase.DB_NAME) },
        BackupEntryTarget("tabs_thumbnails", BackupCategory.TABS, BackupEntryType.DIRECTORY, "tabs/thumbnails") { File(it.filesDir, TabThumbnailCache.DIR_NAME) },

        BackupEntryTarget("downloads_db", BackupCategory.DOWNLOADS, BackupEntryType.DATABASE, "downloads/${DownloadDatabase.DB_NAME}") { it.getDatabasePath(DownloadDatabase.DB_NAME) },
        BackupEntryTarget("downloads_legacy_prefs", BackupCategory.DOWNLOADS, BackupEntryType.PREFS, "downloads/legacy_prefs.xml") { it.prefsFile(LEGACY_DOWNLOAD_PREFS_NAME) },

        BackupEntryTarget("userscripts_db", BackupCategory.USER_SCRIPTS, BackupEntryType.DATABASE, "userscripts/${UserScriptDatabase.DB_NAME}") { it.getDatabasePath(UserScriptDatabase.DB_NAME) },
        BackupEntryTarget("userscripts_gm_values_prefs", BackupCategory.USER_SCRIPTS, BackupEntryType.PREFS, "userscripts/gm_values.xml") { it.prefsFile(USER_SCRIPT_GM_PREFS_NAME) },

        BackupEntryTarget("website_blocker_categories_db", BackupCategory.WEBSITE_BLOCKER, BackupEntryType.DATABASE, "website_blocker/${WebsiteBlockerCategoryDatabase.DB_NAME}") { it.getDatabasePath(WebsiteBlockerCategoryDatabase.DB_NAME) },
        BackupEntryTarget("website_blocker_additional_websites_db", BackupCategory.WEBSITE_BLOCKER, BackupEntryType.DATABASE, "website_blocker/${AdditionalWebsitesDatabase.DB_NAME}") { it.getDatabasePath(AdditionalWebsitesDatabase.DB_NAME) },
        BackupEntryTarget("website_blocker_category_files", BackupCategory.WEBSITE_BLOCKER, BackupEntryType.DIRECTORY, "website_blocker/categories") { WebsiteBlockerPaths.categoriesDir(it) },
        BackupEntryTarget("website_blocker_compiled_engine", BackupCategory.WEBSITE_BLOCKER, BackupEntryType.DATABASE, "website_blocker/engine.dat") { WebsiteBlockerPaths.engineFile(it) },
        BackupEntryTarget("website_blocker_compiled_manifest", BackupCategory.WEBSITE_BLOCKER, BackupEntryType.DATABASE, "website_blocker/manifest.json") { WebsiteBlockerPaths.manifestFile(it) },

        BackupEntryTarget("quiver_guard_filter_lists_db", BackupCategory.QUIVER_GUARD, BackupEntryType.DATABASE, "quiver_guard/${FilterListDatabase.DB_NAME}") { it.getDatabasePath(FilterListDatabase.DB_NAME) },
        BackupEntryTarget("quiver_guard_manual_filter_db", BackupCategory.QUIVER_GUARD, BackupEntryType.DATABASE, "quiver_guard/${ManualFilterDatabase.DB_NAME}") { it.getDatabasePath(ManualFilterDatabase.DB_NAME) },
        BackupEntryTarget("quiver_guard_files", BackupCategory.QUIVER_GUARD, BackupEntryType.DIRECTORY, "quiver_guard/files") { File(it.filesDir, QUIVER_GUARD_FILES_DIR_NAME) },
        BackupEntryTarget("quiver_guard_compiled_db", BackupCategory.QUIVER_GUARD, BackupEntryType.DATABASE, "quiver_guard/${QuiverGuardPaths.DATABASE_FILE_NAME}") { QuiverGuardPaths.databaseFile(it) },
        BackupEntryTarget("quiver_guard_compiled_manifest", BackupCategory.QUIVER_GUARD, BackupEntryType.DATABASE, "quiver_guard/${QuiverGuardPaths.MANIFEST_FILE_NAME}") { QuiverGuardPaths.manifestFile(it) },

        BackupEntryTarget("cookies_db", BackupCategory.COOKIES, BackupEntryType.DATABASE, "cookies/Cookies") { webviewCookiesFile(it) ?: File(it.applicationInfo.dataDir, "app_webview/Default/Cookies") },

        BackupEntryTarget("bookmarks_db", BackupCategory.BOOKMARKS, BackupEntryType.DATABASE, "bookmarks/${BookmarkDatabase.DB_NAME}") { it.getDatabasePath(BookmarkDatabase.DB_NAME) },
        BackupEntryTarget("bookmarks_legacy_prefs", BackupCategory.BOOKMARKS, BackupEntryType.PREFS, "bookmarks/legacy_prefs.xml") { it.prefsFile(LEGACY_BOOKMARK_PREFS_NAME) },

        BackupEntryTarget("search_history_db", BackupCategory.SEARCH_HISTORY, BackupEntryType.DATABASE, "search_history/${SearchHistoryDatabase.DB_NAME}") { it.getDatabasePath(SearchHistoryDatabase.DB_NAME) },

        BackupEntryTarget("site_permissions_db", BackupCategory.SITE_PERMISSIONS, BackupEntryType.DATABASE, "site_permissions/${SitePermissionDatabase.DB_NAME}") { it.getDatabasePath(SitePermissionDatabase.DB_NAME) },

        BackupEntryTarget("shortcuts_db", BackupCategory.SHORTCUTS, BackupEntryType.DATABASE, "shortcuts/${ShortcutDatabase.DB_NAME}") { it.getDatabasePath(ShortcutDatabase.DB_NAME) },
        BackupEntryTarget("shortcuts_tabs_db", BackupCategory.SHORTCUTS, BackupEntryType.DATABASE, "shortcuts/${ShortcutTabDatabase.DB_NAME}") { it.getDatabasePath(ShortcutTabDatabase.DB_NAME) },
        BackupEntryTarget("shortcuts_icons", BackupCategory.SHORTCUTS, BackupEntryType.DIRECTORY, "shortcuts/icons") { ShortcutIconStore.directory(it) },

        BackupEntryTarget("update_settings_prefs", BackupCategory.UPDATE_SETTINGS, BackupEntryType.PREFS, "update_settings/prefs.xml") { it.prefsFile(UPDATE_PREFS_NAME) }
    )

    fun isSafeProfileDir(name: String): Boolean =
        name != "Default" && name != "." && name != ".." && SAFE_PROFILE_DIR.matches(name)

    fun profileCookieFile(context: Context, dirName: String): File =
        File(context.applicationInfo.dataDir, "app_webview/$dirName/Cookies")

    fun profileCookieFiles(context: Context): List<Pair<String, File>> {
        val root = File(context.applicationInfo.dataDir, "app_webview")
        val dirs = root.listFiles()?.filter { it.isDirectory && isSafeProfileDir(it.name) } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val file = File(dir, "Cookies")
            if (file.exists() && file.length() > 0) dir.name to file else null
        }
    }

    fun forCategory(category: BackupCategory): List<BackupEntryTarget> = ALL.filter { it.category == category }

    fun byId(id: String): BackupEntryTarget? = ALL.firstOrNull { it.id == id }

    fun quiverGuardCompiledArtifacts(context: Context): List<File> = listOf(
        QuiverGuardPaths.databaseFile(context),
        QuiverGuardPaths.tempDatabaseFile(context),
        QuiverGuardPaths.manifestFile(context)
    )

    fun websiteBlockerCompiledArtifacts(context: Context): List<File> = listOf(
        WebsiteBlockerPaths.engineFile(context),
        WebsiteBlockerPaths.engineTempFile(context),
        WebsiteBlockerPaths.manifestFile(context)
    )
}
