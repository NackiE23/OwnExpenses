package com.nackie.ownexpenses.di

import androidx.room.RoomDatabase
import com.nackie.ownexpenses.AppInitializer
import com.nackie.ownexpenses.backup.BackupService
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.buildAppDatabase
import com.nackie.ownexpenses.data.repository.DataStoreSettingsRepository
import com.nackie.ownexpenses.data.repository.DefaultCategorySeeder
import com.nackie.ownexpenses.data.repository.RoomCategoryRepository
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.PeriodSelection
import com.nackie.ownexpenses.domain.SystemTodayProvider
import com.nackie.ownexpenses.domain.TodayProvider
import com.nackie.ownexpenses.domain.repository.CategoryRepository
import com.nackie.ownexpenses.domain.repository.SettingsRepository
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import com.nackie.ownexpenses.presentation.AppViewModel
import com.nackie.ownexpenses.presentation.categories.CategoriesViewModel
import com.nackie.ownexpenses.presentation.history.HistoryViewModel
import com.nackie.ownexpenses.presentation.home.HomeViewModel
import com.nackie.ownexpenses.presentation.quickentry.QuickEntryViewModel
import com.nackie.ownexpenses.presentation.settings.SettingsViewModel
import com.nackie.ownexpenses.presentation.statistics.StatisticsViewModel
import com.nackie.ownexpenses.presentation.widget.WidgetSnapshotSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
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

        single<TodayProvider> { SystemTodayProvider(get()) }
        single { PeriodSelection(get()) }

        single { BackupService(get(), get(), get()) }
        single { WidgetSnapshotSource(get(), get(), get()) }

        single { DefaultCategorySeeder(get(), get(), get()) }
        single { AppInitializer(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }

        viewModelOf(::HomeViewModel)
        viewModelOf(::QuickEntryViewModel)
        viewModelOf(::HistoryViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::StatisticsViewModel)
        viewModelOf(::CategoriesViewModel)
        viewModelOf(::AppViewModel)
    }

internal expect val platformModule: Module
