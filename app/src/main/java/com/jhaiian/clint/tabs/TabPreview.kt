package com.jhaiian.clint.tabs

data class TabPreview(
    val id: String,
    val title: String,
    val url: String,
    val isIncognito: Boolean,
    val profileId: String = "default"
)
