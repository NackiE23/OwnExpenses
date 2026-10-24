package com.nackie.ownexpenses.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nackie.ownexpenses.domain.model.AppSettings
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Enum values are stored under stable keys, so renaming a constant never loses a setting. */
internal class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        dataStore.data
            .map { preferences ->
                AppSettings(
                    csvDelimiter = preferences[CSV_DELIMITER].toCsvDelimiter(),
                    themeMode = preferences[THEME_MODE].toThemeMode(),
                )
            }.distinctUntilChanged()

    override suspend fun setCsvDelimiter(delimiter: CsvDelimiter) {
        dataStore.edit { it[CSV_DELIMITER] = delimiter.key }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.key }
    }

    override suspend fun isDefaultCategoriesSeeded(): Boolean =
        dataStore.data.first()[DEFAULT_CATEGORIES_SEEDED] == true

    override suspend fun markDefaultCategoriesSeeded() {
        dataStore.edit { it[DEFAULT_CATEGORIES_SEEDED] = true }
    }

    private companion object {
        val CSV_DELIMITER = stringPreferencesKey("csv_delimiter")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_CATEGORIES_SEEDED = booleanPreferencesKey("default_categories_seeded")
    }
}

private val CsvDelimiter.key: String
    get() =
        when (this) {
            CsvDelimiter.SEMICOLON -> "semicolon"
            CsvDelimiter.COMMA -> "comma"
        }

private fun String?.toCsvDelimiter(): CsvDelimiter =
    CsvDelimiter.entries.firstOrNull { it.key == this } ?: AppSettings().csvDelimiter

private val ThemeMode.key: String
    get() =
        when (this) {
            ThemeMode.SYSTEM -> "system"
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
        }

private fun String?.toThemeMode(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.key == this } ?: AppSettings().themeMode
