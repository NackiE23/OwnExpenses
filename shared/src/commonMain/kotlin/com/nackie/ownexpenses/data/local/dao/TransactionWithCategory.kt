package com.nackie.ownexpenses.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.nackie.ownexpenses.data.local.entity.TransactionEntity
import com.nackie.ownexpenses.data.local.toDomain
import com.nackie.ownexpenses.domain.model.TransactionDetails

/** Row of [TransactionDao.TRANSACTIONS_WITH_CATEGORIES]; parent columns are null for top-level categories. */
internal data class TransactionWithCategory(
    @Embedded val transaction: TransactionEntity,
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "category_icon") val categoryIcon: String,
    @ColumnInfo(name = "category_color") val categoryColor: Int,
    @ColumnInfo(name = "parent_name") val parentName: String?,
    @ColumnInfo(name = "parent_icon") val parentIcon: String?,
    @ColumnInfo(name = "parent_color") val parentColor: Int?,
) {
    fun toDomain(): TransactionDetails =
        if (parentName == null) {
            TransactionDetails(transaction.toDomain(), categoryName, null, categoryIcon, categoryColor)
        } else {
            TransactionDetails(
                transaction = transaction.toDomain(),
                categoryName = parentName,
                subcategoryName = categoryName,
                icon = checkNotNull(parentIcon),
                color = checkNotNull(parentColor),
            )
        }
}
