package com.androidlearning.sliide_challenge.userfeed.presentation.di

import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedViewModel
import com.androidlearning.sliide_challenge.userfeed.presentation.form.UserFormViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun userFeedPresentationModule(): Module =
    module {
        // Explicit constructor call so the default `Clock` is used instead of being resolved from Koin.
        viewModel { UserFeedViewModel(get()) }
        // The mode comes from the route via `parametersOf`; the SavedStateHandle is supplied by Koin's ViewModel factory.
        viewModel { params -> UserFormViewModel(mode = params.get(), userFeedRepository = get(), savedStateHandle = get()) }
    }
