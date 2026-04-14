package com.jhaiian.clint.browser

internal fun MainActivity.loadJsAsset(filename: String): String {
    return assets.open("JavaScript/$filename").bufferedReader().use { it.readText() }
}
