package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Renders the database's view of the feed, so every create, update and delete shows up as soon as it is written. */
class UserFeedViewModel(
    userFeedRepository: UserFeedRepository,
) : ViewModel() {

    val state: StateFlow<UserFeedState> = userFeedRepository.observeUsers()
        .map { users -> UserFeedState(isLoading = false, users = users) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserFeedState(),
        )
}
