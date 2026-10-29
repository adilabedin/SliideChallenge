package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide_challenge.core.domain.util.ApplicationScope
import com.androidlearning.sliide_challenge.core.domain.util.onFailure
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Renders the database's view of the feed, so every create, update and delete shows up as soon as it is written.
 *
 * A confirmed delete is held back for [UNDO_WINDOW]: the users are only hidden here, and the repository deletes (local
 * row, then GoRest) run once Undo is no longer offered. A delete that has reached GoRest can't be reversed without
 * re-creating the user under a new id, so Undo restores the rows by simply un-hiding them. Closing the screen ends the
 * undo window early, so the deletes are sent then.
 */
class UserFeedViewModel(
    private val userFeedRepository: UserFeedRepository,
    private val applicationScope: ApplicationScope,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    /** Re-reads the clock every minute, so "Synced 5 minutes ago" keeps moving while the screen is open. */
    private val now: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_INTERVAL)
        }
    }

    /** Everything the screen owns rather than the database: the mode, the selection, the opened user and deletes. */
    private val screenState = MutableStateFlow(UserFeedState())

    /** Users deleted but still in the database: during the undo window and while the repository deletes run. */
    private val hiddenIds = MutableStateFlow<Set<FeedUserId>>(emptySet())

    private var undoTimer: Job? = null

    private val _events = Channel<UserFeedEvent>()
    val events = _events.receiveAsFlow()

    val state: StateFlow<UserFeedState> = combine(
        userFeedRepository.observeUsers(),
        now,
        hiddenIds,
        screenState,
    ) { users, now, hiddenIds, screenState ->
        val visibleUsers = users.filterNot { it.id in hiddenIds }.map { it.toFeedUserUi(now) }
        val visibleIds = visibleUsers.mapTo(mutableSetOf()) { it.id }
        screenState.copy(
            isLoading = false,
            users = visibleUsers,
            // A ticked user can vanish underneath the selection, e.g. once a sync drops it.
            selectedIds = screenState.selectedIds intersect visibleIds,
            openedUserId = screenState.openedUserId?.takeIf { it in visibleIds }
                ?: visibleUsers.firstOrNull()?.id?.takeIf { screenState.showsDetailsBesideList },
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserFeedState(),
        )

    fun onAction(action: UserFeedAction) {
        when (action) {
            UserFeedAction.OnRefresh -> refresh()
            is UserFeedAction.OnUserClick -> onUserClick(action.user)
            is UserFeedAction.OnUserLongClick -> screenState.update {
                if (it.mode == FeedMode.Browse) it.copy(usersToDelete = listOf(action.user)) else it
            }
            UserFeedAction.OnCloseUserClick -> screenState.update { it.copy(openedUserId = null) }
            is UserFeedAction.OnLayoutChange -> screenState.update {
                it.copy(showsDetailsBesideList = action.showsDetailsBesideList)
            }
            UserFeedAction.OnEditModeClick -> setMode(FeedMode.Edit)
            UserFeedAction.OnDeleteModeClick -> setMode(FeedMode.Delete)
            UserFeedAction.OnExitModeClick -> setMode(FeedMode.Browse)
            UserFeedAction.OnDeleteSelectedClick -> {
                val selected = state.value.run { users.filter { it.id in selectedIds } }
                if (selected.isNotEmpty()) screenState.update { it.copy(usersToDelete = selected) }
            }
            UserFeedAction.OnDeleteDismiss -> screenState.update { it.copy(usersToDelete = emptyList()) }
            UserFeedAction.OnDeleteConfirm -> confirmDelete()
            UserFeedAction.OnUndoDeleteClick -> undoDelete()
            is UserFeedAction.OnDeleteRequested -> requestDelete(action.id)
        }
    }

    /** The database drives [state], so the fetched page shows up through [UserFeedRepository.observeUsers]. */
    private fun refresh() {
        if (screenState.value.isRefreshing) return
        screenState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val result = try {
                userFeedRepository.refreshUsers()
            } finally {
                // Also on cancellation, so the shimmer never outlives the request.
                screenState.update { it.copy(isRefreshing = false) }
            }
            // After the shimmer goes, since the send waits for the screen to collect it.
            result.onFailure { error -> _events.send(UserFeedEvent.RefreshFailed(error)) }
        }
    }

    /**
     * Looks the user up in the database rather than in [state], which stops updating while another screen is on top
     * and so may hold an old name for the snackbar.
     */
    private fun requestDelete(id: FeedUserId) {
        viewModelScope.launch {
            val user = userFeedRepository.observeUsers().first().find { it.id == id } ?: return@launch
            screenState.update { it.copy(usersToDelete = listOf(user.toFeedUserUi(clock.now()))) }
            confirmDelete()
        }
    }

    private fun onUserClick(user: FeedUserUi) {
        when (screenState.value.mode) {
            FeedMode.Browse -> screenState.update { it.copy(openedUserId = user.id) }
            FeedMode.Edit -> {
                // Back from the form lands on the plain list rather than still in Edit mode.
                setMode(FeedMode.Browse)
                viewModelScope.launch { _events.send(UserFeedEvent.EditUser(user.id)) }
            }
            FeedMode.Delete -> screenState.update {
                val selectedIds = if (user.id in it.selectedIds) it.selectedIds - user.id else it.selectedIds + user.id
                it.copy(selectedIds = selectedIds)
            }
        }
    }

    private fun setMode(mode: FeedMode) {
        screenState.update { it.copy(mode = mode, selectedIds = emptySet()) }
    }

    private fun confirmDelete() {
        val users = screenState.value.usersToDelete
        if (users.isEmpty()) return
        // Only one Undo is offered at a time, so an earlier delete still waiting on its Undo is sent now.
        finishDelete()
        val ids = users.map(FeedUserUi::id)
        hiddenIds.update { it + ids }
        screenState.update {
            it.copy(
                mode = FeedMode.Browse,
                selectedIds = emptySet(),
                // Closed for good rather than just hidden, so Undo brings the row back without reopening the details.
                openedUserId = it.openedUserId?.takeUnless { id -> id in ids },
                usersToDelete = emptyList(),
                deletedUsers = users,
            )
        }
        undoTimer = viewModelScope.launch {
            delay(UNDO_WINDOW)
            finishDelete()
        }
    }

    private fun undoDelete() {
        val users = screenState.value.deletedUsers
        if (users.isEmpty()) return
        undoTimer?.cancel()
        undoTimer = null
        screenState.update { it.copy(deletedUsers = emptyList()) }
        hiddenIds.update { it - users.map(FeedUserUi::id).toSet() }
    }

    /**
     * Sends the deletes still waiting on their Undo, if any. They run in [applicationScope] because the user has
     * confirmed them: they must go through even if this screen closes before they finish.
     */
    private fun finishDelete() {
        val users = screenState.value.deletedUsers
        if (users.isEmpty()) return
        undoTimer?.cancel()
        undoTimer = null
        screenState.update { it.copy(deletedUsers = emptyList()) }
        applicationScope.launch {
            users.forEach { userFeedRepository.deleteUser(it.id) }
            // Kept hidden until now, so the rows don't flash back before the database drops them.
            hiddenIds.update { it - users.map(FeedUserUi::id).toSet() }
        }
    }

    /** The Undo snackbar goes with the screen, so a delete still waiting on it is final now. */
    override fun onCleared() {
        finishDelete()
    }

    private companion object {
        /** Minutes are the finest unit shown, so a label is at most one tick out of date. */
        val TICK_INTERVAL = 1.minutes

        val UNDO_WINDOW = 5.seconds
    }
}
