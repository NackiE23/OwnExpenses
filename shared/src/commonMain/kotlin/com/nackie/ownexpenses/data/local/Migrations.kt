package com.nackie.ownexpenses.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.nackie.ownexpenses.presentation.categories.CategoryPalette

/** Data only: categories in the first, dull palette get its saturated replacements. Schema unchanged. */
internal val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            CategoryPalette.legacyColors.forEach { (old, new) ->
                connection.execSQL("UPDATE categories SET color = $new WHERE color = $old")
            }
        }
    }

/**
 * Sync with the OwnBudget server: every category and record gets a permanent UUID (`sync_id`),
 * records get a "not sent yet" flag (all existing ones start as not sent), and deletions the
 * server has not heard about yet go to `sync_deletions`.
 */
internal val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE categories ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")
            connection.execSQL("UPDATE categories SET sync_id = $RANDOM_UUID_SQL")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_sync_id ON categories (sync_id)")

            connection.execSQL("ALTER TABLE transactions ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE transactions ADD COLUMN sync_pending INTEGER NOT NULL DEFAULT 1")
            connection.execSQL("UPDATE transactions SET sync_id = $RANDOM_UUID_SQL")
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_sync_id ON transactions (sync_id)",
            )

            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_deletions " +
                    "(sync_id TEXT NOT NULL, deleted_at INTEGER NOT NULL, PRIMARY KEY(sync_id))",
            )
        }
    }

/** Notable ("starred") expenses; every existing record starts without the star. */
internal val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE transactions ADD COLUMN is_starred INTEGER NOT NULL DEFAULT 0")
        }
    }

/** UUID v4 in SQL, evaluated per row: random hex with the version digit 4 and variant 8, 9, a or b. */
private const val RANDOM_UUID_SQL =
    "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
