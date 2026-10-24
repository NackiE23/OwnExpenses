package com.nackie.ownexpenses.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nackie.ownexpenses.domain.model.AppSettings
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataStoreSettingsRepositoryTest {
    private val directory = Files.createTempDirectory("settings-test").toFile().apply { deleteOnExit() }

    private fun TestScope.createDataStore() =
        PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) {
            directory.resolve("settings.preferences_pb").toOkioPath()
        }

    @Test
    fun defaultsMatchSpec() =
        runTest {
            val settings = DataStoreSettingsRepository(createDataStore()).settings.first()

            assertEquals(AppSettings(), settings)
            assertEquals("UAH", settings.currencyCode)
            assertEquals(CsvDelimiter.SEMICOLON, settings.csvDelimiter)
            assertEquals(',', settings.csvDelimiter.decimalSeparator)
            assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        }

    @Test
    fun changesArePersisted() =
        runTest {
            val repository = DataStoreSettingsRepository(createDataStore())

            repository.setCsvDelimiter(CsvDelimiter.COMMA)
            repository.setThemeMode(ThemeMode.DARK)

            val settings = repository.settings.first()
            assertEquals(CsvDelimiter.COMMA, settings.csvDelimiter)
            assertEquals('.', settings.csvDelimiter.decimalSeparator)
            assertEquals(ThemeMode.DARK, settings.themeMode)
        }

    @Test
    fun unknownStoredValuesFallBackToDefaults() =
        runTest {
            val dataStore = createDataStore()
            dataStore.edit {
                it[stringPreferencesKey("csv_delimiter")] = "tab"
                it[stringPreferencesKey("theme_mode")] = "sepia"
            }

            assertEquals(AppSettings(), DataStoreSettingsRepository(dataStore).settings.first())
        }

    @Test
    fun seededFlagStartsUnsetAndSticks() =
        runTest {
            val repository = DataStoreSettingsRepository(createDataStore())

            assertFalse(repository.isDefaultCategoriesSeeded())
            repository.markDefaultCategoriesSeeded()
            assertTrue(repository.isDefaultCategoriesSeeded())
        }
}
