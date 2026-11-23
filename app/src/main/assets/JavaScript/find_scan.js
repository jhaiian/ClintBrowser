window.__clintFind = window.__clintFind || (function () {
  var ranges = [];

  function compile(q, regex, matchCase, wholeWord) {
    var src = regex ? q : q.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    if (wholeWord && !regex) src = "(?<![\\p{L}\\p{N}_])" + src + "(?![\\p{L}\\p{N}_])";
    return new RegExp(src, (matchCase ? "g" : "gi") + (regex ? "" : "u"));
  }

  function visible(el) {
    try {
      return el.checkVisibility ? el.checkVisibility({ checkVisibilityCSS: true }) : el.offsetParent !== null;
    } catch (e) { return true; }
  }

  function scan(m) {
    ranges = [];
    var out = { n: 0, y: [], items: [], err: false };
    var q = String(m.q || "");
    if (!q || !document.body) return out;
    var re;
    try { re = compile(q, !!m.regex, !!m.mc, !!m.ww); } catch (e) { out.err = true; return out; }
    var maxY = 1000, maxItems = 200, cap = 5000;
    var height = Math.max(document.documentElement.scrollHeight, 1);
    var top = window.scrollY || 0;
    var cache = new Map();
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n) {
        var p = n.parentElement;
        if (!p || !/\S/.test(n.nodeValue)) return NodeFilter.FILTER_REJECT;
        var tag = p.tagName;
        if (tag === "SCRIPT" || tag === "STYLE" || tag === "NOSCRIPT" || tag === "TEXTAREA" || tag === "TEMPLATE") return NodeFilter.FILTER_REJECT;
        if (m.links && !p.closest("a")) return NodeFilter.FILTER_REJECT;
        var v = cache.get(p);
        if (v === undefined) { v = visible(p); cache.set(p, v); }
        return v ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      }
    });
    var node;
    while (out.n < cap && (node = walker.nextNode())) {
      var text = node.nodeValue, mt;
      re.lastIndex = 0;
      while (out.n < cap && (mt = re.exec(text)) !== null) {
        if (mt[0].length === 0) { re.lastIndex++; continue; }
        var idx = out.n++;
        var range = document.createRange();
        range.setStart(node, mt.index);
        range.setEnd(node, mt.index + mt[0].length);
        ranges.push(range);
        if (idx < maxY) out.y.push(Math.min(1, Math.max(0, (range.getBoundingClientRect().top + top) / height)));
        if (idx < maxItems) {
          var s = Math.max(0, mt.index - 40), e = mt.index + mt[0].length;
          out.items.push({
            b: (s > 0 ? "…" : "") + text.slice(s, mt.index).replace(/\s+/g, " "),
            m: mt[0].replace(/\s+/g, " "),
            a: text.slice(e, e + 60).replace(/\s+/g, " ") + (e + 60 < text.length ? "…" : "")
          });
        }
      }
    }
    return out;
  }

  function go(i) {
    var r = ranges[i];
    if (!r) return false;
    try {
      var sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(r);
      var el = r.startContainer.parentElement;
      if (el) el.scrollIntoView({ block: "center", inline: "nearest" });
      return true;
    } catch (e) { return false; }
  }

  function clear() {
    ranges = [];
    try { window.getSelection().removeAllRanges(); } catch (e) {}
    return true;
  }

  return { scan: scan, go: go, clear: clear };
})();
