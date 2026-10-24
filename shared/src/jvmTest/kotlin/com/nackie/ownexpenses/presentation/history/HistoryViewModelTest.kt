package com.nackie.ownexpenses.presentation.history

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.PeriodSelection
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.presentation.FakeTodayProvider
import com.nackie.ownexpenses.presentation.MainDispatcherTest
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class HistoryViewModelTest : MainDispatcherTest() {
    private lateinit var database: AppDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var periodSelection: PeriodSelection
    private lateinit var viewModel: HistoryViewModel
    private val today = FakeTodayProvider(LocalDate(2026, 10, 24))
    private var cafe = 0L
    private var salary = 0L

    @BeforeTest
    fun setUp() =
        runTest {
            database = createTestDatabase()
            cafe = database.insertCategory("Cafe", TransactionType.EXPENSE)
            salary = database.insertCategory("Salary", TransactionType.INCOME)
            transactions = RoomTransactionRepository(database, MutableClock())
            periodSelection = PeriodSelection(today)
            viewModel = HistoryViewModel(transactions, periodSelection, today)
        }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun showsDaysOfTheSelectedPeriodNewestFirst() =
        runTest {
            add(salary, 1000_00, LocalDate(2026, 10, 1))
            add(cafe, 89_00, LocalDate(2026, 10, 24))
            add(cafe, 209_00, LocalDate(2026, 10, 24))
            add(cafe, 50_00, LocalDate(2026, 9, 30))

            viewModel.uiState.test {
                val state = awaitUntil { it.days.size == 2 && it.totals.incomeMinor > 0 }

                assertEquals(listOf(LocalDate(2026, 10, 24), LocalDate(2026, 10, 1)), state.days.map { it.date })
                assertEquals(listOf(-298_00L, 1000_00L), state.days.map { it.netMinor })
                assertEquals(298_00, state.totals.expenseMinor)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun periodSelectionIsSharedAcrossScreens() =
        runTest {
            add(cafe, 50_00, LocalDate(2026, 9, 30))

            viewModel.uiState.test {
                awaitUntil { it.days.isEmpty() }

                viewModel.selectPeriod(Period.weekOf(LocalDate(2026, 9, 30)))

                val state = awaitUntil { it.days.size == 1 }
                assertEquals(Period.weekOf(LocalDate(2026, 9, 30)), state.period)
                assertEquals(state.period, periodSelection.period.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun deletedRecordDisappears() =
        runTest {
            val id = add(cafe, 89_00, LocalDate(2026, 10, 24))

            viewModel.uiState.test {
                awaitUntil { it.days.size == 1 }

                transactions.deleteTransaction(id)

                awaitUntil { it.days.isEmpty() }
                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun add(
        categoryId: Long,
        amountMinor: Long,
        date: LocalDate,
    ) = transactions.addTransaction(TransactionInput(categoryId, amountMinor, date))

    private suspend fun ReceiveTurbine<HistoryUiState>.awaitUntil(
        condition: (HistoryUiState) -> Boolean,
    ): HistoryUiState {
        while (true) {
            val state = awaitItem()
            if (!state.isLoading && condition(state)) return state
        }
    }
}
