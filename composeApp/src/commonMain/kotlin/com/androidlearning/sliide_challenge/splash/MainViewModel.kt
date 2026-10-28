package com.androidlearning.sliide_challenge.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide_challenge.core.domain.user.MainRepository
import com.androidlearning.sliide_challenge.core.domain.util.onFailure
import com.androidlearning.sliide_challenge.core.domain.util.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads startup data. While [MainState.isLoading] is true, Android keeps the system splash on
 * screen and iOS shows [SplashContent], which mirrors the native launch screen.
 */
class MainViewModel(
    private val mainRepository: MainRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    init {
        loadUsers()
    }

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.OnRetryClick -> loadUsers()
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            _state.update {
                it.copy(isLoading = true)
            }

            mainRepository.loadUsers()
                .onSuccess {
                    _state.update {
                        it.copy(isLoading = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error
                        )
                    }
                }
        }
    }
}
