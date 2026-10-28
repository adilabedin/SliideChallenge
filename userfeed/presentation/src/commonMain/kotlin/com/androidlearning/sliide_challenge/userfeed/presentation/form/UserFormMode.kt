package com.androidlearning.sliide_challenge.userfeed.presentation.form

import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId

/** The same form creates a user or edits an existing one; this tells [UserFormViewModel] which. */
sealed interface UserFormMode {
    data object Create : UserFormMode

    data class Edit(val userId: FeedUserId) : UserFormMode
}
