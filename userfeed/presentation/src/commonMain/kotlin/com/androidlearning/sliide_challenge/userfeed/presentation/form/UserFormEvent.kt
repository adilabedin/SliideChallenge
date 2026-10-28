package com.androidlearning.sliide_challenge.userfeed.presentation.form

sealed interface UserFormEvent {
    /** The change is in the local database (and so in the feed), whether or not it has reached GoRest yet. */
    data object Saved : UserFormEvent

    /** The user being edited no longer exists, e.g. it was deleted while the form was opening. */
    data object UserNotFound : UserFormEvent
}
