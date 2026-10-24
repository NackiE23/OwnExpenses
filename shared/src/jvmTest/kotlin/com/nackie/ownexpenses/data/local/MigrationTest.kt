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

    private fun category(
        id: Long,
        color: Int,
        type: String = "expense",
    ) = "INSERT INTO categories (id, parent_id, name, type, color, icon, sort_order, is_archived) " +
        "VALUES ($id, NULL, 'Category $id', '$type', $color, 'other', $id, 0)"
}
