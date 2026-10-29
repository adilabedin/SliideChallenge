package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId

data class UserFeedState(
    /** True until the database's first emission, so the empty state doesn't flash on open. */
    val isLoading: Boolean = true,
    val users: List<FeedUserUi> = emptyList(),
    /** True while a pull-to-refresh fetches the last GoRest page; the cached users stay on screen under the spinner. */
    val isRefreshing: Boolean = false,
    /** What tapping a row does, picked from the app bar's overflow menu. */
    val mode: FeedMode = FeedMode.Browse,
    /** Users ticked in [FeedMode.Delete]; always a subset of [users]. */
    val selectedIds: Set<FeedUserId> = emptySet(),
    /**
     * The user whose details are shown, beside the list or over it on a phone; always one of [users]. Beside the list
     * the first user is shown until another is picked, so the detail pane is never empty while there are users.
     */
    val openedUserId: FeedUserId? = null,
    /** Whether the details sit beside the list (a wide window) rather than over it. */
    val showsDetailsBesideList: Boolean = false,
    /** Users whose delete is waiting for confirmation in the bottom sheet. */
    val usersToDelete: List<FeedUserUi> = emptyList(),
    /** Deleted users, already hidden from [users], whose delete can still be undone; it is sent once Undo expires. */
    val deletedUsers: List<FeedUserUi> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && users.isEmpty()
}

enum class FeedMode {
    /** Tapping a row opens the user's details; long-pressing it deletes them. */
    Browse,

    /** Tapping a row opens it in the form. */
    Edit,

    /** Tapping a row ticks it, so several users can be deleted at once. */
    Delete,
}
