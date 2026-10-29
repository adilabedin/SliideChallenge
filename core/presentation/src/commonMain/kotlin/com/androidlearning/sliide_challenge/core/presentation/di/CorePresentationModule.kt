package com.androidlearning.sliide_challenge.core.presentation.di

import com.androidlearning.sliide_challenge.core.presentation.splash.MainViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val corePresentationModule = module {
    viewModelOf(::MainViewModel)
}
