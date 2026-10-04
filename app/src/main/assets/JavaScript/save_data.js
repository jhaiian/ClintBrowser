(function () {
    'use strict';
    try {
        if (typeof NetworkInformation === 'undefined') return;
        Object.defineProperty(NetworkInformation.prototype, 'saveData', {
            get: function () { return true; },
            configurable: true,
            enumerable: true
        });
    } catch (e) {}
})();
