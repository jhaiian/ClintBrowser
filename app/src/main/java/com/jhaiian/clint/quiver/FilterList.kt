package com.jhaiian.clint.quiver

// Represents a single ad-filter list that Quiver Guard can load and compile.
// Both built-in lists (shipped with the app) and user-added custom lists share
// this model. The enabled flag reflects the desired state, not the compiled state;
// a list that is enabled but not yet compiled does not contribute to filtering.
data class FilterList(
    val id: Long,
    val name: String,
    val downloadUrl: String,
    val isEnabled: Boolean,
    // Null when the list has never been downloaded.
    val localPath: String?,
    val fileSizeBytes: Long,
    // Unix timestamp in milliseconds. Zero means the list has never been downloaded.
    val downloadedAt: Long,
    val ruleCount: Long,
    val isCustom: Boolean,
    // Unix timestamp in milliseconds set at the end of the last successful compile.
    // Zero means the list has never contributed to a compiled database.
    val compiledAt: Long = 0L,
    // HTTP ETag and Last-Modified values from the last download, used for
    // conditional requests during update checks to avoid re-downloading unchanged content.
    val etag: String? = null,
    val lastModified: String? = null
) {
    // True when the list has been downloaded at least once and a local file path exists.
    val isDownloaded: Boolean
        get() = downloadedAt > 0L && !localPath.isNullOrBlank()

    // True when the list has never been included in a compiled rule database.
    val isNeverCompiled: Boolean
        get() = compiledAt <= 0L

    // True for a list added from a local file rather than a URL. Local lists
    // have no download URL to check, so the update checker and the per-item
    // update/link actions skip them.
    val isLocal: Boolean
        get() = downloadUrl.isBlank()
}
