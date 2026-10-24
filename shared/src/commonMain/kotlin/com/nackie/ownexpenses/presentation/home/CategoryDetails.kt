package com.nackie.ownexpenses.presentation.home

import com.nackie.ownexpenses.domain.leafIds
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.TransactionDetails
import com.nackie.ownexpenses.presentation.statistics.SubcategoryShare

/** A category's period at a glance: the panel opened by holding its tile. */
data class CategoryDetails(
    val category: CategoryWithSubcategories,
    val operationCount: Int,
    val totalMinor: Long,
    /** 0..1 of all records of the category's type in the period. */
    val shareOfPeriod: Float,
    /**
     * Subcategories with records, largest first, plus records put on the category itself
     * ([SubcategoryShare.subcategory] null); empty when no subcategory has records.
     */
    val parts: List<SubcategoryShare>,
)

/** [records] are the period's records of any category; [typeTotalMinor] is the period's sum of the category's type. */
internal fun buildCategoryDetails(
    category: CategoryWithSubcategories,
    records: List<TransactionDetails>,
    typeTotalMinor: Long,
): CategoryDetails {
    val ids = category.leafIds()
    val own = records.filter { it.transaction.categoryId in ids }
    val total = own.sumOf { it.transaction.amountMinor }
    val bySubcategory =
        own.groupBy { it.transaction.categoryId }.mapValues { (_, items) ->
            items.sumOf { it.transaction.amountMinor }
        }
    val subcategoryParts =
        category.subcategories.mapNotNull { sub ->
            bySubcategory[sub.id]?.let {
                SubcategoryShare(
                    sub,
                    it,
                    it.toFloat() / total,
                )
            }
        }
    val parts =
        if (subcategoryParts.isEmpty()) {
            emptyList()
        } else {
            val direct = bySubcategory[category.category.id]
            (subcategoryParts + listOfNotNull(direct?.let { SubcategoryShare(null, it, it.toFloat() / total) }))
                .sortedByDescending { it.totalMinor }
        }
    return CategoryDetails(
        category = category,
        operationCount = own.size,
        totalMinor = total,
        shareOfPeriod = if (typeTotalMinor > 0) total.toFloat() / typeTotalMinor else 0f,
        parts = parts,
    )
}
