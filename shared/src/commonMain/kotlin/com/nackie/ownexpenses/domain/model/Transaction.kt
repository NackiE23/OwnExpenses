package com.nackie.ownexpenses.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Single expense or income. [amountMinor] is positive and in minor units (kopiykas);
 * whether it adds to or subtracts from the balance is decided by [type].
 */
data class Transaction(
    val id: Long,
    val type: TransactionType,
    val amountMinor: Long,
    /** Category or subcategory. */
    val categoryId: Long,
    val date: LocalDate,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Contribution to the balance: positive for incomes, negative for expenses. */
    val signedAmountMinor: Long
        get() =
            when (type) {
                TransactionType.INCOME -> amountMinor
                TransactionType.EXPENSE -> -amountMinor
            }
}

/**
 * User input for adding or editing a transaction. The type is not part of it: it always
 * comes from the chosen category.
 */
data class TransactionInput(
    val categoryId: Long,
    val amountMinor: Long,
    val date: LocalDate,
    val description: String? = null,
) {
    init {
        require(amountMinor > 0) { "Amount must be positive, was $amountMinor" }
    }
}
