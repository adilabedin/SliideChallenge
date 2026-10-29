package com.androidlearning.sliide_challenge.core.database.di

import com.androidlearning.sliide_challenge.core.database.databaseBuilder
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDatabaseModule: Module = module {
    single { databaseBuilder() }
}
