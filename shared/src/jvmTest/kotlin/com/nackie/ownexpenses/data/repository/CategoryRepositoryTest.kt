package com.nackie.ownexpenses.data.repository

import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.domain.model.CategoryRemoval
import com.nackie.ownexpenses.domain.model.NewCategory
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomCategoryRepository
    private lateinit var transactions: RoomTransactionRepository

    @BeforeTest
    fun setUp() {
        database = createTestDatabase()
        repository = RoomCategoryRepository(database)
        transactions = RoomTransactionRepository(database, MutableClock())
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun categoriesAreGroupedByTypeWithSubcategoriesInCreationOrder() =
        runTest {
            val food = create("Food")
            val transport = create("Transport")
            val taxi = create("Taxi", parentId = transport)
            val bus = create("Bus", parentId = transport)
            create("Salary", type = TransactionType.INCOME)

            val tree = repository.observeCategories(TransactionType.EXPENSE).first()

            assertEquals(listOf(food, transport), tree.map { it.category.id })
            assertEquals(listOf(taxi, bus), tree[1].subcategories.map { it.id })
            assertTrue(tree[0].subcategories.isEmpty())
        }

    @Test
    fun nameIsTrimmedAndBlankNameRejected() =
        runTest {
            val id = create("  Food  ")

            assertEquals("Food", repository.getCategory(id)?.name)
            assertFailsWith<IllegalArgumentException> { create("   ") }
        }

    @Test
    fun subcategoryMustMatchParentType() =
        runTest {
            val food = create("Food")

            assertFailsWith<IllegalArgumentException> {
                create("Bonus", type = TransactionType.INCOME, parentId = food)
            }
        }

    @Test
    fun subcategoriesAreOneLevelDeep() =
        runTest {
            val transport = create("Transport")
            val taxi = create("Taxi", parentId = transport)

            assertFailsWith<IllegalArgumentException> { create("Night taxi", parentId = taxi) }
        }

    @Test
    fun updateChangesNameColorAndIcon() =
        runTest {
            val id = create("Food")

            repository.updateCategory(id, name = "Groceries", color = 0xFF00FF00.toInt(), icon = "groceries")

            val category = assertNotNull(repository.getCategory(id))
            assertEquals("Groceries", category.name)
            assertEquals(0xFF00FF00.toInt(), category.color)
            assertEquals("groceries", category.icon)
        }

    @Test
    fun archivedCategoryAndItsSubcategoriesAreHiddenUnlessRequested() =
        runTest {
            val food = create("Food")
            val transport = create("Transport")
            val taxi = create("Taxi", parentId = transport)
            create("Bus", parentId = transport)

            repository.setArchived(food, true)
            repository.setArchived(taxi, true)

            val visible = repository.observeCategories(TransactionType.EXPENSE).first()
            assertEquals(listOf(transport), visible.map { it.category.id })
            assertEquals(listOf("Bus"), visible.single().subcategories.map { it.name })

            val all = repository.observeCategories(TransactionType.EXPENSE, includeArchived = true).first()
            assertEquals(listOf(food, transport), all.map { it.category.id })
            assertEquals(2, all[1].subcategories.size)
        }

    @Test
    fun unusedCategoryIsDeletedWithSubcategories() =
        runTest {
            val transport = create("Transport")
            val taxi = create("Taxi", parentId = transport)

            val removal = assertIs<CategoryRemoval.Deleted>(repository.removeCategory(transport))
            assertEquals(listOf("Transport", "Taxi"), removal.removed.map { it.name })
            assertNull(repository.getCategory(transport))
            assertNull(repository.getCategory(taxi))

            // Undo puts both back with their ids, order and colors.
            repository.restoreCategories(removal.removed)
            assertEquals(removal.removed, listOf(transport, taxi).map { repository.getCategory(it) })
        }

    @Test
    fun categoryWithRecordsInSubcategoryIsArchivedInstead() =
        runTest {
            val transport = create("Transport")
            val taxi = create("Taxi", parentId = transport)
            transactions.addTransaction(TransactionInput(taxi, 150_00, LocalDate(2026, 10, 9)))

            assertEquals(CategoryRemoval.Archived, repository.removeCategory(transport))
            assertEquals(true, repository.getCategory(transport)?.isArchived)
            assertNotNull(repository.getCategory(taxi))
        }

    @Test
    fun reorderSetsTheOrderOfSiblings() =
        runTest {
            val food = create("Food")
            val transport = create("Transport")
            val cafe = create("Cafe")
            val taxi = create("Taxi", parentId = transport)
            val bus = create("Bus", parentId = transport)

            repository.reorder(listOf(cafe, food, transport))
            repository.reorder(listOf(bus, taxi))

            val tree = repository.observeCategories(TransactionType.EXPENSE).first()
            assertEquals(listOf("Cafe", "Food", "Transport"), tree.map { it.category.name })
            assertEquals(listOf("Bus", "Taxi"), tree.last().subcategories.map { it.name })
        }

    @Test
    fun reorderRejectsMixedLevels() =
        runTest {
            val transport = create("Transport")
            val taxi = create("Taxi", parentId = transport)

            assertFailsWith<IllegalArgumentException> { repository.reorder(listOf(taxi, transport)) }
        }

    private suspend fun create(
        name: String,
        type: TransactionType = TransactionType.EXPENSE,
        parentId: Long? = null,
    ): Long = repository.createCategory(NewCategory(name, type, color = 0, icon = "other", parentId = parentId))
}
