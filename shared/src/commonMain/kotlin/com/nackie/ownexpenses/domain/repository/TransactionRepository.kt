package com.nackie.ownexpenses.domain.repository

import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodTotals
import com.nackie.ownexpenses.domain.model.Transaction
import com.nackie.ownexpenses.domain.model.TransactionDetails
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface TransactionRepository {
    /**
     * Running balance in minor units: all incomes minus all expenses dated on or before
     * [asOf]. Computed by the database on every change, never stored. May be negative.
     */
    fun observeBalance(asOf: LocalDate): Flow<Long>

    /** Expense and income sums of records dated within [period]. */
    fun observePeriodTotals(period: Period): Flow<PeriodTotals>

    /**
     * Sums of [type] records within [period] per top-level category id; records in a
     * subcategory count towards its parent. Categories without records are absent.
     */
    fun observeCategoryTotals(
        type: TransactionType,
        period: Period,
    ): Flow<Map<Long, Long>>

    /** Sums of [type] records within [period] per day; days without records are absent. */
    fun observeDailyTotals(
        type: TransactionType,
        period: Period,
    ): Flow<Map<LocalDate, Long>>

    /** Sums of [type] records within [period] per category or subcategory id, as recorded. */
    fun observeTotalsPerCategory(
        type: TransactionType,
        period: Period,
    ): Flow<Map<Long, Long>>

    /** Records of [period] with their categories, newest first (history). */
    fun observeTransactionDetails(period: Period): Flow<List<TransactionDetails>>

    /** Records of [period] with their categories, oldest first (CSV export). */
    suspend fun getTransactionDetailsChronologically(period: Period): List<TransactionDetails>

    suspend fun getTransaction(id: Long): Transaction?

    /** Adds a record typed after its category and returns its id. */
    suspend fun addTransaction(input: TransactionInput): Long

    suspend fun updateTransaction(
        id: Long,
        input: TransactionInput,
    )

    /** Deletes a record and returns it so the deletion can be undone with [restoreTransaction]. */
    suspend fun deleteTransaction(id: Long): Transaction?

    /** Puts back a record removed by [deleteTransaction], with its original id and timestamps. */
    suspend fun restoreTransaction(transaction: Transaction)
}
