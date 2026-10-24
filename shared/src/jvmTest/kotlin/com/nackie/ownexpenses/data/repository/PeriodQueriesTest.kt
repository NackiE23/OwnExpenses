package com.nackie.ownexpenses.data.repository

import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodTotals
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodQueriesTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTransactionRepository

    private var salary = 0L
    private var groceries = 0L
    private var transport = 0L
    private var taxi = 0L

    private val october = Period.monthOf(LocalDate(2026, 10, 1))

    @BeforeTest
    fun setUp() =
        runTest {
            database = createTestDatabase()
            repository = RoomTransactionRepository(database, MutableClock())
            salary = database.insertCategory("Salary", TransactionType.INCOME)
            groceries = database.insertCategory("Groceries", TransactionType.EXPENSE)
            transport = database.insertCategory("Transport", TransactionType.EXPENSE)
            taxi = database.insertCategory("Taxi", TransactionType.EXPENSE, parentId = transport)
        }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun periodTotalsStartFromZeroEachMonthWhileBalanceDoesNot() =
        runTest {
            add(salary, 1000_00, LocalDate(2026, 9, 1))
            add(groceries, 300_00, LocalDate(2026, 9, 30))
            add(salary, 500_00, LocalDate(2026, 10, 1))
            add(groceries, 120_00, LocalDate(2026, 10, 31))

            assertEquals(
                PeriodTotals(expenseMinor = 120_00, incomeMinor = 500_00),
                repository.observePeriodTotals(october).first(),
            )
            assertEquals(
                PeriodTotals(expenseMinor = 300_00, incomeMinor = 1000_00),
                repository.observePeriodTotals(october.shift(-1)).first(),
            )
            assertEquals(1080_00, repository.observeBalance(LocalDate(2026, 10, 31)).first())
        }

    @Test
    fun emptyPeriodHasZeroTotals() =
        runTest {
            add(groceries, 10_00, LocalDate(2026, 9, 30))

            assertEquals(PeriodTotals(), repository.observePeriodTotals(october).first())
        }

    @Test
    fun allTimeIncludesEverything() =
        runTest {
            add(groceries, 10_00, LocalDate(2020, 1, 1))
            add(groceries, 20_00, LocalDate(2030, 1, 1))

            assertEquals(30_00, repository.observePeriodTotals(Period.AllTime).first().expenseMinor)
        }

    @Test
    fun categoryTotalsRollSubcategoriesUpToParent() =
        runTest {
            add(transport, 100_00, LocalDate(2026, 10, 2))
            add(taxi, 150_00, LocalDate(2026, 10, 9))
            add(groceries, 300_00, LocalDate(2026, 10, 3))
            add(groceries, 999_00, LocalDate(2026, 11, 1))
            add(salary, 1000_00, LocalDate(2026, 10, 1))

            assertEquals(
                mapOf(transport to 250_00L, groceries to 300_00L),
                repository.observeCategoryTotals(TransactionType.EXPENSE, october).first(),
            )
            assertEquals(
                mapOf(salary to 1000_00L),
                repository.observeCategoryTotals(TransactionType.INCOME, october).first(),
            )
        }

    @Test
    fun usageCountsRecordsPerTopLevelCategory() =
        runTest {
            add(taxi, 1_00, LocalDate(2026, 10, 9))
            add(taxi, 1_00, LocalDate(2026, 10, 10))
            add(transport, 1_00, LocalDate(2026, 10, 11))
            add(groceries, 1_00, LocalDate(2026, 10, 12))
            add(groceries, 1_00, LocalDate(2026, 8, 1))

            assertEquals(
                mapOf(transport to 3L, groceries to 1L),
                repository.observeCategoryUsage(october).first(),
            )
        }

    private suspend fun add(
        categoryId: Long,
        amountMinor: Long,
        date: LocalDate,
    ) = repository.addTransaction(TransactionInput(categoryId, amountMinor, date))
}
