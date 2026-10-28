package com.androidlearning.sliide_challenge.core.data.di

import com.androidlearning.sliide_challenge.core.data.user.KtorMainApi
import com.androidlearning.sliide_challenge.core.data.user.KtorMainRepository
import com.androidlearning.sliide_challenge.core.data.user.MainApi
import com.androidlearning.sliide_challenge.core.domain.user.MainRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Expects an `HttpClient` to be provided by the app module. */
val coreDataModule = module {
    singleOf(::KtorMainApi) bind MainApi::class
    singleOf(::KtorMainRepository) bind MainRepository::class
}
