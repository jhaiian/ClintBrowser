package com.jhaiian.clint.quiver.engine

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

// Re-issues a request that would otherwise load completely unmodified, so a
// non-blocking Quiver Guard modifier ($csp=, $removeparam=) can actually take
// effect instead of doing nothing. WebResourceRequest exposes no request body,
// so this is only ever attempted for GET/HEAD; callers must leave every other
// method untouched so a POST/PUT body is never silently dropped.
//
// Note: adblock-rust's public API surfaces URL rewriting (removeparam) and CSP
// directives, but - unlike the engine this replaced - does not expose
// $removeheader=/$header=/$cookie= as distinct queryable results, so those
// modifiers are not applied here even if present in a filter list.
object PassthroughFetcher {

    private val client = OkHttpClient()

    // Host is derived by OkHttp from the URL, and OkHttp negotiates its own
    // compression; forwarding these verbatim can produce a mismatched request or
    // a response body that ends up decoded twice.
    private val SKIPPED_REQUEST_HEADERS = setOf("host", "accept-encoding", "content-length")

    data class Modification(
        // Non-null when a "$removeparam=" rule changed the query string; the
        // caller fetches this URL instead of the request's original one.
        val newUrl: String? = null,
        val addResponseHeaders: Map<String, String> = emptyMap(),
    ) {
        val isNoOp: Boolean
            get() = newUrl == null && addResponseHeaders.isEmpty()
    }

    // Performs the fetch and rebuilds a WebResourceResponse with the requested
    // modifications applied. Returns null on any failure, an unsupported method,
    // or a no-op modification, so the caller can fall back to letting the
    // WebView load the request normally instead of breaking it.
    fun fetch(request: WebResourceRequest, modification: Modification): WebResourceResponse? {
        if (modification.isNoOp) return null
        val method = request.method?.uppercase() ?: "GET"
        if (method != "GET" && method != "HEAD") return null

        return try {
            val urlToFetch = modification.newUrl ?: request.url.toString()
            val reqBuilder = Request.Builder().url(urlToFetch)
            if (method == "HEAD") reqBuilder.head() else reqBuilder.get()
            request.requestHeaders?.forEach { (name, value) ->
                if (name.lowercase() !in SKIPPED_REQUEST_HEADERS) {
                    reqBuilder.header(name, value)
                }
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body ?: return null
                val bytes = body.bytes()

                val contentType = resp.header("Content-Type")
                val mimeType = contentType?.substringBefore(";")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "application/octet-stream"
                val encoding = contentType?.substringAfter("charset=", "")?.trim()?.takeIf { it.isNotEmpty() }

                val headers = LinkedHashMap<String, String>()
                for (name in resp.headers.names()) {
                    if (name.equals("Set-Cookie", ignoreCase = true)) continue
                    resp.header(name)?.let { headers[name] = it }
                }

                // WebResourceResponse takes a Map<String, String>, which - unlike a
                // real HTTP response - cannot hold two headers with the same name.
                val setCookies = resp.headers.values("Set-Cookie")
                if (setCookies.isNotEmpty()) {
                    headers["Set-Cookie"] = setCookies.joinToString(", ")
                }

                modification.addResponseHeaders.forEach { (k, v) -> headers[k] = v }

                WebResourceResponse(
                    mimeType,
                    encoding,
                    resp.code,
                    resp.message.ifEmpty { "OK" },
                    headers,
                    bytes.inputStream()
                )
            }
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
