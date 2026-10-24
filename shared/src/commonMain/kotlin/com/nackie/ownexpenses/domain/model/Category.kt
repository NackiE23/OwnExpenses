package com.nackie.ownexpenses.domain.model

/**
 * Category or subcategory (one level deep). A subcategory has [parentId] and always has the
 * same [type] as its parent. Archived categories are hidden from pickers but stay in history.
 */
data class Category(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val type: TransactionType,
    /** ARGB color. */
    val color: Int,
    /** Key of a bundled icon, resolved by the UI. */
    val icon: String,
    val sortOrder: Int,
    val isArchived: Boolean,
) {
    val isSubcategory: Boolean get() = parentId != null
}

/** Top-level category with its subcategories, both in display order. */
data class CategoryWithSubcategories(
    val category: Category,
    val subcategories: List<Category>,
)

/** Input for creating a category. With [parentId] it becomes a subcategory of that parent. */
data class NewCategory(
    val name: String,
    val type: TransactionType,
    val color: Int,
    val icon: String,
    val parentId: Long? = null,
)

/** Outcome of removing a category: categories that have records are archived, not deleted. */
enum class CategoryRemoval {
    DELETED,
    ARCHIVED,
}
