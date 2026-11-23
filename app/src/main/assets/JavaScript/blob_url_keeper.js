(function() {
    if (window.__clintBlobUrlKeeperInstalled) return;
    window.__clintBlobUrlKeeperInstalled = true;
    var revoke = URL.revokeObjectURL;
    if (typeof revoke !== 'function') return;
    URL.revokeObjectURL = function(url) {
        setTimeout(function() {
            try { revoke.call(URL, url); } catch (e) {}
        }, 60000);
    };
})();
