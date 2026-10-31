package com.androidlearning.sliide_challenge.userfeed.presentation.form

import com.androidlearning.sliide_challenge.userfeed.domain.EmailValidationError
import com.androidlearning.sliide_challenge.userfeed.domain.NameValidationError

data class UserFormState(
    val isEditing: Boolean = false,
    /** True while an edit form waits for the user's current details. */
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val email: String = "",
    /** Re-validated on every keystroke, but only shown once the field has been touched (see [nameErrorShown]). */
    val nameError: NameValidationError? = NameValidationError.EMPTY,
    val emailError: EmailValidationError? = EmailValidationError.EMPTY,
    /** Set once the user has edited the field and left it, so a fresh form doesn't open covered in errors. */
    val isNameTouched: Boolean = false,
    val isEmailTouched: Boolean = false,
    /** GoRest refused the create because another user already has [email]; cleared as soon as the email changes. */
    val isEmailTaken: Boolean = false
) {
    val nameErrorShown: NameValidationError? get() = nameError.takeIf { isNameTouched }
    val emailErrorShown: EmailValidationError? get() = emailError.takeIf { isEmailTouched }

    val isNameValid: Boolean get() = nameError == null
    val isEmailValid: Boolean get() = emailError == null && !isEmailTaken

    val canSubmit: Boolean get() = isNameValid && isEmailValid && !isLoading && !isSaving
}
