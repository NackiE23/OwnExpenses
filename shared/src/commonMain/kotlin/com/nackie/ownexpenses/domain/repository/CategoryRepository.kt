package com.nackie.ownexpenses.domain.repository

import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryRemoval
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.NewCategory
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    /**
     * Categories of [type] grouped with their subcategories, in display order. Without
     * [includeArchived], archived categories are left out, and so are all subcategories
     * of an archived parent.
     */
    fun observeCategories(
        type: TransactionType,
        includeArchived: Boolean = false,
    ): Flow<List<CategoryWithSubcategories>>

    suspend fun getCategory(id: Long): Category?

    /**
     * Adds a category at the end of its level and returns its id. A subcategory must use the
     * type of its parent, and the parent must be a top-level category.
     */
    suspend fun createCategory(category: NewCategory): Long

    suspend fun updateCategory(
        id: Long,
        name: String,
        color: Int,
        icon: String,
    )

    suspend fun setArchived(
        id: Long,
        archived: Boolean,
    )

    /**
     * Deletes the category with its subcategories when none of them has records;
     * otherwise archives it so history keeps its category.
     */
    suspend fun removeCategory(id: Long): CategoryRemoval

    /** Puts back categories removed by [removeCategory], with their original ids. */
    suspend fun restoreCategories(categories: List<Category>)

    /**
     * New display order of sibling categories: all top-level categories of one type, or all
     * subcategories of one parent, listed in the wanted order.
     */
    suspend fun reorder(orderedIds: List<Long>)
}
