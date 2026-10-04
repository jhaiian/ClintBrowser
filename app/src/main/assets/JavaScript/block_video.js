(function () {
    'use strict';
    var videoType = /video|avc1|hev1|hvc1|vp8|vp9|vp09|av01|theora/i;
    var proto = HTMLMediaElement.prototype;
    var nativeLoad = proto.load;
    var nativePlay = proto.play;
    var nativeCanPlay = proto.canPlayType;

    function isVideo(el) {
        return el instanceof HTMLVideoElement && !el.srcObject;
    }

    function block(el) {
        try {
            if (!isVideo(el)) return;
            var sources = el.querySelectorAll('source');
            var hadSource = el.hasAttribute('src') || sources.length > 0;
            if (!hadSource) return;
            el.removeAttribute('src');
            el.removeAttribute('autoplay');
            for (var i = 0; i < sources.length; i++) {
                if (sources[i].parentNode) sources[i].parentNode.removeChild(sources[i]);
            }
            nativeLoad.call(el);
        } catch (e) {}
    }

    function blockTree(node) {
        if (!node || node.nodeType !== 1) return;
        if (node instanceof HTMLVideoElement) block(node);
        else if (node.tagName === 'SOURCE' && node.parentNode instanceof HTMLVideoElement) block(node.parentNode);
        if (!node.querySelectorAll) return;
        var list = node.querySelectorAll('video');
        for (var i = 0; i < list.length; i++) block(list[i]);
    }

    try {
        proto.play = function () {
            if (isVideo(this)) {
                block(this);
                return Promise.reject(new DOMException('Video blocked', 'NotSupportedError'));
            }
            return nativePlay.apply(this, arguments);
        };
        proto.load = function () {
            if (isVideo(this)) {
                block(this);
                return;
            }
            return nativeLoad.apply(this, arguments);
        };
        proto.canPlayType = function (type) {
            if (type && videoType.test(String(type))) return '';
            return nativeCanPlay.apply(this, arguments);
        };
    } catch (e) {}

    try {
        var srcDesc = Object.getOwnPropertyDescriptor(proto, 'src');
        if (srcDesc && srcDesc.set) {
            Object.defineProperty(proto, 'src', {
                configurable: true,
                enumerable: true,
                get: srcDesc.get,
                set: function (value) {
                    if (isVideo(this) && value) {
                        return;
                    }
                    srcDesc.set.call(this, value);
                }
            });
        }
    } catch (e) {}

    try {
        if (window.MediaSource) {
            var nativeSupported = MediaSource.isTypeSupported;
            MediaSource.isTypeSupported = function (type) {
                if (type && videoType.test(String(type))) return false;
                return nativeSupported.apply(MediaSource, arguments);
            };
            var nativeAddBuffer = MediaSource.prototype.addSourceBuffer;
            MediaSource.prototype.addSourceBuffer = function (type) {
                if (type && videoType.test(String(type))) {
                    throw new DOMException('Video blocked', 'NotSupportedError');
                }
                return nativeAddBuffer.apply(this, arguments);
            };
        }
    } catch (e) {}

    try {
        if (navigator.requestMediaKeySystemAccess) {
            var nativeEme = navigator.requestMediaKeySystemAccess;
            navigator.requestMediaKeySystemAccess = function (keySystem, configs) {
                var wantsVideo = false;
                if (configs && configs.length) {
                    for (var i = 0; i < configs.length; i++) {
                        var caps = configs[i] && configs[i].videoCapabilities;
                        if (caps && caps.length) wantsVideo = true;
                    }
                }
                if (wantsVideo) {
                    return Promise.reject(new DOMException('Video blocked', 'NotSupportedError'));
                }
                return nativeEme.apply(navigator, arguments);
            };
        }
    } catch (e) {}

    try {
        var observer = new MutationObserver(function (records) {
            for (var i = 0; i < records.length; i++) {
                var record = records[i];
                if (record.type === 'attributes') {
                    if (record.target instanceof HTMLVideoElement) block(record.target);
                    else if (record.target.tagName === 'SOURCE' && record.target.parentNode instanceof HTMLVideoElement) block(record.target.parentNode);
                } else {
                    for (var j = 0; j < record.addedNodes.length; j++) blockTree(record.addedNodes[j]);
                }
            }
        });
        observer.observe(document, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['src', 'autoplay']
        });
    } catch (e) {}
})();
