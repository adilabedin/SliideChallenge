package com.androidlearning.sliide_challenge.userfeed.data.di

import com.androidlearning.sliide_challenge.userfeed.data.KtorUserFeedApi
import com.androidlearning.sliide_challenge.userfeed.data.OfflineFirstUserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.data.UserFeedApi
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** The `HttpClient`, DAOs, `Clock` and `ApplicationScope` it needs come from the core modules. */
val userFeedDataModule = module {
    singleOf(::KtorUserFeedApi) bind UserFeedApi::class
    singleOf(::OfflineFirstUserFeedRepository) bind UserFeedRepository::class
}
