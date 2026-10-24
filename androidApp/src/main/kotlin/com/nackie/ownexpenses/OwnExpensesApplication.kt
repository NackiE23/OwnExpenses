package com.nackie.ownexpenses

import android.app.Application
import com.nackie.ownexpenses.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OwnExpensesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OwnExpensesApplication)
            modules(sharedModule)
        }
    }
}
