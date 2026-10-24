package com.nackie.ownexpenses.data.repository

import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class TransactionDetailsQueryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTransactionRepository
    private val clock = MutableClock()

    private var cafe = 0L
    private var transport = 0L
    private var taxi = 0L

    private val october = Period.monthOf(LocalDate(2026, 10, 1))

    @BeforeTest
    fun setUp() =
        runTest {
            database = createTestDatabase()
            repository = RoomTransactionRepository(database, clock)
            cafe = database.insertCategory("Cafe", TransactionType.EXPENSE)
            transport = database.insertCategory("Transport", TransactionType.EXPENSE)
            taxi = database.insertCategory("Taxi", TransactionType.EXPENSE, parentId = transport)
        }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun subcategoryRecordsShowUnderTheirParent() =
        runTest {
            add(taxi, LocalDate(2026, 10, 9))
            add(cafe, LocalDate(2026, 10, 10))

            val details = repository.observeTransactionDetails(october).first()

            val cafeRow = details.first { it.transaction.categoryId == cafe }
            assertEquals("Cafe", cafeRow.categoryName)
            assertNull(cafeRow.subcategoryName)

            val taxiRow = details.first { it.transaction.categoryId == taxi }
            assertEquals("Transport", taxiRow.categoryName)
            assertEquals("Taxi", taxiRow.subcategoryName)
        }

    @Test
    fun historyIsNewestFirstAndExportOldestFirst() =
        runTest {
            val first = add(cafe, LocalDate(2026, 10, 9))
            clock.now += 1.minutes
            val second = add(cafe, LocalDate(2026, 10, 9))
            val older = add(cafe, LocalDate(2026, 10, 2))

            assertEquals(
                listOf(second, first, older),
                repository.observeTransactionDetails(october).first().map { it.transaction.id },
            )
            assertEquals(
                listOf(older, first, second),
                repository.getTransactionDetailsChronologically(october).map { it.transaction.id },
            )
        }

    @Test
    fun onlyRecordsOfThePeriod() =
        runTest {
            add(cafe, LocalDate(2026, 9, 30))
            val inside = add(cafe, LocalDate(2026, 10, 31))
            add(cafe, LocalDate(2026, 11, 1))

            assertEquals(
                listOf(inside),
                repository.observeTransactionDetails(october).first().map { it.transaction.id },
            )
        }

    private suspend fun add(
        categoryId: Long,
        date: LocalDate,
    ): Long = repository.addTransaction(TransactionInput(categoryId, 10_00, date))
}
