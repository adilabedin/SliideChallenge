package com.androidlearning.sliide_challenge.core.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide_challenge.core.domain.user.MainRepository
import com.androidlearning.sliide_challenge.core.domain.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Prepares startup data. While [startupState] is [StartupState.Loading], Android keeps the system splash on screen. iOS
 * can't hold its launch screen, so there the feed shows its shimmer instead.
 *
 * The database is the source of truth: the network is only hit when nothing is cached. A failed fetch ends in
 * [StartupState.Error], which the app hands to the feed so it can say so and offer a retry.
 */
class MainViewModel(private val mainRepository: MainRepository) : ViewModel() {

    private val _startupState = MutableStateFlow(StartupState.Loading)
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    init {
        viewModelScope.launch {
            val isCacheEmpty = mainRepository.observeUsers().first().isEmpty()
            val loadFailed = isCacheEmpty && mainRepository.loadUsers() is Result.Error
            _startupState.value = if (loadFailed) StartupState.Error else StartupState.Loaded
        }
    }
}
