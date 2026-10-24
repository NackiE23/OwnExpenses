package com.nackie.ownexpenses

import com.nackie.ownexpenses.data.repository.DefaultCategorySeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One-time startup work shared by all entry points (app, widget). Call once per process. */
class AppInitializer internal constructor(
    private val seeder: DefaultCategorySeeder,
    private val scope: CoroutineScope,
) {
    fun initialize() {
        scope.launch { seeder.seedIfNeeded() }
    }
}
