package com.nackie.ownexpenses.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import com.nackie.ownexpenses.data.documentDirectoryPath

internal fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = "${documentDirectoryPath()}/$DATABASE_FILE_NAME")
