package com.androidlearning.sliide_challenge.userfeed.presentation.detail

import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId

sealed interface UserDetailEvent {
    /**
     * The delete was confirmed. It is not sent from here: the feed holds it back so it can offer Undo, so whoever shows
     * this screen hands [id] to the feed and returns to it.
     */
    data class DeleteConfirmed(val id: FeedUserId) : UserDetailEvent

    /** The user no longer exists, e.g. a refresh of the GoRest page dropped them. */
    data object UserNotFound : UserDetailEvent
}
