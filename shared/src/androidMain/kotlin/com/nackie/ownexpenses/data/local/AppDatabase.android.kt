package com.nackie.ownexpenses.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

internal fun createDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(DATABASE_FILE_NAME).absolutePath,
    )
}
