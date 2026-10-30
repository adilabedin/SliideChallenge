package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.presentation.snackbar.showNow
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.refresh_error_no_internet
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.refresh_error_timeout
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.refresh_error_unknown
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.undo
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.user_deleted
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.users_deleted
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Offers Undo for as long as the ViewModel keeps [deletedUsers]; once they clear (Undo expired or another delete took
 * its place) the effect restarts, which takes the snackbar down. The ViewModel owns the timer, so the delete still
 * goes through if this screen leaves composition first. Undo jumps the queue, since every second it waits is a second
 * less to tap it.
 */
@Composable
fun UndoDeleteSnackbarEffect(
    deletedUsers: List<FeedUserUi>,
    snackbarHostState: SnackbarHostState,
    onUndoClick: () -> Unit
) {
    LaunchedEffect(deletedUsers) {
        if (deletedUsers.isEmpty()) return@LaunchedEffect
        val message = deletedUsers.singleOrNull()?.let { getString(Res.string.user_deleted, it.name) }
            ?: getPluralString(Res.plurals.users_deleted, deletedUsers.size, deletedUsers.size)
        val result = snackbarHostState.showNow(
            message = message,
            actionLabel = getString(Res.string.undo),
            duration = SnackbarDuration.Indefinite
        )
        if (result == SnackbarResult.ActionPerformed) onUndoClick()
    }
}

/** The cached users stay on screen underneath, so the message says what the user is looking at. */
fun DataError.Network.toRefreshMessage(): StringResource = when (this) {
    DataError.Network.NO_INTERNET -> Res.string.refresh_error_no_internet
    DataError.Network.REQUEST_TIMEOUT -> Res.string.refresh_error_timeout
    else -> Res.string.refresh_error_unknown
}
