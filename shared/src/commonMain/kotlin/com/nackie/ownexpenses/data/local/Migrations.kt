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

internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
