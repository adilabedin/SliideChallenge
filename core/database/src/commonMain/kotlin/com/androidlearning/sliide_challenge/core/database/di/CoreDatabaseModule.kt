package com.androidlearning.sliide_challenge.core.database.di

import com.androidlearning.sliide_challenge.core.database.DatabaseFactory
import com.androidlearning.sliide_challenge.core.database.DatabaseTransactionRunner
import com.androidlearning.sliide_challenge.core.database.RoomTransactionRunner
import com.androidlearning.sliide_challenge.core.database.SliideDatabase
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Provides the platform `RoomDatabase.Builder`: Android needs the Koin-provided `Context`, iOS does not. */
internal expect val platformDatabaseModule: Module

val coreDatabaseModule = module {
    includes(platformDatabaseModule)

    single { DatabaseFactory.create(get()) }
    single { get<SliideDatabase>().userDao() }
    single { get<SliideDatabase>().createdUserDao() }
    single { get<SliideDatabase>().userPendingSyncDao() }
    single { get<SliideDatabase>().rejectedCreationDao() }
    singleOf(::RoomTransactionRunner) bind DatabaseTransactionRunner::class
}
