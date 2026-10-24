package com.nackie.ownexpenses

import android.app.Application
import com.nackie.ownexpenses.di.sharedModule
import com.nackie.ownexpenses.ui.di.uiModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OwnExpensesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OwnExpensesApplication)
            modules(sharedModule, uiModule)
        }
        get<AppInitializer>().initialize()
    }
}
