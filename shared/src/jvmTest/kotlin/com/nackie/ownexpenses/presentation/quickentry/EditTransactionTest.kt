package com.nackie.ownexpenses.presentation.quickentry

import app.cash.turbine.test
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.repository.RoomCategoryRepository
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.model.AmountKey
import com.nackie.ownexpenses.domain.model.AmountOperator
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.presentation.FakeTodayProvider
import com.nackie.ownexpenses.presentation.MainDispatcherTest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class EditTransactionTest : MainDispatcherTest() {
    private lateinit var database: AppDatabase
    private lateinit var categories: RoomCategoryRepository
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var viewModel: QuickEntryViewModel
    private val today = LocalDate(2026, 10, 24)

    private var cafe = 0L
    private var transport = 0L
    private var taxi = 0L
    private var bus = 0L
    private var salary = 0L

    @BeforeTest
    fun setUp() =
        runTest {
            database = createTestDatabase()
            cafe = database.insertCategory("Cafe", TransactionType.EXPENSE, sortOrder = 0)
            transport = database.insertCategory("Transport", TransactionType.EXPENSE, sortOrder = 1)
            taxi = database.insertCategory("Taxi", TransactionType.EXPENSE, parentId = transport)
            bus = database.insertCategory("Bus", TransactionType.EXPENSE, parentId = transport, sortOrder = 1)
            salary = database.insertCategory("Salary", TransactionType.INCOME)

            categories = RoomCategoryRepository(database)
            transactions = RoomTransactionRepository(database, MutableClock())
            viewModel = QuickEntryViewModel(categories, transactions, FakeTodayProvider(today))
        }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun openForEditPrefillsEverything() =
        runTest {
            val id = transactions.addTransaction(TransactionInput(taxi, 12_50, LocalDate(2026, 10, 20), "Airport"))

            viewModel.openForEdit(id)
            val state = viewModel.uiState.filterNotNull().first()

            assertTrue(state.isEditing)
            assertEquals("Transport", state.category.name)
            assertEquals(taxi, state.selectedSubcategoryId)
            assertEquals(12_50L, state.amount.result)
            assertEquals(LocalDate(2026, 10, 20), state.date)
            assertEquals("Airport", state.description)
            assertTrue(state.isDescriptionExpanded)
            assertEquals(listOf("Cafe", "Transport"), state.categoryChoices.map { it.category.name })
        }

    @Test
    fun savingUpdatesTheRecordInPlace() =
        runTest {
            val id = transactions.addTransaction(TransactionInput(taxi, 12_50, today))

            viewModel.events.test {
                viewModel.openForEdit(id)
                viewModel.uiState.filterNotNull().first()
                viewModel.onKey(AmountKey.Backspace)
                viewModel.onKey(AmountKey.Backspace)
                viewModel.onKey(AmountKey.Backspace)
                viewModel.onSubcategoryClick(bus)
                viewModel.shiftDate(-1)
                viewModel.submit()

                assertEquals(QuickEntryEvent.Updated, awaitItem())
            }

            assertNull(viewModel.uiState.value)
            val saved = assertNotNull(transactions.getTransaction(id))
            assertEquals(12_00, saved.amountMinor)
            assertEquals(bus, saved.categoryId)
            assertEquals(LocalDate(2026, 10, 23), saved.date)
        }

    @Test
    fun editedAmountCanBeExtendedWithAnExpression() =
        runTest {
            val id = transactions.addTransaction(TransactionInput(cafe, 89_00, today))

            viewModel.events.test {
                viewModel.openForEdit(id)
                viewModel.uiState.filterNotNull().first()
                // 89 + 35, e.g. a forgotten second coffee.
                viewModel.onKey(AmountKey.Operator(AmountOperator.PLUS))
                viewModel.onKey(AmountKey.Digit(3))
                viewModel.onKey(AmountKey.Digit(5))
                viewModel.submit()
                assertEquals(QuickEntryEvent.Updated, awaitItem())
            }

            assertEquals(124_00, transactions.getTransaction(id)?.amountMinor)
        }

    @Test
    fun recordCanMoveToAnotherCategory() =
        runTest {
            val id = transactions.addTransaction(TransactionInput(taxi, 5_00, today))

            viewModel.events.test {
                viewModel.openForEdit(id)
                viewModel.uiState.filterNotNull().first()
                viewModel.selectCategory(cafe)

                val state = assertNotNull(viewModel.uiState.value)
                assertEquals("Cafe", state.category.name)
                assertNull(state.selectedSubcategoryId)
                assertTrue(state.subcategories.isEmpty())

                viewModel.submit()
                awaitItem()
            }

            assertEquals(cafe, transactions.getTransaction(id)?.categoryId)
        }

    @Test
    fun archivedSubcategoryOfTheRecordStaysSelectable() =
        runTest {
            val id = transactions.addTransaction(TransactionInput(taxi, 5_00, today))
            categories.setArchived(taxi, true)

            viewModel.openForEdit(id)
            val state = viewModel.uiState.filterNotNull().first()

            assertEquals(listOf("Taxi", "Bus"), state.subcategories.map { it.name })
            assertEquals(taxi, state.selectedSubcategoryId)
        }

    @Test
    fun deleteAndUndoRestoreTheSameRecord() =
        runTest {
            val id = transactions.addTransaction(TransactionInput(cafe, 89_00, today, "Mac"))
            val original = assertNotNull(transactions.getTransaction(id))

            viewModel.events.test {
                viewModel.openForEdit(id)
                viewModel.uiState.filterNotNull().first()
                viewModel.deleteEdited()

                val deleted = assertIs<QuickEntryEvent.Deleted>(awaitItem())
                assertEquals(original, deleted.transaction)
                assertNull(viewModel.uiState.value)
                assertNull(transactions.getTransaction(id))

                viewModel.undoDelete(deleted.transaction)
            }

            transactions.observeBalance(today).test {
                var balance = awaitItem()
                while (balance != -89_00L) balance = awaitItem()
            }
            assertEquals(original, transactions.getTransaction(id))
        }
}
