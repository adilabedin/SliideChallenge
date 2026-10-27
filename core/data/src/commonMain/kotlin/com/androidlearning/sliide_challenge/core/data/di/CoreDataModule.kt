package com.androidlearning.sliide_challenge.core.data.di

import com.androidlearning.sliide_challenge.core.data.networking.HttpClientFactory
import org.koin.dsl.module

val coreDataModule = module {
    single { HttpClientFactory.create() }
}
