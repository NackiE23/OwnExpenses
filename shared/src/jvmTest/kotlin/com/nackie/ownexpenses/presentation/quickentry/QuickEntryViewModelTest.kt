package com.nackie.ownexpenses.presentation.quickentry

import app.cash.turbine.test
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.repository.RoomCategoryRepository
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.model.AmountKey
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.presentation.FakeTodayProvider
import com.nackie.ownexpenses.presentation.MainDispatcherTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class QuickEntryViewModelTest : MainDispatcherTest() {
    private lateinit var database: AppDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var viewModel: QuickEntryViewModel
    private lateinit var transport: CategoryWithSubcategories
    private val today = LocalDate(2026, 10, 24)

    @BeforeTest
    fun setUp() =
        runTest {
            database = createTestDatabase()
            val transportId = database.insertCategory("Transport", TransactionType.EXPENSE)
            database.insertCategory("Taxi", TransactionType.EXPENSE, parentId = transportId)
            database.insertCategory("Bus", TransactionType.EXPENSE, parentId = transportId, sortOrder = 1)
            transport = RoomCategoryRepository(database).observeCategories(TransactionType.EXPENSE).first().single()

            transactions = RoomTransactionRepository(database, MutableClock())
            viewModel = QuickEntryViewModel(transactions, FakeTodayProvider(today))
        }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun opensWithTodayAndEmptyAmount() {
        viewModel.open(transport)

        val state = assertNotNull(viewModel.uiState.value)
        assertEquals("Transport", state.category.name)
        assertEquals(listOf("Taxi", "Bus"), state.subcategories.map { it.name })
        assertEquals(today, state.date)
        assertTrue(state.amount.isEmpty)
        assertFalse(state.canSubmit)
    }

    @Test
    fun threeActionsRecordAnExpense() =
        runTest {
            viewModel.events.test {
                // 1. tap the category, 2. type the amount, 3. tap Add.
                viewModel.open(transport)
                press(1, 2, 5)
                viewModel.submit()

                val added = assertIs<QuickEntryEvent.Added>(awaitItem())
                assertEquals(TransactionType.EXPENSE, added.type)
                assertNull(viewModel.uiState.value, "Panel closes after adding")

                val saved = assertNotNull(transactions.getTransaction(added.transactionId))
                assertEquals(125_00, saved.amountMinor)
                assertEquals(transport.category.id, saved.categoryId)
                assertEquals(today, saved.date)
                assertNull(saved.description)
                assertEquals(-125_00, transactions.observeBalance(today).first())
            }
        }

    @Test
    fun optionalSubcategoryDateAndDescriptionAreSaved() =
        runTest {
            val taxi = transport.subcategories.first()

            viewModel.events.test {
                viewModel.open(transport)
                viewModel.onSubcategoryClick(taxi.id)
                viewModel.shiftDate(-1)
                viewModel.expandDescription()
                viewModel.onDescriptionChange("  Airport ")
                press(8, 9)
                viewModel.onKey(AmountKey.DecimalSeparator)
                press(5)
                viewModel.submit()

                val added = assertIs<QuickEntryEvent.Added>(awaitItem())
                val saved = assertNotNull(transactions.getTransaction(added.transactionId))
                assertEquals(taxi.id, saved.categoryId)
                assertEquals(89_50, saved.amountMinor)
                assertEquals(LocalDate(2026, 10, 23), saved.date)
                assertEquals("Airport", saved.description)
            }
        }

    @Test
    fun tappingSelectedSubcategoryAgainClearsIt() {
        val taxi = transport.subcategories.first()
        viewModel.open(transport)

        viewModel.onSubcategoryClick(taxi.id)
        assertEquals(taxi.id, viewModel.uiState.value?.selectedSubcategoryId)

        viewModel.onSubcategoryClick(taxi.id)
        assertNull(viewModel.uiState.value?.selectedSubcategoryId)
    }

    @Test
    fun zeroAmountIsNotSubmitted() =
        runTest {
            viewModel.open(transport)
            press(0)
            viewModel.submit()

            assertNotNull(viewModel.uiState.value, "Panel stays open")
            assertEquals(0, transactions.observeBalance(today).first())
        }

    @Test
    fun undoRemovesTheAddedRecord() =
        runTest {
            viewModel.events.test {
                viewModel.open(transport)
                press(4, 0)
                viewModel.submit()
                val added = assertIs<QuickEntryEvent.Added>(awaitItem())

                viewModel.undo(added.transactionId)

                transactions.observeBalance(today).test {
                    var balance = awaitItem()
                    while (balance != 0L) balance = awaitItem()
                }
                assertNull(transactions.getTransaction(added.transactionId))
            }
        }

    @Test
    fun closeDiscardsInput() {
        viewModel.open(transport)
        press(5)
        viewModel.close()

        assertNull(viewModel.uiState.value)
    }

    @Test
    fun descriptionIsCapped() {
        viewModel.open(transport)
        viewModel.onDescriptionChange("x".repeat(500))

        assertEquals(
            QuickEntryViewModel.MAX_DESCRIPTION_LENGTH,
            viewModel.uiState.value
                ?.description
                ?.length,
        )
    }

    private fun press(vararg digits: Int) {
        digits.forEach { viewModel.onKey(AmountKey.Digit(it)) }
    }
}
