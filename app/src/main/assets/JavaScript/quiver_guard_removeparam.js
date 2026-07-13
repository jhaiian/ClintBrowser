// Invoked as (function(__r){ ... })('newUrl') by
// QuiverGuardWebIntegration.buildRemoveParamScriptForPage when a "$removeparam="
// (or similar URL-rewriting) rule changed the current page's own URL. Only cleans
// up the visible address bar via history.replaceState - the actual network request
// for this navigation was already sent with the original URL before this script
// runs, so it cannot be redirected retroactively from page JS. Subresource
// requests (images, scripts, xhr) are rewritten directly at the network layer in
// shouldInterceptRequest instead, where the actual request can still be changed.

try {
    if (__r && __r !== location.href) {
        var current = new URL(location.href);
        var target = new URL(__r, location.href);
        // Same-origin guard: this should always hold for a removeparam rewrite of
        // the current page, but history.replaceState silently no-ops cross-origin
        // in most browsers anyway - checking explicitly just makes the intent clear.
        if (target.origin === current.origin) {
            history.replaceState(history.state, '', target.href);
        }
    }
} catch (e) {}
