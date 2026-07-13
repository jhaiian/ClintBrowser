// Runs as a bare, argument-less script (wrapped in an IIFE by
// QuiverGuardWebIntegration.bootstrapScript) - the same script, unmodified, for every
// page. Unlike an earlier version of this file, nothing about this script is
// precomputed per-navigation in Kotlin: it fetches its own cosmetic data for
// location.href, at the moment it actually runs, through the bridge object Kotlin
// registers on this WebView (window.__qgBridge - see QuiverGuardJsBridge). That's a
// deliberate fix for a real bug, not a stylistic choice - see the kdoc on
// QuiverGuardWebIntegration for the full explanation of the race it fixes.

var __qgBridge = window.__qgBridge;
if (__qgBridge) {

    // ---- hostname-specific hide selectors, procedural actions, scriptlets ----
    var __qgData = null;
    try {
        __qgData = JSON.parse(__qgBridge.urlCosmeticResources(location.href));
    } catch (e) {}

    if (__qgData && !__qgData.error) {
        if (__qgData.hideSelectors && __qgData.hideSelectors.length) {
            try {
                var __qgStyle = document.createElement('style');
                var __qgRules = '';
                for (var __qgI = 0; __qgI < __qgData.hideSelectors.length; __qgI++) {
                    // Individual rules, not one comma-joined selector list: a single
                    // unsupported/malformed selector in a combined list invalidates the
                    // whole rule in standard CSS, which would silently disable every
                    // other hide rule on the page.
                    __qgRules += __qgData.hideSelectors[__qgI] + '{display:none!important;}';
                }
                __qgStyle.textContent = __qgRules;
                (document.head || document.documentElement).appendChild(__qgStyle);
            } catch (e) {}
        }

        if (__qgData.injectedScript) {
            try { new Function(__qgData.injectedScript)(); } catch (e) {}
        }

        if (__qgData.proceduralActions && __qgData.proceduralActions.length) {
            try { runProceduralActions(__qgData.proceduralActions); } catch (e) {}
        }

        // Per adblock-rust's own docs: generic (non-hostname-specific) hiding rules
        // keyed on a plain class or id selector are deliberately excluded from
        // hideSelectors above for performance, and only meaningful to look up when
        // genericHide is false.
        if (!__qgData.genericHide) {
            try { runGenericHide(JSON.stringify(__qgData.exceptions || [])); } catch (e) {}
        }
    }

    // ---- address bar cleanup for a "$removeparam="-style rewrite of this page's own URL ----
    // Only cleans up the visible URL via history.replaceState - the actual network
    // request for this navigation was already sent with the original URL before this
    // script runs, so it can't be redirected retroactively from page JS. Subresource
    // requests (images, scripts, xhr) are rewritten directly at the network layer in
    // shouldInterceptRequest instead, where the actual request can still be changed.
    try {
        var __qgRewrite = JSON.parse(__qgBridge.rewrittenUrl(location.href));
        if (__qgRewrite && __qgRewrite.rewrittenUrl && __qgRewrite.rewrittenUrl !== location.href) {
            var __qgCurrent = new URL(location.href);
            var __qgTarget = new URL(__qgRewrite.rewrittenUrl, location.href);
            // Same-origin guard: should always hold for a removeparam rewrite of the
            // current page, but history.replaceState silently no-ops cross-origin in
            // most browsers anyway - checking explicitly just makes the intent clear.
            if (__qgTarget.origin === __qgCurrent.origin) {
                history.replaceState(history.state, '', __qgTarget.href);
            }
        }
    } catch (e) {}
}

// ---- procedural cosmetic filter interpreter ----
// Interprets adblock-rust's ProceduralOrActionFilter JSON shape - see
// https://github.com/uBlockOrigin/uBlock-issues/wiki/Static-filter-syntax#extended-css-selectors
// for the operator semantics. This is data, not code: adblock-rust only returns the
// parsed operator chain, not JS to run, so this interpreter is static and ships with
// the app instead of being generated per-rule.
function runProceduralActions(rawActions) {
    var rules = [];
    for (var i = 0; i < rawActions.length; i++) {
        try {
            var entry = rawActions[i];
            var rule = typeof entry === 'string' ? JSON.parse(entry) : entry;
            if (rule && Array.isArray(rule.selector) && rule.selector.length) {
                rules.push(rule);
            }
        } catch (e) {}
    }
    if (!rules.length) return;

    function parseRegexArg(arg) {
        if (typeof arg !== 'string' || arg.charAt(0) !== '/') return null;
        var lastSlash = arg.lastIndexOf('/');
        if (lastSlash <= 0) return null;
        try {
            return new RegExp(arg.slice(1, lastSlash), arg.slice(lastSlash + 1));
        } catch (e) {
            return null;
        }
    }

    function matchTextOrRegex(text, arg) {
        var re = parseRegexArg(arg);
        return re ? re.test(text) : text.indexOf(arg) !== -1;
    }

    function matchesAttr(node, arg) {
        var eq = arg.indexOf('=');
        if (eq === -1) return node.hasAttribute(arg);
        var name = arg.slice(0, eq).trim();
        var value = arg.slice(eq + 1).trim().replace(/^["']|["']$/g, '');
        return node.hasAttribute(name) && matchTextOrRegex(node.getAttribute(name) || '', value);
    }

    function matchesCss(node, pseudo, arg) {
        var colon = arg.indexOf(':');
        if (colon === -1) return false;
        var prop = arg.slice(0, colon).trim().replace(/-([a-z])/g, function (_, c) { return c.toUpperCase(); });
        var value = arg.slice(colon + 1).trim();
        try {
            var style = pseudo ? getComputedStyle(node, pseudo) : getComputedStyle(node);
            var actual = style[prop];
            return actual !== undefined && matchTextOrRegex(String(actual), value);
        } catch (e) {
            return false;
        }
    }

    function upward(node, arg) {
        var trimmed = arg.trim();
        var count = parseInt(trimmed, 10);
        if (!isNaN(count) && String(count) === trimmed) {
            var cur = node;
            for (var i = 0; i < count && cur; i++) cur = cur.parentElement;
            return cur;
        }
        return node.parentElement ? node.parentElement.closest(trimmed) : null;
    }

    function toArray(nodeList) {
        return Array.prototype.slice.call(nodeList);
    }

    // Applies one chained operator. The first operator in a rule is always a plain
    // CSS selector queried from the document; every operator after that either
    // narrows the current candidate set (has-text, matches-*, min-text-length) or
    // maps it to a different set of nodes (upward, xpath, a chained css-selector
    // scoping into each candidate).
    function applyOperator(op, nodes, isFirst) {
        var type = op.type, arg = op.arg;

        if (type === 'css-selector') {
            if (isFirst || !nodes) {
                try { return toArray(document.querySelectorAll(arg)); } catch (e) { return []; }
            }
            var scoped = [];
            nodes.forEach(function (n) {
                try {
                    if (n.querySelectorAll) scoped = scoped.concat(toArray(n.querySelectorAll(arg)));
                } catch (e) {}
            });
            return scoped;
        }

        if (type === 'xpath') {
            var out = [];
            var contexts = nodes && nodes.length ? nodes : [document];
            contexts.forEach(function (ctx) {
                try {
                    var result = document.evaluate(arg, ctx, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
                    for (var j = 0; j < result.snapshotLength; j++) out.push(result.snapshotItem(j));
                } catch (e) {}
            });
            return out;
        }

        if (!nodes) return [];
        switch (type) {
            case 'has-text':
                return nodes.filter(function (n) { return matchTextOrRegex(n.textContent || '', arg); });
            case 'matches-attr':
                return nodes.filter(function (n) { return matchesAttr(n, arg); });
            case 'matches-css':
                return nodes.filter(function (n) { return matchesCss(n, null, arg); });
            case 'matches-css-before':
                return nodes.filter(function (n) { return matchesCss(n, '::before', arg); });
            case 'matches-css-after':
                return nodes.filter(function (n) { return matchesCss(n, '::after', arg); });
            case 'matches-path':
                return matchTextOrRegex(location.pathname + location.search, arg) ? nodes : [];
            case 'min-text-length':
                var min = parseInt(arg, 10) || 0;
                return nodes.filter(function (n) { return (n.textContent || '').length >= min; });
            case 'upward':
                var mapped = [];
                nodes.forEach(function (n) {
                    var a = upward(n, arg);
                    if (a && mapped.indexOf(a) === -1) mapped.push(a);
                });
                return mapped;
            default:
                return nodes;
        }
    }

    function evaluateRule(rule) {
        var nodes = null;
        for (var i = 0; i < rule.selector.length; i++) {
            nodes = applyOperator(rule.selector[i], nodes, i === 0);
            if (!nodes.length) return [];
        }
        return nodes || [];
    }

    function applyAction(nodes, action) {
        if (!action || action.type === 'remove') {
            // No action at all also means "hide" - a bare procedural selector
            // behaves like a plain ##selector hiding rule.
            if (!action) {
                nodes.forEach(function (n) { n.style.setProperty('display', 'none', 'important'); });
            } else {
                nodes.forEach(function (n) { n.remove(); });
            }
            return;
        }
        switch (action.type) {
            case 'style':
                nodes.forEach(function (n) { n.style.cssText += ';' + action.arg; });
                break;
            case 'remove-attr':
                nodes.forEach(function (n) { n.removeAttribute(action.arg); });
                break;
            case 'remove-class':
                nodes.forEach(function (n) { n.classList.remove(action.arg); });
                break;
        }
    }

    function runOnce() {
        rules.forEach(function (rule) {
            try {
                var nodes = evaluateRule(rule);
                if (nodes.length) applyAction(nodes, rule.action);
            } catch (e) {}
        });
    }

    runOnce();

    // Ads frequently load after the initial DOM is built, so re-run on mutation.
    // Debounced to one pass per 150ms of DOM churn rather than once per mutation
    // record, since a page can emit hundreds of records per second during initial
    // render.
    if (typeof MutationObserver !== 'undefined') {
        var pending = false;
        var observer = new MutationObserver(function () {
            if (pending) return;
            pending = true;
            setTimeout(function () { pending = false; runOnce(); }, 150);
        });
        var root = document.documentElement;
        if (root) {
            observer.observe(root, { childList: true, subtree: true });
        } else {
            document.addEventListener('DOMContentLoaded', function () {
                observer.observe(document.documentElement, { childList: true, subtree: true });
            }, { once: true });
        }
    }
}

// ---- generic (class/id-keyed) cosmetic hiding ----
// Scans the live DOM for class/id tokens actually present and asks the bridge which
// of the engine's generic hiding rules match them, incrementally, as the page
// mutates - see QuiverGuardNative.nativeHiddenClassIdSelectors's kdoc for why this
// can't be precomputed the way the rest of this script now is: it depends on
// elements that don't exist yet at the time this script runs, which is exactly the
// common case for ad slots a page's own ad-loading JS creates after the fact.
function runGenericHide(exceptionsJson) {
    var seenClasses = Object.create(null);
    var seenIds = Object.create(null);
    var hideStyle = null;

    function ensureStyle() {
        if (!hideStyle) {
            hideStyle = document.createElement('style');
            (document.head || document.documentElement).appendChild(hideStyle);
        }
        return hideStyle;
    }

    function scanAndApply() {
        var newClasses = [];
        var newIds = [];
        var all = document.querySelectorAll('[class],[id]');
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            if (el.id && !seenIds[el.id]) {
                seenIds[el.id] = true;
                newIds.push(el.id);
            }
            var cn = el.className;
            if (cn && typeof cn === 'string') {
                var parts = cn.split(/\s+/);
                for (var j = 0; j < parts.length; j++) {
                    var c = parts[j];
                    if (c && !seenClasses[c]) {
                        seenClasses[c] = true;
                        newClasses.push(c);
                    }
                }
            }
        }
        if (!newClasses.length && !newIds.length) return;

        try {
            var selectorsJson = __qgBridge.hiddenClassIdSelectors(
                JSON.stringify(newClasses), JSON.stringify(newIds), exceptionsJson
            );
            var selectors = JSON.parse(selectorsJson);
            if (selectors && selectors.length) {
                var rules = '';
                for (var k = 0; k < selectors.length; k++) {
                    rules += selectors[k] + '{display:none!important}';
                }
                ensureStyle().textContent += rules;
            }
        } catch (e) {}
    }

    scanAndApply();

    if (typeof MutationObserver !== 'undefined') {
        var pending = false;
        var observer = new MutationObserver(function () {
            if (pending) return;
            pending = true;
            setTimeout(function () { pending = false; scanAndApply(); }, 150);
        });
        var observe = function () {
            observer.observe(document.documentElement, {
                childList: true, subtree: true,
                attributes: true, attributeFilter: ['class', 'id']
            });
        };
        if (document.documentElement) {
            observe();
        } else {
            document.addEventListener('DOMContentLoaded', observe, { once: true });
        }
    }
}
