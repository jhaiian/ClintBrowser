package com.jhaiian.clint.quiver

// A single user-authored line of AdBlock/uBlock filter syntax. Unlike FilterList, a manual rule
// has no download URL or file metadata; the rule text is the only content, and createdAt exists
// only to give newly added rules a stable, predictable position at the end of the list.
data class ManualFilterRule(
    val id: Long,
    val ruleText: String,
    val createdAt: Long
)
