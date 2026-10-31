package com.androidlearning.sliide_challenge.core.data.di

import com.androidlearning.sliide_challenge.core.data.BuildKonfig
import com.androidlearning.sliide_challenge.core.data.networking.HttpClientFactory
import com.androidlearning.sliide_challenge.core.data.user.KtorMainApi
import com.androidlearning.sliide_challenge.core.data.user.MainApi
import com.androidlearning.sliide_challenge.core.data.user.OfflineFirstMainRepository
import com.androidlearning.sliide_challenge.core.domain.user.MainRepository
import com.androidlearning.sliide_challenge.core.domain.util.ApplicationScope
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The DAOs it needs come from `coreDatabaseModule`. [enableHttpLogging] comes from the app entry point,
 * which is the only place that knows whether this is a debug build.
 */
fun coreDataModule(enableHttpLogging: Boolean) = module {
    // Shared by every class that takes a `Clock`; tests construct those classes with a fake instead.
    single<Clock> { Clock.System }
    // One per process; the app entry points also run their startup work in it.
    single { ApplicationScope(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
    single { HttpClientFactory.create(apiKey = BuildKonfig.API_KEY, json = get(), enableLogging = enableHttpLogging) }

    singleOf(::KtorMainApi) bind MainApi::class
    singleOf(::OfflineFirstMainRepository) bind MainRepository::class
}
