package com.androidlearning.sliide_challenge.core.data.networking

import io.ktor.http.Headers

/** A decoded body plus the response headers, for endpoints that carry metadata there (e.g. pagination). */
data class NetworkResponse<T>(val body: T, val headers: Headers)
