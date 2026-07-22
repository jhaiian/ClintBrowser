package com.jhaiian.clint.downloads

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.jhaiian.clint.BuildConfig
import com.jhaiian.clint.settings.fragments.DownloadSettingsFragment
import java.io.File

internal object DownloadFileHelper {

    private var contentInfoUtil: com.j256.simplemagic.ContentInfoUtil? = null

    fun isSafCustomMode(context: Context, item: DownloadItem? = null): Boolean {
        if (item != null) return item.locationMode == DownloadSettingsFragment.MODE_CUSTOM
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = prefs.getString(
            DownloadSettingsFragment.PREF_DOWNLOAD_LOCATION_MODE,
            DownloadSettingsFragment.MODE_DEFAULT
        )
        return mode == DownloadSettingsFragment.MODE_CUSTOM
    }

    fun getSafTreeUri(context: Context, item: DownloadItem? = null): Uri? {
        if (item != null && item.locationMode == DownloadSettingsFragment.MODE_CUSTOM) {
            val uriStr = item.customLocationUri ?: return null
            return Uri.parse(uriStr)
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val uriStr = prefs.getString(
            DownloadSettingsFragment.PREF_DOWNLOAD_CUSTOM_URI,
            null
        ) ?: return null
        return Uri.parse(uriStr)
    }

    fun tempDownloadDir(context: Context?): File {
        return context?.getExternalFilesDir("clint_downloads_temp")
            ?: File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                ".clint_temp"
            )
    }

    fun isInTempDir(context: Context, file: File?): Boolean {
        if (file == null) return false
        return file.absolutePath.startsWith(tempDownloadDir(context).absolutePath + File.separator)
    }

    /**
     * True once the OS has granted blanket filesystem access. Only reachable on the GitHub
     * flavor since the F-Droid manifest never declares MANAGE_EXTERNAL_STORAGE, and never true
     * below Android 11 since the permission does not exist there.
     */
    fun hasAllFilesAccess(): Boolean =
        !BuildConfig.IS_FDROID &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()

    /**
     * True when the OS lets this app write anywhere in shared storage without staging through
     * SAF. Android 8 and 9 predate scoped storage entirely, so a granted WRITE_EXTERNAL_STORAGE
     * is enough there regardless of flavor. Android 10 enforces scoped storage for apps
     * targeting API 30+ with no opt-out, so it always falls through to SAF. Android 11+ needs
     * [hasAllFilesAccess].
     */
    fun canWriteSharedStorageDirectly(context: Context): Boolean = when {
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> false
        else -> hasAllFilesAccess()
    }

    /**
     * Converts a SAF tree Uri from the custom folder picker back into a real filesystem path,
     * relying on the same `<volume>:<relative path>` document id convention the External
     * Storage Provider uses when issuing tree Uris. Returns null for anything that doesn't
     * match, since that convention isn't a guaranteed public contract.
     */
    fun safTreeUriToFile(uri: Uri): File? {
        val docId = uri.lastPathSegment ?: return null
        val separator = docId.indexOf(':')
        if (separator < 0) return null
        val volume = docId.substring(0, separator)
        val relativePath = docId.substring(separator + 1)
        val root = if (volume == "primary") Environment.getExternalStorageDirectory() else File("/storage/$volume")
        return File(root, relativePath)
    }

    /**
     * Resolves the user's chosen custom folder straight to a filesystem path so a download can
     * write to it directly instead of staging in the app's own storage and copying out through
     * SAF. Returns null unless the location mode is custom and this Android version and flavor
     * combination allows direct shared-storage writes; callers should fall back to the SAF
     * temp-then-copy workflow in that case.
     */
    fun resolveDirectCustomDir(context: Context, item: DownloadItem? = null): File? {
        if (!canWriteSharedStorageDirectly(context) || !isSafCustomMode(context, item)) return null
        val treeUri = getSafTreeUri(context, item) ?: return null
        return safTreeUriToFile(treeUri)
    }

    fun resolveDownloadDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        var i = 1
        while (file.exists()) { file = File(dir, "$base($i)$ext"); i++ }
        return file
    }

    fun uniqueSafName(docDir: DocumentFile, name: String): String {
        if (docDir.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        var i = 1
        var candidate = "$base($i)$ext"
        while (docDir.findFile(candidate) != null) { i++; candidate = "$base($i)$ext" }
        return candidate
    }

    fun extractFilenameFromContentDisposition(cd: String): String? {
        if (cd.isBlank()) return null
        val fnStar = Regex("""filename\*\s*=\s*(?:[^']*'[^']*')?(.+)""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(1)?.trim()?.let {
                java.net.URLDecoder.decode(it.trim('"', '\''), "UTF-8")
            }
        if (!fnStar.isNullOrBlank()) return fnStar
        val fn = Regex("""filename\s*=\s*["']?([^"';\r\n]+)["']?""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(1)?.trim()
        return fn?.ifBlank { null }
    }

    fun detectExtFromMagicBytes(b: ByteArray): String? {
        if (b.size < 4) return null

        fun at(i: Int) = b.getOrElse(i) { 0 }.toInt() and 0xFF

        if (b.size >= 8
            && at(4) == 0x66 && at(5) == 0x74 && at(6) == 0x79 && at(7) == 0x70) {
            if (b.size >= 12) {
                val brand = String(b, 8, 4, Charsets.ISO_8859_1)
                return when {
                    brand.startsWith("M4A") || brand.startsWith("m4a") -> "m4a"
                    brand.startsWith("M4V") || brand.startsWith("m4v") -> "m4v"
                    brand.startsWith("qt") || brand.startsWith("QT") -> "mov"
                    brand.startsWith("3gp") || brand.startsWith("3GP") -> "3gp"
                    brand.startsWith("3g2") || brand.startsWith("3G2") -> "3g2"
                    brand.startsWith("f4v") || brand.startsWith("F4V") -> "f4v"
                    else -> "mp4"
                }
            }
            return "mp4"
        }

        if (at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3) {
            val header = String(b, 0, minOf(b.size, 64), Charsets.ISO_8859_1)
            return if (header.contains("webm", ignoreCase = true)) "webm" else "mkv"
        }

        if (at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 && b.size >= 12) {
            return when {
                at(8) == 0x41 && at(9) == 0x56 && at(10) == 0x49 -> "avi"
                at(8) == 0x57 && at(9) == 0x41 && at(10) == 0x56 && at(11) == 0x45 -> "wav"
                at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "webp"
                else -> null
            }
        }

        if (at(0) == 0x46 && at(1) == 0x4C && at(2) == 0x56) return "flv"
        if (at(0) == 0x47 && b.size >= 189 && at(188) == 0x47) return "ts"

        if (at(0) == 0x00 && at(1) == 0x00 && at(2) == 0x01) {
            return when (at(3)) {
                0xBA -> "mpg"
                0xB3 -> "mpg"
                else -> null
            }
        }

        if (at(0) == 0x4F && at(1) == 0x67 && at(2) == 0x67 && at(3) == 0x53) return "ogg"
        if (at(0) == 0x49 && at(1) == 0x44 && at(2) == 0x33) return "mp3"

        if (at(0) == 0xFF && (at(1) and 0xE0) == 0xE0) {
            val layer = (at(1) shr 1) and 0x03
            if (layer == 1) return "mp3"
        }

        if (at(0) == 0xFF && (at(1) == 0xF1 || at(1) == 0xF9)) return "aac"
        if (at(0) == 0x66 && at(1) == 0x4C && at(2) == 0x61 && at(3) == 0x43) return "flac"
        if (at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) return "jpg"
        if (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47) return "png"
        if (at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46) return "gif"
        if (at(0) == 0x42 && at(1) == 0x4D) return "bmp"
        if (at(0) == 0x25 && at(1) == 0x50 && at(2) == 0x44 && at(3) == 0x46) return "pdf"

        if (at(0) == 0x50 && at(1) == 0x4B && at(2) == 0x03 && at(3) == 0x04) {
            return try {
                if (contentInfoUtil == null) contentInfoUtil = com.j256.simplemagic.ContentInfoUtil()
                contentInfoUtil?.findMatch(b)?.mimeType?.let { mime ->
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                } ?: "zip"
            } catch (_: Throwable) { "zip" }
        }

        if (at(0) == 0x52 && at(1) == 0x61 && at(2) == 0x72 && at(3) == 0x21) return "rar"
        if (at(0) == 0x37 && at(1) == 0x7A && at(2) == 0xBC && at(3) == 0xAF) return "7z"
        if (at(0) == 0x1F && at(1) == 0x8B) return "gz"

        return try {
            if (contentInfoUtil == null) contentInfoUtil = com.j256.simplemagic.ContentInfoUtil()
            val info = contentInfoUtil?.findMatch(b) ?: return null
            val mime = info.mimeType ?: return null
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: mime.substringAfterLast('/').takeIf { it.isNotEmpty() && !it.contains('+') }
        } catch (_: Throwable) { null }
    }
}
