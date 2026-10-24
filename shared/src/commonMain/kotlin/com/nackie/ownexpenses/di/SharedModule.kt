package com.nackie.ownexpenses.di

import androidx.room.RoomDatabase
import com.nackie.ownexpenses.AppInitializer
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.buildAppDatabase
import com.nackie.ownexpenses.data.repository.DataStoreSettingsRepository
import com.nackie.ownexpenses.data.repository.DefaultCategorySeeder
import com.nackie.ownexpenses.data.repository.RoomCategoryRepository
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.repository.CategoryRepository
import com.nackie.ownexpenses.domain.repository.SettingsRepository
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Data layer graph. Platforms contribute their builders through [platformModule]; the UI
 * layer must provide [com.nackie.ownexpenses.domain.model.DefaultCategoryNames].
 */
val sharedModule: Module =
    module {
        includes(platformModule)

        single<AppDatabase> { get<RoomDatabase.Builder<AppDatabase>>().buildAppDatabase() }
        single<Clock> { Clock.System }

        single<CategoryRepository> { RoomCategoryRepository(get()) }
        single<TransactionRepository> { RoomTransactionRepository(get(), get()) }
        single<SettingsRepository> { DataStoreSettingsRepository(get()) }

        single { DefaultCategorySeeder(get(), get(), get()) }
        single { AppInitializer(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    }

internal expect val platformModule: Module
