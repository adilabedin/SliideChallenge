package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId

sealed interface UserFeedEvent {
    data class EditUser(val id: FeedUserId) : UserFeedEvent

    /** A pull-to-refresh failed; the cached users stay on screen. */
    data class RefreshFailed(val error: DataError.Network) : UserFeedEvent
}
