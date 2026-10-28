package com.androidlearning.sliide_challenge.userfeed.domain

/** A row of the user feed: either a user created on this device or one from the fetched GoRest page. */
data class FeedUser(
    val id: FeedUserId,
    val name: String,
    val email: String,
)

/**
 * Tells the repository which table a [FeedUser] lives in. Local row ids and GoRest ids are separate sequences and can
 * collide, so a bare `Long` is not enough.
 */
sealed interface FeedUserId {
    /** A user created on this device, keyed by its local row id; it may not have a GoRest id yet. */
    data class Created(val localId: Long) : FeedUserId

    /** A user from the fetched GoRest page, keyed by its GoRest id. */
    data class Remote(val id: Long) : FeedUserId
}
