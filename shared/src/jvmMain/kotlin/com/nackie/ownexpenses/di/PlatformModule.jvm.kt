package com.nackie.ownexpenses.di

import org.koin.core.module.Module
import org.koin.dsl.module

/** The JVM target only runs tests, which build their own in-memory database. */
internal actual val platformModule: Module = module { }
