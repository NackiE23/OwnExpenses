package com.nackie.ownexpenses

import android.app.Application
import com.nackie.ownexpenses.di.sharedModule
import com.nackie.ownexpenses.sync.SyncConfig
import com.nackie.ownexpenses.sync.SyncScheduler
import com.nackie.ownexpenses.ui.di.uiModule
import com.nackie.ownexpenses.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class OwnExpensesApplication : Application() {
    /** Work tied to the process, not to any screen. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OwnExpensesApplication)
            modules(sharedModule, uiModule, appModule)
        }
        get<AppInitializer>().initialize()
        WidgetUpdater(this, get()).start(applicationScope)
        if (get<SyncConfig>().isAvailable) SyncScheduler(this, get()).start(applicationScope)
    }
}

/** The OwnBudget server address is baked into the build (env OWNEXPENSES_SYNC_URL, see docs/RELEASE.md). */
private val appModule =
    module {
        single { SyncConfig(serverUrl = BuildConfig.SYNC_SERVER_URL, appVersion = BuildConfig.VERSION_NAME) }
    }
