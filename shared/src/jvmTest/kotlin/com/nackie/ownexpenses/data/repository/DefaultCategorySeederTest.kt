package com.nackie.ownexpenses.data.repository

import com.nackie.ownexpenses.data.FakeSettingsRepository
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.domain.model.DefaultCategory
import com.nackie.ownexpenses.domain.model.DefaultCategoryNames
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultCategorySeederTest {
    private lateinit var database: AppDatabase
    private lateinit var settings: FakeSettingsRepository
    private lateinit var seeder: DefaultCategorySeeder
    private lateinit var categories: RoomCategoryRepository

    private val names = DefaultCategoryNames { "name:${it.name}" }

    @BeforeTest
    fun setUp() {
        database = createTestDatabase()
        settings = FakeSettingsRepository()
        seeder = DefaultCategorySeeder(database, settings, names)
        categories = RoomCategoryRepository(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun createsAllDefaultsWithLocalizedNamesInOrder() =
        runTest {
            seeder.seedIfNeeded()

            val expenses = categories.observeCategories(TransactionType.EXPENSE).first()
            val incomes = categories.observeCategories(TransactionType.INCOME).first()

            val expected = DefaultCategory.entries.filter { it.parent == null }
            assertEquals(
                expected.filter { it.type == TransactionType.EXPENSE }.map { "name:${it.name}" },
                expenses.map { it.category.name },
            )
            assertEquals(
                expected.filter { it.type == TransactionType.INCOME }.map { "name:${it.name}" },
                incomes.map { it.category.name },
            )

            val transport = expenses.single { it.category.name == "name:TRANSPORT" }
            assertEquals(listOf("name:TAXI", "name:PUBLIC_TRANSPORT"), transport.subcategories.map { it.name })
            assertTrue(transport.subcategories.all { it.type == TransactionType.EXPENSE })
            assertTrue(transport.subcategories.all { it.color == transport.category.color })
            assertTrue(settings.seeded)
        }

    @Test
    fun runsOnlyOnce() =
        runTest {
            seeder.seedIfNeeded()
            seeder.seedIfNeeded()

            assertEquals(DefaultCategory.entries.size, database.categoryDao().count())
        }

    @Test
    fun concurrentCallsSeedOnce() =
        runTest {
            List(4) { async { seeder.seedIfNeeded() } }.awaitAll()

            assertEquals(DefaultCategory.entries.size, database.categoryDao().count())
        }

    @Test
    fun doesNotRecreateAfterUserDeletedEverything() =
        runTest {
            seeder.seedIfNeeded()
            TransactionType.entries.forEach { type ->
                categories.observeCategories(type).first().forEach { categories.removeCategory(it.category.id) }
            }
            assertEquals(0, database.categoryDao().count())

            seeder.seedIfNeeded()

            assertEquals(0, database.categoryDao().count())
        }

    @Test
    fun keepsExistingCategoriesIntact() =
        runTest {
            database.insertCategory("Mine", TransactionType.EXPENSE)

            seeder.seedIfNeeded()

            assertEquals(1, database.categoryDao().count())
            assertTrue(settings.seeded)
        }
}
