package com.androidlearning.sliide_challenge.di

import com.androidlearning.sliide_challenge.composeapp.BuildKonfig
import com.androidlearning.sliide_challenge.core.data.networking.HttpClientFactory
import com.androidlearning.sliide_challenge.core.data.user.KtorMainApi
import com.androidlearning.sliide_challenge.core.data.user.MainApi
import com.androidlearning.sliide_challenge.core.data.user.OfflineFirstMainRepository
import com.androidlearning.sliide_challenge.core.database.DatabaseFactory
import com.androidlearning.sliide_challenge.core.database.SliideDatabase
import com.androidlearning.sliide_challenge.core.domain.user.MainRepository
import com.androidlearning.sliide_challenge.core.presentation.splash.MainViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

fun appModule(): Module =
    module {
        single { HttpClientFactory.create(apiKey = BuildKonfig.API_KEY) }
        single { DatabaseFactory.create(databaseBuilder()) }
        single { get<SliideDatabase>().userDao() }
        single { get<SliideDatabase>().createdUserDao() }
        single { get<SliideDatabase>().userPendingSyncDao() }
        single { get<SliideDatabase>().rejectedCreationDao() }

        singleOf(::KtorMainApi) bind MainApi::class
        // Explicit constructor call so the default `Clock` is used instead of being resolved from Koin.
        single<MainRepository> { OfflineFirstMainRepository(get(), get()) }

        viewModelOf(::MainViewModel)
    }
