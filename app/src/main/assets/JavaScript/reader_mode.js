(function() {
    var candidates = [
        'article',
        '[role="article"]',
        '.article-body',
        '.article-content',
        '.article__body',
        '.post-content',
        '.post-body',
        '.entry-content',
        '.story-body',
        '.story-content',
        '.content-body',
        'main',
        '[role="main"]',
        '#content',
        '#main-content',
        '#article-body'
    ];

    function findBestNode() {
        for (var i = 0; i < candidates.length; i++) {
            var el = document.querySelector(candidates[i]);
            if (el && (el.innerText || '').trim().length > 150) {
                return el;
            }
        }
        return document.body;
    }

    var node = findBestNode();
    var clone = node.cloneNode(true);

    var remove = clone.querySelectorAll('script,style,noscript,iframe,nav,header,footer,aside,form,[role="navigation"],[role="banner"],[role="complementary"]');
    for (var i = remove.length - 1; i >= 0; i--) {
        var p = remove[i].parentNode;
        if (p) p.removeChild(remove[i]);
    }

    return JSON.stringify({ title: document.title || '', content: clone.innerHTML });
})();
