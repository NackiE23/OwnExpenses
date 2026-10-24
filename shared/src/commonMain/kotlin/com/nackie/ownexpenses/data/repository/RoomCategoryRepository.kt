package com.nackie.ownexpenses.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.data.local.toDomain
import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryRemoval
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.NewCategory
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomCategoryRepository(
    private val database: AppDatabase,
) : CategoryRepository {
    private val dao = database.categoryDao()

    override fun observeCategories(
        type: TransactionType,
        includeArchived: Boolean,
    ): Flow<List<CategoryWithSubcategories>> =
        dao.observeByType(type).map { entities ->
            entities.map { it.toDomain() }.groupIntoTree(includeArchived)
        }

    override suspend fun getCategory(id: Long): Category? = dao.getById(id)?.toDomain()

    override suspend fun createCategory(category: NewCategory): Long {
        val name = category.name.requireValidName()
        return database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val parent =
                    category.parentId?.let { parentId ->
                        requireNotNull(dao.getById(parentId)) { "Parent category $parentId not found" }
                    }
                if (parent != null) {
                    require(parent.parentId == null) { "Subcategories can only be one level deep" }
                    require(parent.type == category.type) { "Subcategory type must match its parent" }
                }
                dao.insert(
                    CategoryEntity(
                        parentId = parent?.id,
                        name = name,
                        type = category.type,
                        color = category.color,
                        icon = category.icon,
                        sortOrder = dao.maxSortOrder(category.type, parent?.id) + 1,
                    ),
                )
            }
        }
    }

    override suspend fun updateCategory(
        id: Long,
        name: String,
        color: Int,
        icon: String,
    ) {
        val validName = name.requireValidName()
        modify(id) { it.copy(name = validName, color = color, icon = icon) }
    }

    override suspend fun setArchived(
        id: Long,
        archived: Boolean,
    ) {
        modify(id) { it.copy(isArchived = archived) }
    }

    override suspend fun removeCategory(id: Long): CategoryRemoval =
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val category = requireNotNull(dao.getById(id)) { "Category $id not found" }
                if (dao.hasTransactions(id)) {
                    dao.update(category.copy(isArchived = true))
                    CategoryRemoval.ARCHIVED
                } else {
                    dao.deleteSubcategories(id)
                    dao.delete(id)
                    CategoryRemoval.DELETED
                }
            }
        }

    private suspend fun modify(
        id: Long,
        transform: (CategoryEntity) -> CategoryEntity,
    ) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val category = requireNotNull(dao.getById(id)) { "Category $id not found" }
                dao.update(transform(category))
            }
        }
    }
}

private fun String.requireValidName(): String = trim().also { require(it.isNotEmpty()) { "Category name is blank" } }

/** Expects categories already sorted for display; keeps that order on both levels. */
internal fun List<Category>.groupIntoTree(includeArchived: Boolean): List<CategoryWithSubcategories> {
    val visible = if (includeArchived) this else filterNot { it.isArchived }
    val subcategoriesByParent = visible.filter { it.isSubcategory }.groupBy { it.parentId }
    return visible
        .filterNot { it.isSubcategory }
        .map { CategoryWithSubcategories(it, subcategoriesByParent[it.id].orEmpty()) }
}
