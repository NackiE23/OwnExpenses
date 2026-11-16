package com.nackie.ownexpenses.di

import com.nackie.ownexpenses.data.local.createDatabaseBuilder
import com.nackie.ownexpenses.data.settings.createSettingsDataStore
import com.nackie.ownexpenses.sync.createSyncHttpClient
import io.ktor.client.engine.android.Android
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        single { createDatabaseBuilder(androidContext()) }
        single { createSettingsDataStore(androidContext()) }
        single { createSyncHttpClient(Android) }
    }
