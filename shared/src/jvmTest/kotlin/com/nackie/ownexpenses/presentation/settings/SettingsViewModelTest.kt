package com.nackie.ownexpenses.presentation.settings

import app.cash.turbine.test
import com.nackie.ownexpenses.backup.BackupError
import com.nackie.ownexpenses.backup.BackupService
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
import com.nackie.ownexpenses.importing.SumlioCategoryNames
import com.nackie.ownexpenses.importing.SumlioImportError
import com.nackie.ownexpenses.importing.SumlioImportService
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
        viewModel =
            SettingsViewModel(
                settings,
                transactions,
                BackupService(database, settings, MutableClock()),
                SumlioImportService(database),
                PeriodSelection(today),
                today,
            ).tracked()
    }

    @AfterTest
    fun tearDown() {
        cancelViewModels()
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

                val ready = assertIs<SettingsEvent.FileReady>(awaitItem())
                assertEquals("ownexpenses_2026-10-01_2026-10-31.csv", ready.file.fileName)
                assertEquals("text/csv", ready.file.mimeType)
                assertEquals(FileKind.CSV_EXPORT, ready.file.kind)
                assertEquals(1, ready.file.recordCount)
                assertEquals(ExportTarget.SHARE, ready.target)
                assertTrue(
                    ready.file.content.contains("2026-10-17,expense,Кафе,,327.00,UAH,Круасани\r\n"),
                    ready.file.content,
                )
            }
        }

    @Test
    fun emptyPeriodExportsHeaderOnly() =
        runTest {
            viewModel.events.test {
                viewModel.export(Period.AllTime, ExportTarget.SAVE)

                val ready = assertIs<SettingsEvent.FileReady>(awaitItem())
                assertEquals(0, ready.file.recordCount)
                assertEquals(
                    2,
                    ready.file.content
                        .split("\r\n")
                        .size,
                )
            }
        }

    @Test
    fun backupThenRestoreAfterConfirmation() =
        runTest {
            val cafe = database.insertCategory("Кафе", TransactionType.EXPENSE)
            transactions.addTransaction(TransactionInput(cafe, 89_00, LocalDate(2026, 10, 17)))

            viewModel.events.test {
                viewModel.createBackup(ExportTarget.SAVE)
                val ready = assertIs<SettingsEvent.FileReady>(awaitItem())
                assertEquals(FileKind.BACKUP, ready.file.kind)
                assertEquals("application/json", ready.file.mimeType)

                // Data changes after the backup; restoring brings back the backed-up state.
                transactions.addTransaction(TransactionInput(cafe, 1_00, LocalDate(2026, 10, 18)))
                viewModel.openBackup(ready.file.content)
                viewModel.uiState.test {
                    var state = awaitItem()
                    while (state.pendingRestore == null) state = awaitItem()
                    assertEquals(1, state.pendingRestore.transactionCount)
                    cancelAndIgnoreRemainingEvents()
                }

                viewModel.confirmRestore()
                assertEquals(SettingsEvent.Restored(transactionCount = 1), awaitItem())
            }
            assertEquals(1, database.transactionDao().getAll().size)
        }

    @Test
    fun brokenFileIsRejectedWithoutTouchingData() =
        runTest {
            val cafe = database.insertCategory("Кафе", TransactionType.EXPENSE)
            transactions.addTransaction(TransactionInput(cafe, 89_00, LocalDate(2026, 10, 17)))

            viewModel.events.test {
                viewModel.openBackup("date;type;category")
                assertEquals(SettingsEvent.RestoreFailed(BackupError.NOT_A_BACKUP), awaitItem())
            }
            assertEquals(null, viewModel.uiState.value.pendingRestore)
            assertEquals(1, database.transactionDao().getAll().size)
        }

    @Test
    fun sumlioImportWaitsForConfirmation() =
        runTest {
            val csv =
                "Date (UTC),Type,From account / from category,From subcategory,To account / to category," +
                    "To subcategory,Amount 1,Currency 1,Amount 2,Currency 2,Comment,Tags\n" +
                    "2026-10-01T08:00:00.000Z,Expense,Card,\"\",Cafe,\"\",89.0,UAH,89.0,UAH,\"\",\"\"\n"
            val names = SumlioCategoryNames(refunds = "Refunds", other = "Other")

            viewModel.events.test {
                viewModel.openSumlioFile(csv, names)
                viewModel.uiState.test {
                    var state = awaitItem()
                    while (state.pendingImport == null) state = awaitItem()
                    assertEquals(1, state.pendingImport.recordCount)
                    assertEquals(-89_00, state.pendingImport.balanceChangeMinor)
                    cancelAndIgnoreRemainingEvents()
                }
                assertTrue(database.transactionDao().getAll().isEmpty())

                viewModel.confirmImport()
                assertEquals(SettingsEvent.Imported(transactionCount = 1), awaitItem())

                viewModel.openSumlioFile(csv, names)
                assertEquals(SettingsEvent.ImportFailed(SumlioImportError.NOTHING_TO_IMPORT), awaitItem())
            }
            assertEquals(1, database.transactionDao().getAll().size)
        }

    @Test
    fun themeChangeReachesTheAppViewModel() =
        runTest {
            val app = AppViewModel(settings).tracked()

            viewModel.setThemeMode(ThemeMode.DARK)

            assertEquals(ThemeMode.DARK, settings.state.value.themeMode)
            app.themeMode.test {
                assertEquals(ThemeMode.DARK, awaitItem())
            }
        }
}
