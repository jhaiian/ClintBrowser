package com.jhaiian.clint.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun registeredDomain(host: String): String =
    "https://$host".toHttpUrlOrNull()?.topPrivateDomain() ?: host

