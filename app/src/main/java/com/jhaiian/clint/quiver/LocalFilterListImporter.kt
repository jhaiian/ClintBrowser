package com.jhaiian.clint.quiver

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jhaiian.clint.R
import java.io.File
import java.util.UUID

// Result of importing a user-picked file as a candidate filter list.
internal sealed class LocalFilterListImportResult {
    data class Success(
        val file: File,
        val suggestedTitle: String,
        val sizeBytes: Long,
        val ruleCount: Long
    ) : LocalFilterListImportResult()
    data class Error(val messageResId: Int) : LocalFilterListImportResult()
}

// Copies a file picked through the system file chooser into the app's cache
// directory and validates it the same way CustomFilterListFetcher validates a
// network download, so a bad local file is rejected before it ever reaches the
// title-confirmation dialog.
internal object LocalFilterListImporter {

    private fun tempFileFor(context: Context): File {
        val dir = File(context.applicationContext.cacheDir, "quiver_guard_fetch")
        dir.mkdirs()
        return File(dir, "import_${UUID.randomUUID()}.txt")
    }

    // Resolves the picked URI's display name through the content resolver, since
    // the URI's own path segment is often an opaque document ID rather than the
    // real filename. Falls back to the path segment for providers that don't
    // report OpenableColumns.DISPLAY_NAME.
    private fun displayNameFor(context: Context, uri: Uri): String? {
        val resolved = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
        return resolved ?: uri.lastPathSegment
    }

    fun import(context: Context, uri: Uri): LocalFilterListImportResult {
        val appContext = context.applicationContext
        val tempFile = tempFileFor(appContext)
        return try {
            val input = try {
                appContext.contentResolver.openInputStream(uri)
            } catch (_: Exception) {
                null
            } ?: return LocalFilterListImportResult.Error(R.string.filter_list_add_file_error_read)

            input.use { stream ->
                tempFile.outputStream().use { output -> stream.copyTo(output) }
            }

            if (tempFile.length() <= 0L || FilterListContentValidator.looksLikeHtml(tempFile)) {
                tempFile.delete()
                return LocalFilterListImportResult.Error(R.string.filter_list_add_error_invalid_format)
            }

            val analysis = FilterListContentValidator.analyzeFile(tempFile)
            if (analysis.ruleCount <= 0L) {
                tempFile.delete()
                return LocalFilterListImportResult.Error(R.string.filter_list_add_error_invalid_format)
            }

            val rawName = displayNameFor(appContext, uri).orEmpty()
            val suggestedTitle = if (rawName.contains('.')) rawName.substringBeforeLast('.') else rawName

            LocalFilterListImportResult.Success(tempFile, suggestedTitle, tempFile.length(), analysis.ruleCount)
        } catch (_: Exception) {
            tempFile.delete()
            LocalFilterListImportResult.Error(R.string.filter_list_add_file_error_read)
        }
    }
}
