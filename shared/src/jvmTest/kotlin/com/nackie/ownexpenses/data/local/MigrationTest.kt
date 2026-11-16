package com.nackie.ownexpenses.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.nackie.ownexpenses.presentation.categories.CategoryPalette
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationTest {
    private val directory: Path = Files.createTempDirectory("migration")
    private val helper =
        MigrationTestHelper(
            schemaDirectoryPath = Path("schemas"),
            databasePath = directory.resolve("test.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
            databaseFactory = { AppDatabaseConstructor.initialize() },
        )

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun migration1To2RecolorsTheFirstPaletteOnly() {
        val custom = 0xFF123456.toInt()
        helper.createDatabase(1).use { connection ->
            connection.execSQL(category(id = 1, color = 0xFF7E57C2.toInt()))
            connection.execSQL(category(id = 2, color = custom))
            connection.execSQL(category(id = 3, color = 0xFF546E7A.toInt(), type = "income"))
        }

        val colors =
            helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).use { connection ->
                connection.prepare("SELECT color FROM categories ORDER BY id").use { statement ->
                    buildList { while (statement.step()) add(statement.getLong(0).toInt()) }
                }
            }

        assertEquals(listOf(CategoryPalette.colors[0], custom, 0xFF90A4AE.toInt()), colors)
    }

    @Test
    fun migration2To3GivesEveryRowItsOwnSyncIdAndMarksRecordsUnsent() {
        helper.createDatabase(2).use { connection ->
            connection.execSQL(category(id = 1, color = custom()))
            connection.execSQL(category(id = 2, color = custom()))
            connection.execSQL(
                "INSERT INTO transactions (id, type, amount_minor, category_id, date, description, created_at, " +
                    "updated_at) VALUES (1, 'expense', 8500, 1, 20000, NULL, 0, 0), " +
                    "(2, 'expense', 4000, 2, 20001, 'cafe', 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)).use { connection ->
            val uuidV4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            val ids =
                listOf("categories", "transactions").flatMap { table ->
                    connection.prepare("SELECT sync_id FROM $table").use { statement ->
                        buildList { while (statement.step()) add(statement.getText(0)) }
                    }
                }
            assertEquals(4, ids.toSet().size)
            ids.forEach { assertTrue(uuidV4.matches(it), it) }

            val pending =
                connection.prepare("SELECT COUNT(*) FROM transactions WHERE sync_pending = 1").use { statement ->
                    statement.step()
                    statement.getLong(0)
                }
            assertEquals(2L, pending)
        }
    }

    private fun custom() = 0xFF123456.toInt()

    private fun category(
        id: Long,
        color: Int,
        type: String = "expense",
    ) = "INSERT INTO categories (id, parent_id, name, type, color, icon, sort_order, is_archived) " +
        "VALUES ($id, NULL, 'Category $id', '$type', $color, 'other', $id, 0)"
}
