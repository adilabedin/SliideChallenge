package com.androidlearning.sliide_challenge.userfeed.presentation.detail

sealed interface UserDetailAction {
    /** Switches to edit mode with the fields prefilled from the saved user. */
    data object OnEditClick : UserDetailAction

    /** Leaves edit mode and drops any unsaved changes. */
    data object OnCancelEditClick : UserDetailAction

    data object OnSaveClick : UserDetailAction

    data class OnNameChange(val name: String) : UserDetailAction

    data class OnEmailChange(val email: String) : UserDetailAction

    data object OnNameFocusLost : UserDetailAction

    data object OnEmailFocusLost : UserDetailAction

    /** Asks for confirmation in the delete bottom sheet. */
    data object OnDeleteClick : UserDetailAction

    data object OnDeleteDismiss : UserDetailAction

    data object OnDeleteConfirm : UserDetailAction
}
