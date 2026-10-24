package com.nackie.ownexpenses.presentation.widget

import app.cash.turbine.test
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.repository.RoomCategoryRepository
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.presentation.FakeTodayProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetSnapshotSourceTest {
    private lateinit var database: AppDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var source: WidgetSnapshotSource
    private val today = LocalDate(2026, 10, 24)

    @BeforeTest
    fun setUp() {
        database = createTestDatabase()
        transactions = RoomTransactionRepository(database, MutableClock())
        source = WidgetSnapshotSource(RoomCategoryRepository(database), transactions, FakeTodayProvider(today))
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun showsBalanceAndThreeMostUsedExpenseCategories() =
        runTest {
            val salary = database.insertCategory("Salary", TransactionType.INCOME)
            val ids = (0 until 5).map { database.insertCategory("E$it", TransactionType.EXPENSE, sortOrder = it) }
            add(salary, 1000_00)
            add(ids[3], 10_00)
            add(ids[3], 10_00)
            add(ids[4], 10_00)

            val snapshot = source.current()

            assertEquals(970_00, snapshot.balanceMinor)
            // E3 used twice, E4 once, then user order for the rest.
            assertEquals(listOf("E3", "E4", "E0"), snapshot.categories.map { it.name })
        }

    @Test
    fun emitsAgainWhenARecordIsAdded() =
        runTest {
            val cafe = database.insertCategory("Cafe", TransactionType.EXPENSE)

            source.observe().test {
                assertEquals(0, awaitItem().balanceMinor)
                add(cafe, 89_00)
                var snapshot = awaitItem()
                while (snapshot.balanceMinor == 0L) snapshot = awaitItem()
                assertEquals(-89_00, snapshot.balanceMinor)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun add(
        categoryId: Long,
        amountMinor: Long,
    ) = transactions.addTransaction(TransactionInput(categoryId, amountMinor, today))
}
