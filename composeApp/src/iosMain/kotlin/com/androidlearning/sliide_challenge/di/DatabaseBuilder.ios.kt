package com.androidlearning.sliide_challenge.di

import androidx.room.RoomDatabase
import com.androidlearning.sliide_challenge.core.database.SliideDatabase
import org.koin.core.scope.Scope
import com.androidlearning.sliide_challenge.core.database.databaseBuilder as iosDatabaseBuilder

internal actual fun Scope.databaseBuilder(): RoomDatabase.Builder<SliideDatabase> =
    iosDatabaseBuilder()
