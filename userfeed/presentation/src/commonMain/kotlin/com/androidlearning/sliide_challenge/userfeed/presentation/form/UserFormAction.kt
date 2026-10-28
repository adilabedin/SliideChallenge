package com.androidlearning.sliide_challenge.userfeed.presentation.form

sealed interface UserFormAction {
    data class OnNameChange(val name: String) : UserFormAction

    data class OnEmailChange(val email: String) : UserFormAction

    data object OnNameFocusLost : UserFormAction

    data object OnEmailFocusLost : UserFormAction

    data object OnSubmitClick : UserFormAction
}
