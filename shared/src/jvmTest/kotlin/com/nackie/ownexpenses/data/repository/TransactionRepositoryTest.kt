package com.nackie.ownexpenses.data.repository

import app.cash.turbine.test
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class TransactionRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTransactionRepository
    private val clock = MutableClock()

    private var salary = 0L
    private var groceries = 0L
    private var transport = 0L
    private var taxi = 0L
    private var cafe = 0L

    @BeforeTest
    fun setUp() =
        runTest {
            database = createTestDatabase()
            repository = RoomTransactionRepository(database, clock)
            salary = database.insertCategory("Salary", TransactionType.INCOME)
            groceries = database.insertCategory("Groceries", TransactionType.EXPENSE)
            transport = database.insertCategory("Transport", TransactionType.EXPENSE)
            taxi = database.insertCategory("Taxi", TransactionType.EXPENSE, parentId = transport)
            cafe = database.insertCategory("Cafe", TransactionType.EXPENSE)
        }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun balanceFollowsSpecExample() =
        runTest {
            // docs/TZ.md: income 1000 on the 1st, 777 of expenses during the month, balance 223.
            add(salary, 1000_00, OCT_1)
            add(groceries, 300_00, LocalDate(2026, 10, 3))
            add(taxi, 150_00, LocalDate(2026, 10, 9))
            add(cafe, 327_00, LocalDate(2026, 10, 17))

            assertEquals(223_00, repository.observeBalance(LocalDate(2026, 10, 31)).first())
        }

    @Test
    fun balanceIsZeroWithoutTransactions() =
        runTest {
            assertEquals(0, repository.observeBalance(OCT_1).first())
        }

    @Test
    fun balanceCanBeNegative() =
        runTest {
            add(salary, 100_00, OCT_1)
            add(groceries, 250_50, OCT_1)

            assertEquals(-150_50, repository.observeBalance(OCT_1).first())
        }

    @Test
    fun futureTransactionsCountOnlyFromTheirDate() =
        runTest {
            add(salary, 1000_00, OCT_1)
            add(groceries, 400_00, LocalDate(2026, 10, 20))

            assertEquals(1000_00, repository.observeBalance(LocalDate(2026, 10, 19)).first())
            assertEquals(600_00, repository.observeBalance(LocalDate(2026, 10, 20)).first())
        }

    @Test
    fun balanceIsNotResetAtMonthStart() =
        runTest {
            add(salary, 1000_00, LocalDate(2026, 9, 1))
            add(groceries, 300_00, LocalDate(2026, 9, 30))

            assertEquals(700_00, repository.observeBalance(OCT_1).first())
        }

    @Test
    fun balanceUpdatesImmediatelyOnAddEditDeleteAndRestore() =
        runTest {
            repository.observeBalance(OCT_1).test {
                assertEquals(0, awaitItem())

                add(salary, 1000_00, OCT_1)
                assertEquals(1000_00, awaitItem())

                val expense = add(groceries, 200_00, OCT_1)
                assertEquals(800_00, awaitItem())

                repository.updateTransaction(expense, TransactionInput(groceries, 250_00, OCT_1))
                assertEquals(750_00, awaitItem())

                val deleted = assertNotNull(repository.deleteTransaction(expense))
                assertEquals(1000_00, awaitItem())

                repository.restoreTransaction(deleted)
                assertEquals(750_00, awaitItem())
            }
        }

    @Test
    fun typeComesFromCategory() =
        runTest {
            val income = add(salary, 10_00, OCT_1)
            val expense = add(taxi, 10_00, OCT_1)

            assertEquals(TransactionType.INCOME, repository.getTransaction(income)?.type)
            assertEquals(TransactionType.EXPENSE, repository.getTransaction(expense)?.type)
        }

    @Test
    fun movingToCategoryOfOtherTypeChangesType() =
        runTest {
            val id = add(groceries, 50_00, OCT_1)

            repository.updateTransaction(id, TransactionInput(salary, 50_00, OCT_1))

            assertEquals(TransactionType.INCOME, repository.getTransaction(id)?.type)
            assertEquals(50_00, repository.observeBalance(OCT_1).first())
        }

    @Test
    fun descriptionIsTrimmedAndBlankBecomesNull() =
        runTest {
            val withText = add(cafe, 89_00, OCT_1, description = "  Croissants ")
            val blank = add(cafe, 89_00, OCT_1, description = "   ")

            assertEquals("Croissants", repository.getTransaction(withText)?.description)
            assertNull(repository.getTransaction(blank)?.description)
        }

    @Test
    fun timestampsComeFromClock() =
        runTest {
            val id = add(cafe, 89_00, OCT_1)
            val created = clock.now

            clock.now = Instant.parse("2026-10-25T08:00:00Z")
            repository.updateTransaction(id, TransactionInput(cafe, 99_00, OCT_1))

            val transaction = assertNotNull(repository.getTransaction(id))
            assertEquals(created, transaction.createdAt)
            assertEquals(clock.now, transaction.updatedAt)
        }

    @Test
    fun restoreKeepsIdAndTimestamps() =
        runTest {
            val id = add(cafe, 89_00, OCT_1, description = "Mac")
            val original = assertNotNull(repository.getTransaction(id))

            repository.deleteTransaction(id)
            assertNull(repository.getTransaction(id))
            repository.restoreTransaction(original)

            assertEquals(original, repository.getTransaction(id))
        }

    @Test
    fun deletingMissingTransactionReturnsNull() =
        runTest {
            assertNull(repository.deleteTransaction(42))
        }

    @Test
    fun unknownCategoryIsRejected() =
        runTest {
            assertFailsWith<IllegalArgumentException> { add(categoryId = 999, amountMinor = 10_00, date = OCT_1) }
            assertEquals(0, repository.observeBalance(OCT_1).first())
        }

    @Test
    fun nonPositiveAmountIsRejected() {
        assertFailsWith<IllegalArgumentException> { TransactionInput(groceries, 0, OCT_1) }
        assertFailsWith<IllegalArgumentException> { TransactionInput(groceries, -5, OCT_1) }
    }

    @Test
    fun databaseRefusesToDeleteCategoryInUse() =
        runTest {
            add(groceries, 10_00, OCT_1)

            assertFailsWith<Exception> { database.categoryDao().delete(groceries) }
        }

    private suspend fun add(
        categoryId: Long,
        amountMinor: Long,
        date: LocalDate,
        description: String? = null,
    ): Long = repository.addTransaction(TransactionInput(categoryId, amountMinor, date, description))

    private companion object {
        val OCT_1 = LocalDate(2026, 10, 1)
    }
}
