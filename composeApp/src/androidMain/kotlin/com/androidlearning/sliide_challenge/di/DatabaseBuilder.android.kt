package com.androidlearning.sliide_challenge.di

import androidx.room.RoomDatabase
import com.androidlearning.sliide_challenge.core.database.SliideDatabase
import com.androidlearning.sliide_challenge.core.database.databaseBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope

internal actual fun Scope.databaseBuilder(): RoomDatabase.Builder<SliideDatabase> =
    databaseBuilder(androidContext())
