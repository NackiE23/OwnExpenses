package com.nackie.ownexpenses.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.domain.model.DefaultCategory
import com.nackie.ownexpenses.domain.model.DefaultCategoryNames
import com.nackie.ownexpenses.domain.repository.SettingsRepository

/**
 * Creates [DefaultCategory] entries with localized names on first launch. Runs at most once
 * per install: after that the user owns the category list, even if they delete everything.
 */
internal class DefaultCategorySeeder(
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val names: DefaultCategoryNames,
) {
    suspend fun seedIfNeeded() {
        if (settings.isDefaultCategoriesSeeded()) return

        // Resolved before the write transaction, which should not wait on resource loading.
        val localizedNames = DefaultCategory.entries.associateWith { names.nameOf(it) }
        val dao = database.categoryDao()

        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                // Count check inside the transaction makes concurrent calls insert only once.
                if (dao.count() > 0) return@immediateTransaction

                val ids = mutableMapOf<DefaultCategory, Long>()
                val (topLevel, subcategories) = DefaultCategory.entries.partition { it.parent == null }
                (topLevel + subcategories).forEach { category ->
                    val siblings =
                        DefaultCategory.entries.filter {
                            it.parent == category.parent &&
                                it.type == category.type
                        }
                    ids[category] =
                        dao.insert(
                            CategoryEntity(
                                parentId = category.parent?.let(ids::getValue),
                                name = localizedNames.getValue(category),
                                type = category.type,
                                color = category.color,
                                icon = category.icon,
                                sortOrder = siblings.indexOf(category),
                            ),
                        )
                }
            }
        }
        settings.markDefaultCategoriesSeeded()
    }
}
