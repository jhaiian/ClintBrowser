(function () {
    'use strict';
    var proto = HTMLMediaElement.prototype;
    var desc = Object.getOwnPropertyDescriptor(proto, 'preload');
    if (!desc || !desc.set) return;
    var nativeSet = desc.set;

    function lock(el) {
        try {
            if (el.getAttribute('preload') === 'none') return;
            nativeSet.call(el, 'none');
        } catch (e) {}
    }

    function lockTree(node) {
        if (!node || node.nodeType !== 1) return;
        if (node instanceof HTMLMediaElement) lock(node);
        if (!node.querySelectorAll) return;
        var list = node.querySelectorAll('video,audio');
        for (var i = 0; i < list.length; i++) {
            lock(list[i]);
        }
    }

    try {
        Object.defineProperty(proto, 'preload', {
            configurable: true,
            enumerable: true,
            get: desc.get,
            set: function () { lock(this); }
        });
    } catch (e) {}

    var nativeCreate = Document.prototype.createElement;
    Document.prototype.createElement = function () {
        var el = nativeCreate.apply(this, arguments);
        if (el instanceof HTMLMediaElement) lock(el);
        return el;
    };

    if (typeof Audio === 'function') {
        window.Audio = new Proxy(Audio, {
            construct: function (target, args) {
                var audio = Reflect.construct(target, []);
                lock(audio);
                if (args.length) audio.src = args[0];
                return audio;
            }
        });
    }

    var observer = new MutationObserver(function (records) {
        for (var i = 0; i < records.length; i++) {
            var record = records[i];
            if (record.type === 'attributes') {
                if (record.target instanceof HTMLMediaElement) lock(record.target);
            } else {
                for (var j = 0; j < record.addedNodes.length; j++) lockTree(record.addedNodes[j]);
            }
        }
    });
    observer.observe(document, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['preload', 'src']
    });
})();
