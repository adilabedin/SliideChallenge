package com.androidlearning.sliide_challenge.core.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide_challenge.core.domain.user.MainRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Prepares startup data. While [isLoading] is true, Android keeps the system splash on
 * screen and iOS shows the app's `SplashContent`, which mirrors the native launch screen.
 *
 * The database is the source of truth: the network is only hit when nothing is cached. If that
 * fetch fails the feed opens on its empty state.
 */
class MainViewModel(
    private val mainRepository: MainRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoading.value = true

            if (mainRepository.observeUsers().first().isEmpty()) {
                mainRepository.loadUsers()
            }

            _isLoading.value = false
        }
    }
}
