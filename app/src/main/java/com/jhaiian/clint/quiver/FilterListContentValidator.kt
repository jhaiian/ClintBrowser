package com.jhaiian.clint.quiver

import java.io.File

// Validation shared by every entry point that adds a filter list from raw file
// content, currently CustomFilterListFetcher (URL downloads) and
// LocalFilterListImporter (picked local files). Keeping the checks here means a
// server response and a picked file are held to the same standard for what
// counts as a usable list.
internal object FilterListContentValidator {

    // Only the first N comment lines are scanned for header metadata to keep
    // startup analysis time bounded for large lists.
    private const val METADATA_SCAN_LINE_LIMIT = 200

    // Number of characters read from the beginning of the file to detect HTML
    // responses that indicate the source does not point to a filter list.
    private const val HTML_SNIFF_CHARS = 512

    data class FilterListAnalysis(val ruleCount: Long, val metadata: Map<String, String>)

    // Returns true if the beginning of the file looks like an HTML document.
    // Some servers return a login or error page instead of a plain-text list,
    // and some picked files may simply be the wrong file; this check catches
    // the most common cases without a full HTML parser.
    fun looksLikeHtml(file: File): Boolean {
        val sample = try {
            file.bufferedReader().use { reader ->
                val buffer = CharArray(HTML_SNIFF_CHARS)
                val read = reader.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read)
            }
        } catch (_: Exception) {
            ""
        }
        val lowered = sample.trimStart().lowercase()
        return lowered.startsWith("<!doctype html") || lowered.startsWith("<html")
    }

    // Scans the file to count actionable rules and extract metadata from the
    // comment header. The header is considered ended once a non-comment,
    // non-empty line is encountered.
    fun analyzeFile(file: File): FilterListAnalysis {
        var ruleCount = 0L
        val metadata = mutableMapOf<String, String>()
        var headerEnded = false
        var scannedHeaderLines = 0
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) continue
                if (trimmed.startsWith("!")) {
                    if (!headerEnded && scannedHeaderLines < METADATA_SCAN_LINE_LIMIT) {
                        val content = trimmed.removePrefix("!").trim()
                        val colonIndex = content.indexOf(':')
                        if (colonIndex > 0) {
                            val key = content.substring(0, colonIndex).trim()
                            val value = content.substring(colonIndex + 1).trim()
                            if (key.isNotEmpty() && value.isNotEmpty()) {
                                metadata[key] = value
                            }
                        }
                        scannedHeaderLines++
                    }
                    continue
                }
                headerEnded = true
                ruleCount++
            }
        }
        return FilterListAnalysis(ruleCount, metadata)
    }
}
