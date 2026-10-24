package com.nackie.ownexpenses.domain.repository

import com.nackie.ownexpenses.domain.model.AppSettings
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setCsvDelimiter(delimiter: CsvDelimiter)

    suspend fun setThemeMode(mode: ThemeMode)

    /** Set once default categories were created, so deleting them all never recreates them. */
    suspend fun isDefaultCategoriesSeeded(): Boolean

    suspend fun markDefaultCategoriesSeeded()
}
