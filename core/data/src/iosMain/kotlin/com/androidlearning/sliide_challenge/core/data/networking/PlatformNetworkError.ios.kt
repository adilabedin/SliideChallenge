package com.androidlearning.sliide_challenge.core.data.networking

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDataNotAllowed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorInternationalRoamingOff
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut

private val noInternetCodes = setOf(
    NSURLErrorNotConnectedToInternet,
    NSURLErrorNetworkConnectionLost,
    NSURLErrorCannotFindHost,
    NSURLErrorCannotConnectToHost,
    NSURLErrorDNSLookupFailed,
    NSURLErrorDataNotAllowed,
    NSURLErrorInternationalRoamingOff,
)

actual fun Throwable.toPlatformNetworkError(): DataError.Network? {
    if (this !is DarwinHttpRequestException) return null
    if (origin.domain != NSURLErrorDomain) return null
    return when (origin.code) {
        in noInternetCodes -> DataError.Network.NO_INTERNET
        NSURLErrorTimedOut -> DataError.Network.REQUEST_TIMEOUT
        else -> null
    }
}
