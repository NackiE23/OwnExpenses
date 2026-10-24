package com.nackie.ownexpenses.domain.model

/** Sums of a period in minor units. Unlike the balance, these start from zero every period. */
data class PeriodTotals(
    val expenseMinor: Long = 0,
    val incomeMinor: Long = 0,
) {
    fun of(type: TransactionType): Long =
        when (type) {
            TransactionType.EXPENSE -> expenseMinor
            TransactionType.INCOME -> incomeMinor
        }
}
