package com.androidlearning.sliide_challenge.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

object DatabaseFactory {

    /** Takes the platform `databaseBuilder(...)` (or an in-memory builder in tests). */
    fun create(builder: RoomDatabase.Builder<SliideDatabase>): SliideDatabase =
        builder
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
}
