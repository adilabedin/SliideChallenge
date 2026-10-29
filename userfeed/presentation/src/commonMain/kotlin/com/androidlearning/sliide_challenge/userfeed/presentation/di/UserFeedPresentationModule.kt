package com.androidlearning.sliide_challenge.userfeed.presentation.di

import com.androidlearning.sliide_challenge.userfeed.presentation.detail.UserDetailViewModel
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedViewModel
import com.androidlearning.sliide_challenge.userfeed.presentation.form.UserFormViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val userFeedPresentationModule = module {
    viewModelOf(::UserFeedViewModel)
    // Lambda form because the mode comes from the route via `parametersOf`; Koin's ViewModel factory supplies the
    // SavedStateHandle.
    viewModel { params -> UserFormViewModel(mode = params.get(), userFeedRepository = get(), savedStateHandle = get()) }
    // The user id comes from the route the same way.
    viewModel { params ->
        UserDetailViewModel(userId = params.get(), userFeedRepository = get(), savedStateHandle = get(), clock = get())
    }
}
