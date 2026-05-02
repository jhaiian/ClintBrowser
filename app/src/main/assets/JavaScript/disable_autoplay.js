(function () {
    'use strict';

    var userActivated = false;
    var activationTimer;

    function onUserGesture() {
        userActivated = true;
        clearTimeout(activationTimer);
        activationTimer = setTimeout(function () { userActivated = false; }, 3000);
    }

    document.addEventListener('click', onUserGesture, true);
    document.addEventListener('touchend', onUserGesture, true);
    document.addEventListener('keydown', onUserGesture, true);

    var nativePlay = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
        if (userActivated) {
            return nativePlay.apply(this, arguments);
        }
        this.autoplay = false;
        return Promise.reject(new DOMException('Autoplay prevented by Data Saver', 'NotAllowedError'));
    };
})();
