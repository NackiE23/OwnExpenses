package com.nackie.ownexpenses.presentation.categories

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.repository.RoomCategoryRepository
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.model.CategoryRemoval
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.presentation.MainDispatcherTest
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class CategoriesViewModelTest : MainDispatcherTest() {
    private lateinit var database: AppDatabase
    private lateinit var viewModel: CategoriesViewModel

    @BeforeTest
    fun setUp() {
        database = createTestDatabase()
        viewModel = CategoriesViewModel(RoomCategoryRepository(database))
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun createsCategoryAtTheEndOfTheShownType() =
        runTest {
            database.insertCategory("Food", TransactionType.EXPENSE)

            viewModel.uiState.test {
                awaitUntil { it.active.size == 1 }
                viewModel.startCreate()
                viewModel.onNameChange("  Books ")
                viewModel.onIconChange("entertainment")
                viewModel.save()

                val state = awaitUntil { it.active.size == 2 }
                assertNull(state.editor)
                val books = state.active.last().category
                assertEquals("Books", books.name)
                assertEquals("entertainment", books.icon)
                assertEquals(TransactionType.EXPENSE, books.type)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun blankNameCannotBeSaved() {
        viewModel.startCreate()
        viewModel.onNameChange("   ")

        assertEquals(
            false,
            viewModel.uiState.value.editor
                ?.canSave ?: false,
        )
    }

    @Test
    fun subcategoryInheritsTypeColorAndIconAsDefaults() =
        runTest {
            database.insertCategory("Salary", TransactionType.INCOME)

            viewModel.uiState.test {
                viewModel.selectType(TransactionType.INCOME)
                val salary =
                    awaitUntil { it.type == TransactionType.INCOME && it.active.isNotEmpty() }
                        .active
                        .single()
                        .category

                viewModel.startCreate(parent = salary)
                val editor = awaitUntil { it.editor != null }.editor!!
                assertEquals(TransactionType.INCOME, editor.type)
                assertEquals(salary.color, editor.color)

                viewModel.onNameChange("Bonus")
                viewModel.save()
                val state =
                    awaitUntil {
                        it.active
                            .single()
                            .subcategories
                            .isNotEmpty()
                    }
                assertEquals(
                    listOf("Bonus"),
                    state.active
                        .single()
                        .subcategories
                        .map { it.name },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun editChangesNameColorAndIcon() =
        runTest {
            database.insertCategory("Food", TransactionType.EXPENSE)

            viewModel.uiState.test {
                val food = awaitUntil { it.active.isNotEmpty() }.active.single().category
                viewModel.startEdit(food)
                viewModel.onNameChange("Groceries")
                viewModel.onColorChange(CategoryPalette.colors[2])
                viewModel.save()

                val updated =
                    awaitUntil {
                        it.active
                            .single()
                            .category.name == "Groceries"
                    }.active.single().category
                assertEquals(CategoryPalette.colors[2], updated.color)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun unusedCategoryIsDeletedAndCanBeRestored() =
        runTest {
            val transport = database.insertCategory("Transport", TransactionType.EXPENSE)
            database.insertCategory("Taxi", TransactionType.EXPENSE, parentId = transport)

            turbineScope {
                val states = viewModel.uiState.testIn(backgroundScope)
                val events = viewModel.events.testIn(backgroundScope)

                val category =
                    states
                        .awaitUntil { it.active.isNotEmpty() }
                        .active
                        .single()
                        .category
                viewModel.remove(category)
                states.awaitUntil { it.active.isEmpty() }

                val removed = assertIs<CategoriesEvent.Removed>(events.awaitItem())
                viewModel.undoRemove(assertIs<CategoryRemoval.Deleted>(removed.removal))

                val restored = states.awaitUntil { it.active.isNotEmpty() }
                assertEquals(
                    listOf("Taxi"),
                    restored.active
                        .single()
                        .subcategories
                        .map { it.name },
                )
            }
        }

    @Test
    fun categoryWithRecordsGoesToArchiveAndBack() =
        runTest {
            val cafe = database.insertCategory("Cafe", TransactionType.EXPENSE)
            val transactions = RoomTransactionRepository(database, MutableClock())
            transactions.addTransaction(TransactionInput(cafe, 89_00, LocalDate(2026, 10, 24)))

            turbineScope {
                val states = viewModel.uiState.testIn(backgroundScope)
                val events = viewModel.events.testIn(backgroundScope)

                val category =
                    states
                        .awaitUntil { it.active.isNotEmpty() }
                        .active
                        .single()
                        .category
                viewModel.remove(category)

                val archived = states.awaitUntil { it.archived.isNotEmpty() }
                assertTrue(archived.active.isEmpty())
                assertEquals(CategoryRemoval.Archived, assertIs<CategoriesEvent.Removed>(events.awaitItem()).removal)

                viewModel.setArchived(archived.archived.single().category, archived = false)
                states.awaitUntil { it.active.isNotEmpty() && it.archived.isEmpty() }
            }
        }

    @Test
    fun moveUpAndDownReordersSiblings() =
        runTest {
            database.insertCategory("A", TransactionType.EXPENSE, sortOrder = 0)
            database.insertCategory("B", TransactionType.EXPENSE, sortOrder = 1)
            database.insertCategory("C", TransactionType.EXPENSE, sortOrder = 2)

            viewModel.uiState.test {
                val state = awaitUntil { it.active.size == 3 }
                viewModel.move(state.active[2].category, steps = -1)
                awaitUntil { names(it) == listOf("A", "C", "B") }

                viewModel.move(
                    viewModel.uiState.value.active[0]
                        .category,
                    steps = 1,
                )
                awaitUntil { names(it) == listOf("C", "A", "B") }

                // Already first: nothing happens.
                viewModel.move(
                    viewModel.uiState.value.active[0]
                        .category,
                    steps = -1,
                )
                assertEquals(listOf("C", "A", "B"), names(viewModel.uiState.value))
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun names(state: CategoriesUiState) = state.active.map { it.category.name }

    private suspend fun ReceiveTurbine<CategoriesUiState>.awaitUntil(
        condition: (CategoriesUiState) -> Boolean,
    ): CategoriesUiState {
        while (true) {
            val state = awaitItem()
            if (!state.isLoading && condition(state)) return state
        }
    }
}
