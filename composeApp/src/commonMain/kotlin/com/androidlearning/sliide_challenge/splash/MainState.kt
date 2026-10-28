package com.androidlearning.sliide_challenge.splash

import com.androidlearning.sliide_challenge.core.domain.util.DataError

data class MainState(
    val isLoading: Boolean = true,
    val error: DataError.Network? = null,
) {
    val isReady: Boolean get() = !isLoading && error == null
}

sealed interface MainAction {
    data object OnRetryClick : MainAction
}
