package com.nackie.ownexpenses.presentation.settings

import app.cash.turbine.test
import com.nackie.ownexpenses.data.FakeSettingsRepository
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.PeriodSelection
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.presentation.AppViewModel
import com.nackie.ownexpenses.presentation.FakeTodayProvider
import com.nackie.ownexpenses.presentation.MainDispatcherTest
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class SettingsViewModelTest : MainDispatcherTest() {
    private lateinit var database: AppDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var viewModel: SettingsViewModel
    private val settings = FakeSettingsRepository()
    private val today = FakeTodayProvider(LocalDate(2026, 10, 24))

    @BeforeTest
    fun setUp() {
        database = createTestDatabase()
        transactions = RoomTransactionRepository(database, MutableClock())
        viewModel = SettingsViewModel(settings, transactions, PeriodSelection(today), today)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportUsesPeriodAndDelimiterSetting() =
        runTest {
            val cafe = database.insertCategory("Кафе", TransactionType.EXPENSE)
            transactions.addTransaction(TransactionInput(cafe, 327_00, LocalDate(2026, 10, 17), "Круасани"))
            transactions.addTransaction(TransactionInput(cafe, 10_00, LocalDate(2026, 9, 1)))
            settings.setCsvDelimiter(CsvDelimiter.COMMA)

            viewModel.events.test {
                viewModel.export(Period.monthOf(LocalDate(2026, 10, 1)), ExportTarget.SHARE)

                val ready = assertIs<SettingsEvent.ExportReady>(awaitItem())
                assertEquals("ownexpenses_2026-10-01_2026-10-31.csv", ready.fileName)
                assertEquals(1, ready.recordCount)
                assertEquals(ExportTarget.SHARE, ready.target)
                assertTrue(ready.content.contains("2026-10-17,expense,Кафе,,327.00,UAH,Круасани\r\n"), ready.content)
            }
        }

    @Test
    fun emptyPeriodExportsHeaderOnly() =
        runTest {
            viewModel.events.test {
                viewModel.export(Period.AllTime, ExportTarget.SAVE)

                val ready = assertIs<SettingsEvent.ExportReady>(awaitItem())
                assertEquals(0, ready.recordCount)
                assertEquals(2, ready.content.split("\r\n").size)
            }
        }

    @Test
    fun themeChangeReachesTheAppViewModel() =
        runTest {
            val app = AppViewModel(settings)

            viewModel.setThemeMode(ThemeMode.DARK)

            assertEquals(ThemeMode.DARK, settings.state.value.themeMode)
            app.themeMode.test {
                assertEquals(ThemeMode.DARK, awaitItem())
            }
        }
}
