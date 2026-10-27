package com.androidlearning.sliide_challenge.userfeed.presentation.di

import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val userFeedPresentationModule = module {
    viewModelOf(::UserFeedViewModel)
}
