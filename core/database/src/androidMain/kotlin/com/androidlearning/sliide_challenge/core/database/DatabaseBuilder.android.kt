package com.androidlearning.sliide_challenge.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun databaseBuilder(context: Context): RoomDatabase.Builder<SliideDatabase> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<SliideDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(DatabaseConstants.DB_NAME).absolutePath
    )
}
