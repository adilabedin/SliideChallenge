package com.androidlearning.sliide_challenge.userfeed.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.domain.UserValidator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Validates as the user types and saves through [UserFeedRepository], which writes to the database before calling
 * GoRest. The form waits for that call so it can report a create GoRest refused (email already taken) in place;
 * any other failure has already been queued for sync, so the form closes as if it had succeeded.
 */
class UserFormViewModel(
    private val mode: UserFormMode,
    private val userFeedRepository: UserFeedRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        validated(
            UserFormState(
                isEditing = mode is UserFormMode.Edit,
                name = savedStateHandle[KEY_NAME] ?: "",
                email = savedStateHandle[KEY_EMAIL] ?: "",
            ),
        ),
    )
    val state = _state.asStateFlow()

    private val _events = Channel<UserFormEvent>()
    val events = _events.receiveAsFlow()

    /** Leaving a field only reveals its error if the user actually typed in it. */
    private var isNameEdited = false
    private var isEmailEdited = false

    init {
        // After process death the fields come back from the SavedStateHandle and must not be overwritten.
        if (mode is UserFormMode.Edit && !savedStateHandle.contains(KEY_NAME)) {
            prefill(mode)
        }
    }

    fun onAction(action: UserFormAction) {
        when (action) {
            is UserFormAction.OnNameChange -> {
                isNameEdited = true
                savedStateHandle[KEY_NAME] = action.name
                _state.update { validated(it.copy(name = action.name)) }
            }
            is UserFormAction.OnEmailChange -> {
                isEmailEdited = true
                savedStateHandle[KEY_EMAIL] = action.email
                _state.update { validated(it.copy(email = action.email, isEmailTaken = false)) }
            }
            UserFormAction.OnNameFocusLost -> if (isNameEdited) _state.update { it.copy(isNameTouched = true) }
            UserFormAction.OnEmailFocusLost -> if (isEmailEdited) _state.update { it.copy(isEmailTouched = true) }
            UserFormAction.OnSubmitClick -> submit()
        }
    }

    private fun prefill(mode: UserFormMode.Edit) {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val user = userFeedRepository.observeUsers().first().find { it.id == mode.userId }
            if (user == null) {
                _events.send(UserFormEvent.UserNotFound)
                return@launch
            }
            savedStateHandle[KEY_NAME] = user.name
            savedStateHandle[KEY_EMAIL] = user.email
            _state.update { validated(it.copy(isLoading = false, name = user.name, email = user.email)) }
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) {
            // The submit button is disabled while invalid, but the keyboard's Done key can still get here.
            if (!current.isSaving && !current.isLoading) {
                _state.update { it.copy(isNameTouched = true, isEmailTouched = true) }
            }
            return
        }
        val name = UserValidator.normalizeName(current.name)
        val email = UserValidator.normalizeEmail(current.email)
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // The repository writes locally, calls GoRest, then queues the change if the call failed. Cancelling
            // between those steps (the form being closed) would leave a local change that never syncs.
            val result = withContext(NonCancellable) { save(name, email) }
            if (result is Result.Error && result.error == DataError.Network.UNPROCESSABLE_ENTITY &&
                mode is UserFormMode.Create
            ) {
                _state.update { it.copy(isSaving = false, isEmailTaken = true, isEmailTouched = true) }
            } else {
                _events.send(UserFormEvent.Saved)
            }
        }
    }

    private suspend fun save(name: String, email: String): EmptyResult<DataError.Network> =
        when (mode) {
            UserFormMode.Create -> userFeedRepository.createUser(name, email).also { result ->
                if (result is Result.Error && result.error == DataError.Network.UNPROCESSABLE_ENTITY) {
                    dismissRejection(email)
                }
            }
            is UserFormMode.Edit -> userFeedRepository.updateUser(mode.userId, name, email)
        }

    /**
     * The repository records a refused create so whoever is showing the feed can report it. Here the form reports it
     * itself and stays open, so the record is dropped to avoid telling the user twice.
     */
    private suspend fun dismissRejection(email: String) {
        userFeedRepository.observeRejectedCreations().first()
            .lastOrNull { it.email == email }
            ?.let { userFeedRepository.dismissRejectedCreation(it.id) }
    }

    /** Validates what will actually be saved, so stray spaces never decide whether the form is valid. */
    private fun validated(state: UserFormState): UserFormState = state.copy(
        nameError = (UserValidator.validateName(UserValidator.normalizeName(state.name)) as? Result.Error)?.error,
        emailError = (UserValidator.validateEmail(UserValidator.normalizeEmail(state.email)) as? Result.Error)?.error,
    )

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
    }
}
