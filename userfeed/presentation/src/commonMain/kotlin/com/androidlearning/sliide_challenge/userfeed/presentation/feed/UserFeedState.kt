package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId

data class UserFeedState(
    /** True until the database's first emission, so the empty state doesn't flash on open. */
    val isLoading: Boolean = true,
    val users: List<FeedUserUi> = emptyList(),
    /** What tapping a row does, picked from the app bar's overflow menu. */
    val mode: FeedMode = FeedMode.Browse,
    /** Users ticked in [FeedMode.Delete]; always a subset of [users]. */
    val selectedIds: Set<FeedUserId> = emptySet(),
    /** Users whose delete is waiting for confirmation in the bottom sheet. */
    val usersToDelete: List<FeedUserUi> = emptyList(),
    /** Deleted users, already hidden from [users], whose delete can still be undone; it is sent once Undo expires. */
    val deletedUsers: List<FeedUserUi> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && users.isEmpty()
}

enum class FeedMode {
    /** Rows can only be long-pressed, to delete one user. */
    Browse,

    /** Tapping a row opens it in the form. */
    Edit,

    /** Tapping a row ticks it, so several users can be deleted at once. */
    Delete,
}
