(function () {
    if (window !== window.top || window.__clintForceZoom) return;
    try {
        Object.defineProperty(window, "__clintForceZoom", { value: true });
    } catch (e) {
        return;
    }
    var blocked = /^(user-scalable|maximum-scale)\s*=/i;
    var fix = function (meta) {
        var content = meta.getAttribute("content");
        if (content === null) return;
        var parts = content.split(/[,;]/).map(function (p) { return p.trim(); }).filter(function (p) {
            return p && !blocked.test(p);
        });
        parts.push("user-scalable=yes", "maximum-scale=10");
        var next = parts.join(", ");
        if (next !== content) meta.setAttribute("content", next);
    };
    var scan = function () {
        var list = document.getElementsByTagName("meta");
        for (var i = 0; i < list.length; i++) {
            if (String(list[i].getAttribute("name") || "").toLowerCase() === "viewport") fix(list[i]);
        }
    };
    new MutationObserver(scan).observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ["content", "name"] });
    scan();
})();
