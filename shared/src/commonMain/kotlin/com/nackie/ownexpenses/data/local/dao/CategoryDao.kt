package com.nackie.ownexpenses.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sort_order, id")
    fun observeByType(type: TransactionType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    /** Top-level categories first, so the result can be inserted in order. */
    @Query("SELECT * FROM categories ORDER BY parent_id IS NOT NULL, id")
    suspend fun getAll(): List<CategoryEntity>

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    /**
     * Subcategories first: the parent foreign key is RESTRICT, which SQLite checks per row, so a
     * single DELETE of everything fails as soon as a parent goes before its children.
     */
    @Query("DELETE FROM categories WHERE parent_id IS NOT NULL")
    suspend fun deleteAllSubcategories()

    /** Call [deleteAllSubcategories] first. */
    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT * FROM categories WHERE parent_id = :parentId ORDER BY sort_order, id")
    suspend fun getSubcategories(parentId: Long): List<CategoryEntity>

    @Query("UPDATE categories SET sort_order = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(
        id: Long,
        sortOrder: Int,
    )

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** Highest sort order among siblings, or -1 when there are none. */
    @Query(
        "SELECT COALESCE(MAX(sort_order), -1) FROM categories WHERE type = :type AND parent_id IS :parentId",
    )
    suspend fun maxSortOrder(
        type: TransactionType,
        parentId: Long?,
    ): Int

    /** Whether the category or any of its subcategories is used by a transaction. */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM transactions
            WHERE category_id = :id OR category_id IN (SELECT id FROM categories WHERE parent_id = :id)
        )
        """,
    )
    suspend fun hasTransactions(id: Long): Boolean

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE parent_id = :parentId")
    suspend fun deleteSubcategories(parentId: Long)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}
