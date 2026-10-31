package com.androidlearning.sliide_challenge.core.data.networking

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

actual fun Throwable.toPlatformNetworkError(): DataError.Network? = when (this) {
    is UnknownHostException,
    is UnresolvedAddressException,
    is ConnectException -> DataError.Network.NO_INTERNET

    is SocketTimeoutException -> DataError.Network.REQUEST_TIMEOUT

    else -> null
}
