package com.nackie.ownexpenses.ui.di

import com.nackie.ownexpenses.domain.model.DefaultCategoryNames
import com.nackie.ownexpenses.ui.ResourceDefaultCategoryNames
import org.koin.core.module.Module
import org.koin.dsl.module

/** UI-layer bindings the data layer depends on. Load together with `sharedModule`. */
val uiModule: Module =
    module {
        single<DefaultCategoryNames> { ResourceDefaultCategoryNames() }
    }
