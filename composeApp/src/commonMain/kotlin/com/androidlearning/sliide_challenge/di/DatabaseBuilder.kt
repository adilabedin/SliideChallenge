package com.androidlearning.sliide_challenge.di

import androidx.room.RoomDatabase
import com.androidlearning.sliide_challenge.core.database.SliideDatabase
import org.koin.core.scope.Scope

/** Android needs the Koin-provided `Context` to locate the database file; iOS does not. */
internal expect fun Scope.databaseBuilder(): RoomDatabase.Builder<SliideDatabase>
