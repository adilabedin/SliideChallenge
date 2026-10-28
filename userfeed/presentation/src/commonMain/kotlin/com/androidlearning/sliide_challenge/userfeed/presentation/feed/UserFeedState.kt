package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser

data class UserFeedState(
    /** True until the database's first emission, so the empty state doesn't flash on open. */
    val isLoading: Boolean = true,
    val users: List<FeedUser> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && users.isEmpty()
}
