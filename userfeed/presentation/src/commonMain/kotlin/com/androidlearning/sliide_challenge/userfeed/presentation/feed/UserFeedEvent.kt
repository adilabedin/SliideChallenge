package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId

sealed interface UserFeedEvent {
    data class EditUser(val id: FeedUserId) : UserFeedEvent
}
