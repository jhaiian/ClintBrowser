(function () {
    'use strict';
    var proto = HTMLScriptElement.prototype;
    var nativeSetAttribute = Element.prototype.setAttribute;
    var srcDescriptor = Object.getOwnPropertyDescriptor(proto, 'src');

    try {
        if (srcDescriptor && srcDescriptor.configurable) {
            Object.defineProperty(proto, 'src', {
                configurable: true,
                enumerable: srcDescriptor.enumerable,
                get: srcDescriptor.get,
                set: function () {}
            });
        }
    } catch (e) {}

    try {
        Element.prototype.setAttribute = function (name, value) {
            if (this instanceof HTMLScriptElement && typeof name === 'string' && name.toLowerCase() === 'src') return;
            return nativeSetAttribute.call(this, name, value);
        };
    } catch (e) {}
})();
