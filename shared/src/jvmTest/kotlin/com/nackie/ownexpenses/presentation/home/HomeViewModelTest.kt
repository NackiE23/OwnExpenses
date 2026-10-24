package com.nackie.ownexpenses.presentation.home

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.repository.RoomCategoryRepository
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.PeriodSelection
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodTotals
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

internal class HomeViewModelTest : MainDispatcherTest() {
    private lateinit var database: AppDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var periodSelection: PeriodSelection
    private lateinit var viewModel: HomeViewModel
    private val today = FakeTodayProvider(LocalDate(2026, 10, 24))

    private var salary = 0L
    private var groceries = 0L
    private var cafe = 0L
    private var transport = 0L
    private var taxi = 0L

    @BeforeTest
    fun setUp() =
        runTest {
            database = createTestDatabase()
            salary = database.insertCategory("Salary", TransactionType.INCOME)
            groceries = database.insertCategory("Groceries", TransactionType.EXPENSE, sortOrder = 0)
            cafe = database.insertCategory("Cafe", TransactionType.EXPENSE, sortOrder = 1)
            transport = database.insertCategory("Transport", TransactionType.EXPENSE, sortOrder = 2)
            taxi = database.insertCategory("Taxi", TransactionType.EXPENSE, parentId = transport)

            transactions = RoomTransactionRepository(database, MutableClock())
            periodSelection = PeriodSelection(today)
            viewModel = HomeViewModel(RoomCategoryRepository(database), transactions, periodSelection, today)
        }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun showsBalancePeriodTotalsAndTilesOfCurrentMonth() =
        runTest {
            add(salary, 1000_00, LocalDate(2026, 9, 1))
            add(groceries, 300_00, LocalDate(2026, 10, 3))
            add(taxi, 150_00, LocalDate(2026, 10, 9))
            add(transport, 50_00, LocalDate(2026, 10, 10))

            viewModel.uiState.test {
                val state = awaitLoaded { it.tiles.sumOf { tile -> tile.totalMinor } == 500_00L }

                assertEquals(500_00, state.balanceMinor)
                assertEquals(Period.monthOf(LocalDate(2026, 10, 1)), state.period)
                assertEquals(PeriodTotals(expenseMinor = 500_00, incomeMinor = 0), state.totals)
                // The user's order, however much each category was used.
                assertEquals(listOf("Groceries", "Cafe", "Transport"), state.tiles.map { it.category.category.name })
                assertEquals(listOf(300_00L, 0L, 200_00L), state.tiles.map { it.totalMinor })
                assertEquals(
                    listOf("Taxi"),
                    state.tiles
                        .last()
                        .category.subcategories
                        .map { it.name },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun switchingTypeShowsIncomeCategories() =
        runTest {
            add(salary, 1000_00, LocalDate(2026, 10, 1))

            viewModel.uiState.test {
                awaitLoaded { it.tiles.isNotEmpty() }
                viewModel.selectType(TransactionType.INCOME)

                val state =
                    awaitLoaded {
                        it.selectedType == TransactionType.INCOME &&
                            it.tiles.any { tile -> tile.totalMinor > 0 }
                    }
                assertEquals(listOf("Salary"), state.tiles.map { it.category.category.name })
                assertEquals(1000_00, state.tiles.single().totalMinor)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun changingPeriodChangesTotalsButNotBalance() =
        runTest {
            add(salary, 1000_00, LocalDate(2026, 9, 1))
            add(groceries, 100_00, LocalDate(2026, 9, 15))
            add(groceries, 40_00, LocalDate(2026, 10, 15))

            viewModel.uiState.test {
                awaitLoaded { it.totals.expenseMinor == 40_00L }

                viewModel.showPreviousPeriod()
                val september =
                    awaitLoaded {
                        it.period == Period.monthOf(LocalDate(2026, 9, 1)) &&
                            it.totals.incomeMinor > 0
                    }

                assertEquals(PeriodTotals(expenseMinor = 100_00, incomeMinor = 1000_00), september.totals)
                assertEquals(860_00, september.balanceMinor)
                assertEquals(periodSelection.period.value, september.period)

                viewModel.showNextPeriod()
                awaitLoaded { it.period == Period.monthOf(LocalDate(2026, 10, 1)) && it.totals.expenseMinor == 40_00L }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun futureRecordEntersBalanceAfterMidnight() =
        runTest {
            add(salary, 1000_00, LocalDate(2026, 10, 1))
            add(groceries, 200_00, LocalDate(2026, 10, 25))

            viewModel.uiState.test {
                awaitLoaded { it.balanceMinor == 1000_00L }

                today.date.value = LocalDate(2026, 10, 25)

                awaitLoaded { it.balanceMinor == 800_00L }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun balanceUpdatesAfterAdd() =
        runTest {
            viewModel.uiState.test {
                awaitLoaded { it.balanceMinor == 0L }

                add(groceries, 77_70, LocalDate(2026, 10, 24))

                // Balance, totals and tiles are separate queries and may update one by one.
                val state =
                    awaitLoaded {
                        it.balanceMinor == -77_70L &&
                            it.totals.expenseMinor == 77_70L &&
                            it.tiles.first().totalMinor == 77_70L
                    }
                assertEquals(
                    "Groceries",
                    state.tiles
                        .first()
                        .category.category.name,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun add(
        categoryId: Long,
        amountMinor: Long,
        date: LocalDate,
    ) = transactions.addTransaction(TransactionInput(categoryId, amountMinor, date))

    /** Room emits asynchronously, so intermediate states are skipped until [condition] holds. */
    private suspend fun ReceiveTurbine<HomeUiState>.awaitLoaded(condition: (HomeUiState) -> Boolean): HomeUiState {
        while (true) {
            val state = awaitItem()
            if (!state.isLoading && condition(state)) return state
        }
    }
}
