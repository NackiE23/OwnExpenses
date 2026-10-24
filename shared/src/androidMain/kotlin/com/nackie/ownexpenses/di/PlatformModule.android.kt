package com.nackie.ownexpenses.di

import com.nackie.ownexpenses.data.local.createDatabaseBuilder
import com.nackie.ownexpenses.data.settings.createSettingsDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        single { createDatabaseBuilder(androidContext()) }
        single { createSettingsDataStore(androidContext()) }
    }
