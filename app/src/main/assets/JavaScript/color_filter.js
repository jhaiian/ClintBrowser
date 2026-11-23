(function (cfg) {
    if (window !== window.top) return;
    var api = window.__clintCfx;
    if (!api) {
        if (!cfg || !cfg.m) return;
        var OVERLAY_ID = "__clint_cfx";
        var FILTERS = { grayscale: "grayscale(1)", sepia: "sepia(1)", black_white: "grayscale(1) contrast(1000%)" };
        var state = null;
        var key = "";
        var applied = "";
        var appliedFor = "";
        var observer = null;
        var watchedRoot = null;

        var channel = function (v) {
            var n = Number(v);
            if (!(n >= 0)) n = 0;
            if (n > 1) n = 1;
            return Math.round(n * 255);
        };

        var buildOverlay = function () {
            var t = state.t || [0.5, 0.5, 0.5];
            var el = document.createElement("div");
            el.setAttribute("id", OVERLAY_ID);
            el.setAttribute("aria-hidden", "true");
            el.setAttribute("data-k", key);
            [
                ["position", "fixed"], ["inset", "0"], ["top", "0"], ["left", "0"], ["right", "0"], ["bottom", "0"],
                ["width", "auto"], ["height", "auto"], ["margin", "0"], ["padding", "0"], ["border", "0"],
                ["z-index", "2147483647"], ["pointer-events", "none"], ["display", "block"], ["opacity", "1"],
                ["background", "rgb(" + channel(t[0]) + "," + channel(t[1]) + "," + channel(t[2]) + ")"],
                ["mix-blend-mode", "color"]
            ].forEach(function (p) { el.style.setProperty(p[0], p[1], "important"); });
            return el;
        };

        var removeOverlay = function () {
            var el = document.getElementById(OVERLAY_ID);
            if (el) el.remove();
        };

        var clearFilter = function (root) {
            if (root && applied && root.style.getPropertyValue("filter") === applied) root.style.removeProperty("filter");
            applied = "";
            appliedFor = "";
        };

        var ensure = function () {
            var root = document.documentElement;
            if (!root || !state) return;
            if (root !== watchedRoot && observer) {
                watchedRoot = root;
                observer.observe(root, { attributes: true, attributeFilter: ["style"], childList: true });
            }
            if (state.m === "monochrome") {
                clearFilter(root);
                var el = document.getElementById(OVERLAY_ID);
                if (el && el.getAttribute("data-k") !== key) {
                    el.remove();
                    el = null;
                }
                if (!el) el = buildOverlay();
                if (el.parentNode !== root) root.appendChild(el);
                return;
            }
            removeOverlay();
            var value = FILTERS[state.m];
            if (!value) return;
            if (appliedFor === value && applied && root.style.getPropertyValue("filter") === applied && root.style.getPropertyPriority("filter") === "important") return;
            root.style.setProperty("filter", value, "important");
            applied = root.style.getPropertyValue("filter");
            appliedFor = value;
        };

        var clear = function () {
            if (observer) {
                observer.disconnect();
                observer = null;
            }
            watchedRoot = null;
            removeOverlay();
            clearFilter(document.documentElement);
        };

        var watch = function () {
            if (observer) return;
            observer = new MutationObserver(function () { ensure(); });
            observer.observe(document, { childList: true });
            watchedRoot = null;
        };

        document.addEventListener("DOMContentLoaded", function () { ensure(); });

        api = {
            set: function (next) {
                if (!next || !next.m) {
                    state = null;
                    key = "";
                    clear();
                    return;
                }
                var nextKey = next.m + ":" + (next.t || []).join(",");
                if (state && nextKey === key) return;
                state = next;
                key = nextKey;
                appliedFor = "";
                watch();
                ensure();
            }
        };
        try {
            Object.defineProperty(window, "__clintCfx", { value: api, configurable: true });
        } catch (e) {
            return;
        }
    }
    api.set(cfg);
})(__CLINT_CFX__);
