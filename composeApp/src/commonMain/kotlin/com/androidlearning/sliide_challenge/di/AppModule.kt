package com.androidlearning.sliide_challenge.di

import com.androidlearning.sliide_challenge.composeapp.BuildKonfig
import com.androidlearning.sliide_challenge.core.data.networking.HttpClientFactory
import org.koin.core.module.Module
import org.koin.dsl.module

fun appModule(): Module =
    module {
        single { HttpClientFactory.create(apiKey = BuildKonfig.API_KEY) }
    }
