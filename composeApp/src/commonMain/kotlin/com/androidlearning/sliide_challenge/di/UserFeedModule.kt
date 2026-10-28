package com.androidlearning.sliide_challenge.di

import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun userFeedModule(): Module =
    module {
        viewModelOf(::UserFeedViewModel)
    }
