package com.androidlearning.sliide_challenge.core.data.networking

import com.androidlearning.sliide_challenge.core.domain.util.DataError

/**
 * Maps engine-specific transport exceptions (OkHttp vs. NSURLSession) to a [DataError.Network].
 * Returns `null` when the exception isn't a recognised connectivity failure.
 */
expect fun Throwable.toPlatformNetworkError(): DataError.Network?
