package com.jhaiian.clint.browser.webview

object TabMediaControl {
    const val PAUSE_SCRIPT = "(function(){function p(d){try{d.querySelectorAll('video,audio').forEach(function(m){try{m.pause()}catch(e){}});d.querySelectorAll('iframe').forEach(function(f){try{if(f.contentDocument)p(f.contentDocument)}catch(e){}})}catch(e){}}p(document)})();"
}
