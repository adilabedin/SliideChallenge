package com.androidlearning.sliide_challenge.core.presentation.snackbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/**
 * Takes down the snackbar showing, for a message that can't wait its turn, such as an Undo whose timer is already
 * running. Suspends until it goes away; cancelling the caller takes it down.
 */
suspend fun SnackbarHostState.showNow(
    message: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short
): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(message = message, actionLabel = actionLabel, duration = duration)
}
