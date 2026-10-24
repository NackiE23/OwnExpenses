package com.nackie.ownexpenses.domain.model

/**
 * A transaction with what lists and exports show about its category. Records in a
 * subcategory are presented under their top-level category: [categoryName], [icon] and
 * [color] are always the top-level ones, [subcategoryName] is set only for subcategories.
 */
data class TransactionDetails(
    val transaction: Transaction,
    val categoryName: String,
    val subcategoryName: String?,
    val icon: String,
    /** ARGB color. */
    val color: Int,
)
