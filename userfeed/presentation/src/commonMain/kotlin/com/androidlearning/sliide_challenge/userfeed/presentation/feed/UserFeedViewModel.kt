package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Renders the database's view of the feed, so every create, update and delete shows up as soon as it is written. */
class UserFeedViewModel(
    userFeedRepository: UserFeedRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    /** Re-reads the clock every minute, so "Synced 5 minutes ago" keeps moving while the screen is open. */
    private val now: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_INTERVAL)
        }
    }

    val state: StateFlow<UserFeedState> = combine(userFeedRepository.observeUsers(), now) { users, now ->
        UserFeedState(isLoading = false, users = users.map { it.toFeedUserUi(now) })
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserFeedState(),
        )

    private companion object {
        /** Minutes are the finest unit shown, so a label is at most one tick out of date. */
        val TICK_INTERVAL = 1.minutes
    }
}
