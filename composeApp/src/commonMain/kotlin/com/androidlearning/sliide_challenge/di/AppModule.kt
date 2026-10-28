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

        singleOf(::KtorMainApi) bind MainApi::class
        singleOf(::OfflineFirstMainRepository) bind MainRepository::class

        viewModelOf(::MainViewModel)
    }
