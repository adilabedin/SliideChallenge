package com.androidlearning.sliide_challenge.userfeed.domain

/** One local change waiting to reach GoRest, as identified by the queue it sits in. */
sealed interface PendingUserChange {
    /** A user created on this device that GoRest hasn't accepted yet, by its local id. */
    data class Create(val localId: Long) : PendingUserChange

    /** A queued edit, by the user's GoRest id. */
    data class Update(val userId: Long) : PendingUserChange

    /** A queued delete, by the user's GoRest id. */
    data class Delete(val userId: Long) : PendingUserChange
}
