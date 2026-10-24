package com.nackie.ownexpenses.backup

import com.nackie.ownexpenses.data.FakeSettingsRepository
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.repository.RoomCategoryRepository
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class BackupServiceTest {
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase
    private val clock = MutableClock(Instant.parse("2026-10-24T10:00:00Z"))
    private val sourceSettings = FakeSettingsRepository()
    private val targetSettings = FakeSettingsRepository()

    @BeforeTest
    fun setUp() {
        source = createTestDatabase()
        target = createTestDatabase()
    }

    @AfterTest
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun restoreReproducesEverything() =
        runTest {
            fillSource()
            sourceSettings.setCsvDelimiter(CsvDelimiter.COMMA)
            sourceSettings.setThemeMode(ThemeMode.DARK)

            val file = BackupService(source, sourceSettings, clock).createBackup()
            val targetService = BackupService(target, targetSettings, clock)
            val backup = assertIs<BackupReadResult.Valid>(targetService.read(file.content)).backup
            targetService.restore(backup)

            assertEquals("ownexpenses_backup_2026-10-24.json", file.fileName)
            assertEquals(4, file.categoryCount)
            assertEquals(3, file.transactionCount)
            assertEquals(source.categoryDao().getAll(), target.categoryDao().getAll())
            assertEquals(source.transactionDao().getAll(), target.transactionDao().getAll())
            assertEquals(CsvDelimiter.COMMA, targetSettings.state.value.csvDelimiter)
            assertEquals(ThemeMode.DARK, targetSettings.state.value.themeMode)
            assertTrue(targetSettings.seeded, "Defaults must not be seeded over restored categories")

            val day = LocalDate(2026, 10, 31)
            assertEquals(
                RoomTransactionRepository(source, clock).observeBalance(day).first(),
                RoomTransactionRepository(target, clock).observeBalance(day).first(),
            )
        }

    @Test
    fun restoreReplacesExistingData() =
        runTest {
            fillSource()
            val file = BackupService(source, sourceSettings, clock).createBackup()

            // Like a real install: categories with subcategories, and records in them. A plain
            // "DELETE FROM categories" fails here on the RESTRICT parent key.
            val other = target.insertCategory("Other", TransactionType.EXPENSE)
            val otherSub = target.insertCategory("Other sub", TransactionType.EXPENSE, parentId = other)
            val targetTransactions = RoomTransactionRepository(target, clock)
            targetTransactions.addTransaction(TransactionInput(otherSub, 1_00, LocalDate(2026, 1, 1)))

            val service = BackupService(target, targetSettings, clock)
            assertTrue(service.restore(assertIs<BackupReadResult.Valid>(service.read(file.content)).backup))

            assertEquals(source.categoryDao().getAll(), target.categoryDao().getAll())
            assertEquals(3, target.transactionDao().getAll().size)
        }

    @Test
    fun archivedCategoriesSurvive() =
        runTest {
            fillSource()
            val categories = RoomCategoryRepository(source)
            val cafe = source.categoryDao().getAll().first { it.name == "Cafe" }
            categories.setArchived(cafe.id, true)

            val file = BackupService(source, sourceSettings, clock).createBackup()
            val service = BackupService(target, targetSettings, clock)
            service.restore(assertIs<BackupReadResult.Valid>(service.read(file.content)).backup)

            assertTrue(
                target
                    .categoryDao()
                    .getAll()
                    .first { it.name == "Cafe" }
                    .isArchived,
            )
        }

    @Test
    fun emptyDatabaseMakesAValidBackup() =
        runTest {
            val file = BackupService(source, sourceSettings, clock).createBackup()

            val backup =
                assertIs<BackupReadResult.Valid>(
                    BackupService(target, targetSettings, clock).read(file.content),
                ).backup
            assertEquals(0, backup.categoryCount)
        }

    @Test
    fun fileContainsReadableFormatMarkers() =
        runTest {
            fillSource()
            val content = BackupService(source, sourceSettings, clock).createBackup().content

            assertTrue(content.contains("\"format\": \"ownexpenses-backup\""), content)
            assertTrue(content.contains("\"version\": 1"), content)
            assertTrue(content.contains("\"date\": \"2026-10-03\""), content)
            assertTrue(content.contains("\"type\": \"expense\""), content)
        }

    @Test
    fun rejectsFilesThatAreNotOurBackups() {
        val service = BackupService(target, targetSettings, clock)

        assertEquals(BackupReadResult.Invalid(BackupError.NOT_A_BACKUP), service.read("not json"))
        assertEquals(BackupReadResult.Invalid(BackupError.NOT_A_BACKUP), service.read("{\"hello\": 1}"))
        assertEquals(
            BackupReadResult.Invalid(BackupError.NOT_A_BACKUP),
            service.read(document(format = "something-else")),
        )
    }

    @Test
    fun rejectsNewerFormatVersion() {
        assertEquals(
            BackupReadResult.Invalid(BackupError.NEWER_VERSION),
            BackupService(target, targetSettings, clock).read(document(version = 2)),
        )
    }

    @Test
    fun rejectsInconsistentData() {
        val service = BackupService(target, targetSettings, clock)
        val category = """{"id":1,"name":"Cafe","type":"expense","color":"#FFD81B60","icon":"cafe","sortOrder":0}"""

        fun record(
            categoryId: Long = 1,
            type: String = "expense",
            amount: Long = 100,
            date: String = "2026-10-03",
        ) =
            """{"id":1,"type":"$type","amountMinor":$amount,"categoryId":$categoryId,"date":"$date","createdAt":0,"updatedAt":0}"""

        val broken =
            listOf(
                document(categories = category, transactions = record(categoryId = 99)),
                document(categories = category, transactions = record(type = "income")),
                document(categories = category, transactions = record(amount = 0)),
                document(categories = category, transactions = record(date = "2026-13-40")),
                document(categories = category.replace("#FFD81B60", "red")),
                document(categories = category.replace("\"expense\"", "\"transfer\"")),
                document(categories = "$category,$category"),
                document(categories = category.replace("\"id\":1,", "\"id\":2,\"parentId\":7,")),
            )

        broken.forEach { assertEquals(BackupReadResult.Invalid(BackupError.INVALID_DATA), service.read(it), it) }
    }

    @Test
    fun unknownFieldsFromNewerMinorChangesAreIgnored() {
        val result = BackupService(target, targetSettings, clock).read(document().replace("{", "{\"extra\":true,"))

        assertIs<BackupReadResult.Valid>(result)
    }

    private suspend fun fillSource() {
        val categories = RoomCategoryRepository(source)
        val transactions = RoomTransactionRepository(source, clock)
        val salary = source.insertCategory("Salary", TransactionType.INCOME)
        val transport = source.insertCategory("Transport", TransactionType.EXPENSE)
        val taxi = source.insertCategory("Taxi", TransactionType.EXPENSE, parentId = transport)
        val cafe = source.insertCategory("Cafe", TransactionType.EXPENSE, sortOrder = 1)
        categories.updateCategory(cafe, "Cafe", 0xFFD81B60.toInt(), "cafe")
        transactions.addTransaction(TransactionInput(salary, 1000_00, LocalDate(2026, 10, 1)))
        transactions.addTransaction(TransactionInput(taxi, 150_00, LocalDate(2026, 10, 3), "Airport \"Boryspil\""))
        transactions.addTransaction(TransactionInput(cafe, 89_50, LocalDate(2026, 10, 3)))
    }

    private fun document(
        format: String = BackupService.FORMAT_ID,
        version: Int = BackupService.FORMAT_VERSION,
        categories: String = "",
        transactions: String = "",
    ) = """
        {"format":"$format","version":$version,"createdAt":"2026-10-24T10:00:00Z","currencyCode":"UAH",
         "settings":{"csvDelimiter":"semicolon","themeMode":"system"},
         "categories":[$categories],"transactions":[$transactions]}
        """.trimIndent()
}
