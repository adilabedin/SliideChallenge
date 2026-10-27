package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserFeedViewModel : ViewModel() {

    private val _state = MutableStateFlow(UserFeedState())
    val state: StateFlow<UserFeedState> = _state.asStateFlow()
}
