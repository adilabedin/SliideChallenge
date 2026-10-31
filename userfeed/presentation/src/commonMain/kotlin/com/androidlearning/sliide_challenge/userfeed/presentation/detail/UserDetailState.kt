package com.androidlearning.sliide_challenge.userfeed.presentation.detail

import com.androidlearning.sliide_challenge.userfeed.domain.EmailValidationError
import com.androidlearning.sliide_challenge.userfeed.domain.NameValidationError
import com.androidlearning.sliide_challenge.userfeed.domain.UserValidator
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.FeedUserUi

data class UserDetailState(
    /** True until the database's first emission. */
    val isLoading: Boolean = true,
    /** The user as saved; null while loading, or once they are gone (the screen then closes). */
    val user: FeedUserUi? = null,
    /** Set by the app bar's edit button: the details become fields prefilled with [user]'s name and email. */
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    /** The edit fields; only meaningful while [isEditing]. */
    val name: String = "",
    val email: String = "",
    /** Re-validated on every keystroke, but only shown once the field has been touched (see [nameErrorShown]). */
    val nameError: NameValidationError? = null,
    val emailError: EmailValidationError? = null,
    /** Set once the user has edited the field and left it, so edit mode doesn't open covered in errors. */
    val isNameTouched: Boolean = false,
    val isEmailTouched: Boolean = false,
    /** True while the delete bottom sheet asks for confirmation. */
    val isDeleteSheetVisible: Boolean = false
) {
    val nameErrorShown: NameValidationError? get() = nameError.takeIf { isNameTouched }
    val emailErrorShown: EmailValidationError? get() = emailError.takeIf { isEmailTouched }

    val isNameValid: Boolean get() = nameError == null
    val isEmailValid: Boolean get() = emailError == null

    /** Saving what is already saved would only queue a pointless sync, so Save waits for a real change. */
    val hasChanges: Boolean
        get() = user != null &&
            (UserValidator.normalizeName(name) != user.name || UserValidator.normalizeEmail(email) != user.email)

    val canSave: Boolean get() = isEditing && isNameValid && isEmailValid && hasChanges && !isSaving
}
