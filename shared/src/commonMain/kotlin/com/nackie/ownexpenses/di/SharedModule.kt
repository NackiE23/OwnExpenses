package com.nackie.ownexpenses.di

import androidx.room.RoomDatabase
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.buildAppDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Data layer graph: database, settings, and later repositories and ViewModels.
 * Platforms contribute their builders through [platformModule].
 */
val sharedModule: Module =
    module {
        includes(platformModule)
        single<AppDatabase> { get<RoomDatabase.Builder<AppDatabase>>().buildAppDatabase() }
    }

internal expect val platformModule: Module
