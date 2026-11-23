package com.yomu.ml

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * `{address}/v1/{path}` on the reader's server, whether or not [address] already ends in `/v1`
 * (ADR-0023); null when [address] is no http or https address.
 */
internal fun serverEndpoint(address: String, vararg path: String): HttpUrl? {
    val base = address.trim().toHttpUrlOrNull() ?: return null
    val segments = base.pathSegments.filter { it.isNotEmpty() }
    val prefix = if (segments.lastOrNull() == "v1") segments else segments + "v1"
    return base.newBuilder()
        .encodedPath("/")
        .apply { (prefix + path).forEach(::addPathSegment) }
        .build()
}
