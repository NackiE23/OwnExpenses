package com.nackie.ownexpenses.presentation.history

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.repository.RoomCategoryRepository
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.OperationsFilterSelection
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

internal class HistoryViewModelTest : MainDispatcherTest() {
    private lateinit var database: AppDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var periodSelection: PeriodSelection
    private lateinit var filterSelection: OperationsFilterSelection
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
            filterSelection = OperationsFilterSelection()
            viewModel =
                HistoryViewModel(
                    transactions,
                    RoomCategoryRepository(database),
                    periodSelection,
                    filterSelection,
                    today,
                ).tracked()
        }

    @AfterTest
    fun tearDown() {
        cancelViewModels()
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

    @Test
    fun wholeCategoryFilterIncludesItsSubcategories() =
        runTest {
            val taxi = database.insertCategory("Taxi", TransactionType.EXPENSE, parentId = cafe)
            val transport = database.insertCategory("Transport", TransactionType.EXPENSE)
            add(cafe, 89_00, LocalDate(2026, 10, 24))
            add(taxi, 150_00, LocalDate(2026, 10, 23))
            add(transport, 40_00, LocalDate(2026, 10, 22))
            add(salary, 1000_00, LocalDate(2026, 10, 1))

            viewModel.uiState.test {
                val all = awaitUntil { it.days.size == 4 && it.expenseCategories.size == 2 }
                viewModel.toggleCategory(all.expenseCategories.first { it.category.id == cafe })

                val filtered = awaitUntil { it.filter.isActive }
                assertEquals(listOf(89_00L, 150_00L), amounts(filtered))
                assertEquals(PeriodTotals(expenseMinor = 239_00), filtered.totals)

                // Only the subcategory: switching off the records directly in the category.
                viewModel.toggleLeaf(cafe)
                assertEquals(listOf(150_00L), amounts(awaitUntil { it.filter.categoryIds == setOf(taxi) }))

                viewModel.clearFilter()
                assertEquals(4, awaitUntil { !it.filter.isActive }.days.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun queryMatchesDescriptionIgnoringCase() =
        runTest {
            transactions.addTransaction(TransactionInput(cafe, 89_00, LocalDate(2026, 10, 24), "Круасани"))
            transactions.addTransaction(TransactionInput(cafe, 50_00, LocalDate(2026, 10, 24), "Кава"))
            add(cafe, 10_00, LocalDate(2026, 10, 24))

            viewModel.uiState.test {
                awaitUntil {
                    it.days
                        .singleOrNull()
                        ?.items
                        ?.size == 3
                }
                viewModel.setQuery("  КРУАС ")

                val state = awaitUntil { it.filter.isActive }
                assertEquals(listOf(89_00L), amounts(state))
                assertEquals(-89_00, state.days.single().netMinor)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun filterIsSharedWithTheMainScreen() =
        runTest {
            val transport = database.insertCategory("Transport", TransactionType.EXPENSE)
            add(cafe, 89_00, LocalDate(2026, 10, 24))
            add(transport, 40_00, LocalDate(2026, 10, 24))

            viewModel.uiState.test {
                val all =
                    awaitUntil {
                        it.days
                            .singleOrNull()
                            ?.items
                            ?.size == 2 && it.expenseCategories.size == 2
                    }
                filterSelection.showOnly(all.expenseCategories.first { it.category.id == transport })

                assertEquals(listOf(40_00L), amounts(awaitUntil { it.filter.isActive }))
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun amounts(state: HistoryUiState) =
        state.days.flatMap { day -> day.items.map { it.transaction.amountMinor } }

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
