package com.jhaiian.clint.quiver.engine

/**
 * Raw JNI surface backed by the `quiverguard-jni` Rust crate (see /native/quiverguard-jni),
 * which wraps the adblock-rust `Engine`. Nothing here does any decision-making itself - it's a
 * direct mirror of the native functions. [QuiverGuardEngine] is the safe, lock-guarded facade
 * consumers should use instead; [com.jhaiian.clint.quiver.QuiverGuardCompileHelper] is the only
 * other caller, since compiling a filter set doesn't touch the active engine's lock.
 *
 * Every function that returns a JSON string returns `"{\"error\":\"...\"}"` on failure rather
 * than throwing, so callers should always check for an `error` key before trusting a result.
 */
internal object QuiverGuardNative {

    init {
        System.loadLibrary("adblockrust")
    }

    // ---- FilterSet builder lifecycle (compile-time only) ----

    @JvmStatic
    external fun nativeNewFilterSetBuilder(): Long

    /** Returns JSON: {"ruleLines": Int, "commentLines": Int, "emptyLines": Int} */
    @JvmStatic
    external fun nativeAddFilterListRules(builderHandle: Long, rules: String): String

    @JvmStatic
    external fun nativeDestroyFilterSetBuilder(builderHandle: Long)

    /**
     * Consumes [builderHandle] (it must not be used again afterwards, whether this succeeds or
     * not) and writes a compiled, resource-bundled engine to [outputPath].
     * Returns JSON: {"success": Boolean, "sizeBytes": Long, "error": String?}
     */
    @JvmStatic
    external fun nativeFinalizeEngine(builderHandle: Long, outputPath: String): String

    // ---- Engine lifecycle ----

    /** Returns 0 on failure (missing/corrupt file). */
    @JvmStatic
    external fun nativeLoadEngine(path: String): Long

    @JvmStatic
    external fun nativeDestroyEngine(handle: Long)

    // ---- Queries against a loaded engine ----

    /**
     * Returns JSON: {"matched": Boolean, "important": Boolean, "exception": Boolean,
     * "redirect": String?, "rewrittenUrl": String?, "csp": String?}
     *
     * "redirect", when present, is a `data:<mime>;base64,<content>` URI - decode it directly
     * into the WebResourceResponse body rather than trying to parse it further.
     */
    @JvmStatic
    external fun nativeCheckNetworkRequest(
        handle: Long,
        url: String,
        sourceUrl: String,
        requestType: String,
        method: String,
    ): String

    /**
     * Returns JSON: {"hideSelectors": [String], "proceduralActions": [String],
     * "genericHide": Boolean, "injectedScript": String, "exceptions": [String]}
     *
     * Each entry in "proceduralActions" is itself a JSON-encoded string describing an operator
     * chain (adblock-rust's `ProceduralOrActionFilter` shape) - see quiver_guard_cosmetic.js for
     * the interpreter. "exceptions" feeds into [nativeHiddenClassIdSelectors] and should be
     * ignored otherwise.
     */
    @JvmStatic
    external fun nativeUrlCosmeticResources(handle: Long, url: String): String

    /**
     * Looks up which of the compiled engine's generic (non-hostname-specific) class/id-keyed
     * hiding rules apply to the given class/id tokens. Per adblock-rust's own docs, this is only
     * meaningful when "genericHide" from [nativeUrlCosmeticResources] was false, and only for
     * tokens actually observed in the page's DOM - see QuiverGuardWebIntegration's bridge object
     * and quiver_guard_cosmetic.js for how those get collected.
     *
     * [classesJson]/[idsJson] are JSON string arrays of class/id tokens to check; [exceptionsJson]
     * is the "exceptions" array from [nativeUrlCosmeticResources]. Returns a JSON array of
     * matching CSS selectors (possibly empty).
     */
    @JvmStatic
    external fun nativeHiddenClassIdSelectors(
        handle: Long,
        classesJson: String,
        idsJson: String,
        exceptionsJson: String,
    ): String
}
