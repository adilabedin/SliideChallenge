package com.androidlearning.sliide_challenge.userfeed.presentation.di

import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun userFeedPresentationModule(): Module =
    module {
        viewModelOf(::UserFeedViewModel)
    }
