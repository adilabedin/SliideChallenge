package com.androidlearning.sliide_challenge.userfeed.domain

/**
 * A user GoRest refused to create (e.g. its email is already taken). The user has been removed from the feed; this
 * stays until the UI has told the user and calls [UserFeedRepository.dismissRejectedCreation].
 */
data class RejectedCreation(
    val id: Long,
    val name: String,
    val email: String,
)
