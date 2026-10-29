package com.androidlearning.sliide_challenge.userfeed.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.domain.UserValidator
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.toFeedUserUi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Shows one user from the database, so a save (or a sync) shows up as soon as it is written, and lets them be edited
 * in place. Saving goes through [UserFeedRepository.updateUser], which queues the change if GoRest can't be reached.
 *
 * Deleting is only confirmed here: the feed owns the Undo window, so the confirmed delete is handed back to it through
 * [UserDetailEvent.DeleteConfirmed] rather than sent from this screen.
 */
class UserDetailViewModel(
    private val userId: FeedUserId,
    private val userFeedRepository: UserFeedRepository,
    private val savedStateHandle: SavedStateHandle,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    /** Re-reads the clock every minute, so "Synced 5 minutes ago" keeps moving while the screen is open. */
    private val now: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_INTERVAL)
        }
    }

    private val user: Flow<FeedUser?> = userFeedRepository.observeUsers()
        .map { users -> users.find { it.id == userId } }
        .distinctUntilChanged()

    /** Everything the screen owns rather than the database; edits in progress survive process death. */
    private val screenState = MutableStateFlow(
        validated(
            UserDetailState(
                isEditing = savedStateHandle[KEY_IS_EDITING] ?: false,
                name = savedStateHandle[KEY_NAME] ?: "",
                email = savedStateHandle[KEY_EMAIL] ?: "",
            ),
        ),
    )

    private val _events = Channel<UserDetailEvent>()
    val events = _events.receiveAsFlow()

    /** Leaving a field only reveals its error if the user actually typed in it. */
    private var isNameEdited = false
    private var isEmailEdited = false

    val state: StateFlow<UserDetailState> = combine(user, now, screenState) { user, now, screenState ->
        screenState.copy(isLoading = false, user = user?.toFeedUserUi(now))
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserDetailState(),
        )

    init {
        viewModelScope.launch {
            user.first { it == null }
            // Only worth telling a screen that is showing the user. This ViewModel outlives its screen (it is kept
            // per user beside the feed), and a queued event would close the details if the same id came back later.
            _events.trySend(UserDetailEvent.UserNotFound)
        }
    }

    fun onAction(action: UserDetailAction) {
        when (action) {
            UserDetailAction.OnEditClick -> startEditing()
            UserDetailAction.OnCancelEditClick -> if (!screenState.value.isSaving) stopEditing()
            UserDetailAction.OnSaveClick -> save()
            is UserDetailAction.OnNameChange -> {
                isNameEdited = true
                savedStateHandle[KEY_NAME] = action.name
                screenState.update { validated(it.copy(name = action.name)) }
            }
            is UserDetailAction.OnEmailChange -> {
                isEmailEdited = true
                savedStateHandle[KEY_EMAIL] = action.email
                screenState.update { validated(it.copy(email = action.email)) }
            }
            UserDetailAction.OnNameFocusLost -> if (isNameEdited) screenState.update { it.copy(isNameTouched = true) }
            UserDetailAction.OnEmailFocusLost -> if (isEmailEdited) screenState.update { it.copy(isEmailTouched = true) }
            UserDetailAction.OnDeleteClick -> screenState.update {
                // A save in flight would land after the delete was handed to the feed, so wait for it to finish.
                if (it.isSaving) it else it.copy(isDeleteSheetVisible = true)
            }
            UserDetailAction.OnDeleteDismiss -> screenState.update { it.copy(isDeleteSheetVisible = false) }
            UserDetailAction.OnDeleteConfirm -> {
                screenState.update { it.copy(isDeleteSheetVisible = false) }
                viewModelScope.launch { _events.send(UserDetailEvent.DeleteConfirmed(userId)) }
            }
        }
    }

    private fun startEditing() {
        val user = state.value.user ?: return
        if (screenState.value.isEditing) return
        isNameEdited = false
        isEmailEdited = false
        savedStateHandle[KEY_IS_EDITING] = true
        savedStateHandle[KEY_NAME] = user.name
        savedStateHandle[KEY_EMAIL] = user.email
        screenState.update {
            validated(
                it.copy(isEditing = true, name = user.name, email = user.email, isNameTouched = false, isEmailTouched = false),
            )
        }
    }

    private fun stopEditing() {
        savedStateHandle.remove<Boolean>(KEY_IS_EDITING)
        savedStateHandle.remove<String>(KEY_NAME)
        savedStateHandle.remove<String>(KEY_EMAIL)
        screenState.update { it.copy(isEditing = false, isSaving = false) }
    }

    private fun save() {
        val current = state.value
        if (screenState.value.isSaving) return
        if (!current.canSave) {
            // Save is disabled while invalid, but the keyboard's Done key can still get here.
            if (current.isEditing) screenState.update { it.copy(isNameTouched = true, isEmailTouched = true) }
            return
        }
        val name = UserValidator.normalizeName(current.name)
        val email = UserValidator.normalizeEmail(current.email)
        screenState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // The repository writes locally, calls GoRest, then queues the change if the call failed. Cancelling
            // between those steps (the screen being closed) would leave a local change that never syncs.
            withContext(NonCancellable) { userFeedRepository.updateUser(userId, name, email) }
            stopEditing()
        }
    }

    /** Validates what will actually be saved, so stray spaces never decide whether the fields are valid. */
    private fun validated(state: UserDetailState): UserDetailState = state.copy(
        nameError = (UserValidator.validateName(UserValidator.normalizeName(state.name)) as? Result.Error)?.error,
        emailError = (UserValidator.validateEmail(UserValidator.normalizeEmail(state.email)) as? Result.Error)?.error,
    )

    private companion object {
        /** Minutes are the finest unit shown, so a label is at most one tick out of date. */
        val TICK_INTERVAL = 1.minutes

        const val KEY_IS_EDITING = "isEditing"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
    }
}
