package com.nackie.ownexpenses.data

import com.nackie.ownexpenses.domain.model.AppSettings
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeSettingsRepository : SettingsRepository {
    var seeded = false
    val state = MutableStateFlow(AppSettings())

    override val settings: Flow<AppSettings> = state

    override suspend fun setCsvDelimiter(delimiter: CsvDelimiter) {
        state.value = state.value.copy(csvDelimiter = delimiter)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun isDefaultCategoriesSeeded(): Boolean = seeded

    override suspend fun markDefaultCategoriesSeeded() {
        seeded = true
    }
}
