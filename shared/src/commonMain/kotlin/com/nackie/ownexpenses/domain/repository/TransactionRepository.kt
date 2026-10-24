package com.nackie.ownexpenses.domain.repository

import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodTotals
import com.nackie.ownexpenses.domain.model.Transaction
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

    /** Number of records within [period] per top-level category id, subcategories included. */
    fun observeCategoryUsage(period: Period): Flow<Map<Long, Long>>

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
