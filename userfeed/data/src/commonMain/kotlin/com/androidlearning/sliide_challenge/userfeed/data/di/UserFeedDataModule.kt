package com.androidlearning.sliide_challenge.userfeed.data.di

import com.androidlearning.sliide_challenge.userfeed.data.KtorUserFeedApi
import com.androidlearning.sliide_challenge.userfeed.data.OfflineFirstUserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.data.UserFeedApi
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** The `HttpClient` and DAOs it needs are provided by the app's core module. */
fun userFeedDataModule(): Module =
    module {
        singleOf(::KtorUserFeedApi) bind UserFeedApi::class
        // Explicit constructor call so the default `Clock` is used instead of being resolved from Koin.
        single<UserFeedRepository> { OfflineFirstUserFeedRepository(get(), get(), get(), get(), get()) }
    }
