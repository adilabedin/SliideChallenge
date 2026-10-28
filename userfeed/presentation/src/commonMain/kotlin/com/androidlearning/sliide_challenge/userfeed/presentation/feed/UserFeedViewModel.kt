package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide_challenge.core.domain.user.MainRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Renders whatever is cached; [MainRepository] keeps the cache in sync with the latest GoRest page. */
class UserFeedViewModel(
    mainRepository: MainRepository,
) : ViewModel() {

    val state: StateFlow<UserFeedState> = mainRepository.observeUsers()
        .map { users -> UserFeedState(isLoading = false, users = users) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserFeedState(),
        )
}
