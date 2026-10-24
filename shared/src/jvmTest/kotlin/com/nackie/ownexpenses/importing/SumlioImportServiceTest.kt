package com.nackie.ownexpenses.importing

import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SumlioImportServiceTest {
    private lateinit var database: AppDatabase
    private lateinit var service: SumlioImportService
    private val names = SumlioCategoryNames(refunds = "Повернення", other = "Інше")
    private val kyiv = TimeZone.of("Europe/Kyiv")

    @BeforeTest
    fun setUp() {
        database = createTestDatabase()
        service = SumlioImportService(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun summaryCountsWhatWillBeAdded() =
        runTest {
            val summary = readReady(SAMPLE).summary

            assertEquals(5, summary.recordCount)
            // Еда, Транспорт + таксі, Інше, Зарплата, Повернення.
            assertEquals(6, summary.newCategoryCount)
            assertEquals(LocalDate(2026, 10, 1), summary.firstDate)
            assertEquals(LocalDate(2026, 10, 3), summary.lastDate)
            // 10 000 + 343 in, 6 920 + 150 + 14 out.
            assertEquals(3_259_00, summary.balanceChangeMinor)
            assertEquals(2, summary.reversedCount)
            assertEquals(2, summary.skippedCount)
            assertEquals(0, summary.duplicateCount)
        }

    @Test
    fun importMapsRecordsOntoCategories() =
        runTest {
            assertTrue(service.import(readReady(SAMPLE)))

            val categories = database.categoryDao().getAll()
            val records =
                database.transactionDao().getAll().associate { record ->
                    val category = categories.single { it.id == record.categoryId }
                    category.path(categories) to record
                }
            assertEquals(
                setOf("Еда", "Транспорт / таксі", "Зарплата", "Повернення", "Інше"),
                records.keys,
            )

            val food = records.getValue("Еда")
            assertEquals(6_920_00, food.amountMinor)
            assertEquals(TransactionType.EXPENSE, food.type)
            assertEquals("суши", food.description)

            // 22:30 UTC is already the next day in Kyiv; comment and tag both kept.
            val taxi = records.getValue("Транспорт / таксі")
            assertEquals(LocalDate(2026, 10, 2), taxi.date)
            assertEquals("тонік, умивалка · Потяг", taxi.description)
            assertEquals(1_790_893_800_000, taxi.createdAt)

            val refund = records.getValue("Повернення")
            assertEquals(TransactionType.INCOME, refund.type)
            assertEquals(343_00, refund.amountMinor)
            assertEquals("Транспорт · вернув квитки", refund.description)

            val correction = records.getValue("Інше")
            assertEquals(TransactionType.EXPENSE, correction.type)
            assertEquals(14_00, correction.amountMinor)
            assertEquals("Зарплата", correction.description)
        }

    @Test
    fun existingCategoriesAreMatchedByNameIgnoringCase() =
        runTest {
            val transport = database.insertCategory("ТРАНСПОРТ", TransactionType.EXPENSE, sortOrder = 0)
            val taxi = database.insertCategory("Таксі", TransactionType.EXPENSE, parentId = transport)
            database.insertCategory("Інше", TransactionType.EXPENSE, sortOrder = 1)
            // Archived categories are not reused: the records would be hidden from the main screen.
            database.insertCategory("Еда", TransactionType.EXPENSE, sortOrder = 2, isArchived = true)

            val file = readReady(SAMPLE)
            assertEquals(3, file.summary.newCategoryCount)
            service.import(file)

            val records = database.transactionDao().getAll()
            assertEquals(taxi, records.single { it.amountMinor == 150_00L }.categoryId)
            val food = database.categoryDao().getAll().filter { it.name == "Еда" }
            assertEquals(2, food.size)
            assertEquals(food.single { !it.isArchived }.id, records.single { it.amountMinor == 6_920_00L }.categoryId)
        }

    @Test
    fun newCategoriesFollowTheUsersOwnMostUsedFirst() =
        runTest {
            database.insertCategory("Кафе", TransactionType.EXPENSE, sortOrder = 4)
            val csv =
                HEADER + "\n" +
                    row("2026-10-01T08:00:00.000Z", "Expense", "Карта", "", "Одежда", "", "10.0") +
                    row("2026-10-01T08:00:01.000Z", "Expense", "Карта", "", "Еда", "", "10.0") +
                    row("2026-10-01T08:00:02.000Z", "Expense", "Карта", "", "Еда", "", "20.0")

            service.import(readReady(csv))

            val expenses =
                database
                    .categoryDao()
                    .getAll()
                    .filter { it.type == TransactionType.EXPENSE }
                    .sortedBy { it.sortOrder }
            assertEquals(listOf("Кафе", "Еда", "Одежда"), expenses.map { it.name })
            assertEquals(listOf(4, 5, 6), expenses.map { it.sortOrder })
            assertNotEquals(expenses[1].color, expenses[2].color)
        }

    @Test
    fun importingTheSameFileTwiceAddsNothing() =
        runTest {
            service.import(readReady(SAMPLE))

            assertEquals(
                SumlioReadResult.Failed(SumlioImportError.NOTHING_TO_IMPORT),
                service.read(SAMPLE, "UAH", names, kyiv),
            )
            assertEquals(5, database.transactionDao().getAll().size)
        }

    @Test
    fun partlyImportedFileAddsOnlyTheRest() =
        runTest {
            val first = HEADER + "\n" + row("2026-10-01T08:00:00.000Z", "Expense", "Карта", "", "Еда", "", "10.0")
            service.import(readReady(first))

            val summary =
                readReady(
                    first + row("2026-10-02T08:00:00.000Z", "Expense", "Карта", "", "Еда", "", "5.0"),
                ).summary
            assertEquals(1, summary.recordCount)
            assertEquals(1, summary.duplicateCount)
            assertEquals(0, summary.newCategoryCount)
        }

    @Test
    fun otherCsvIsRejected() =
        runTest {
            val ownExport = "\uFEFFdate;type;category;subcategory;amount;currency;description\r\n"
            assertEquals(
                SumlioReadResult.Failed(SumlioImportError.NOT_SUMLIO),
                service.read(ownExport, "UAH", names, kyiv),
            )
            assertEquals(SumlioReadResult.Failed(SumlioImportError.NOT_SUMLIO), service.read("", "UAH", names, kyiv))
        }

    @Test
    fun unreadableRowIsReportedWithItsLine() =
        runTest {
            val csv =
                HEADER + "\n" +
                    row("2026-10-01T08:00:00.000Z", "Expense", "Карта", "", "Еда", "", "10.0") +
                    row("2026-10-01T08:00:01.000Z", "Expense", "Карта", "", "Еда", "", "ten")

            assertEquals(
                SumlioReadResult.Failed(SumlioImportError.INVALID_ROW, line = 3),
                service.read(csv, "UAH", names, kyiv),
            )
            assertTrue(database.transactionDao().getAll().isEmpty())
        }

    private suspend fun readReady(csv: String): SumlioImport =
        assertIs<SumlioReadResult.Ready>(service.read(csv, "UAH", names, kyiv)).file

    private fun CategoryEntity.path(all: List<CategoryEntity>): String =
        parentId?.let { parent -> all.single { it.id == parent }.name + " / " + name } ?: name

    private companion object {
        const val HEADER =
            "Date (UTC),Type,From account / from category,From subcategory,To account / to category," +
                "To subcategory,Amount 1,Currency 1,Amount 2,Currency 2,Commission,Commission currency,Tax," +
                "Tax currency,Comment,Tags"

        fun row(
            date: String,
            type: String,
            from: String,
            fromSub: String,
            to: String,
            toSub: String,
            amount: String,
            currency: String = "UAH",
            comment: String = "\"\"",
            tags: String = "\"\"",
        ) = "$date,$type,$from,\"$fromSub\",$to,\"$toSub\",$amount,$currency,$amount,$currency," +
            "\"\",\"\",\"\",\"\",$comment,$tags\n"

        /** Shaped like a real export: float noise, a quoted comma, refunds, a transfer, accounts at the end. */
        val SAMPLE =
            HEADER + "\n" +
                row(
                    "2026-10-01T08:00:00.000Z",
                    "Expense",
                    "Карта",
                    "",
                    "Еда",
                    "",
                    "6919.999999999999",
                    comment = "суши",
                ) +
                row(
                    "2026-10-01T22:30:00.000Z",
                    "Expense",
                    "Карта",
                    "",
                    "Транспорт",
                    "таксі ",
                    "150.0",
                    comment = "\"тонік, умивалка\"",
                    tags = "Потяг",
                ) +
                row("2026-10-02T09:00:00.000Z", "Income", "Зарплата", "", "Карта", "", "10000.0") +
                row(
                    "2026-10-03T09:00:00.000Z",
                    "Expense",
                    "Карта",
                    "",
                    "Транспорт",
                    "",
                    "-343.0",
                    comment = "вернув квитки",
                ) +
                row("2026-10-03T10:00:00.000Z", "Income", "Зарплата", "", "Карта", "", "-14.0") +
                row("2026-10-04T10:00:00.000Z", "Transfer", "Карта", "", "Наличные", "", "100.0") +
                row("2026-10-05T10:00:00.000Z", "Expense", "Карта", "", "Еда", "", "5.0", currency = "USD") +
                "\"\"\n\"\"\n\"\"\n" +
                "Name,Currency,Balance,Goal,Current debt,Total debt\n" +
                "Карта,UAH,773.0,\"\",\"\",\"\"\n"
    }
}
