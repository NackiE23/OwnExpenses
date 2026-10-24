package com.nackie.ownexpenses.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.nackie.ownexpenses.AppInitializer
import com.nackie.ownexpenses.di.sharedModule
import com.nackie.ownexpenses.ui.di.uiModule
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/** Entry point for the iOS app (stage 5). Call [initKoin] once at app launch first. */
@Suppress("FunctionName", "ktlint:standard:function-naming")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

fun initKoin() {
    val koin = startKoin { modules(sharedModule, uiModule) }.koin
    koin.get<AppInitializer>().initialize()
}
