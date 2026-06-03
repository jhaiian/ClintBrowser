package com.jhaiian.clint.downloads

class SharedSelectionState {
    val ids: MutableSet<Int> = mutableSetOf()
    var isActive: Boolean = false

    fun clear() {
        ids.clear()
        isActive = false
    }

    val count: Int get() = ids.size
}
