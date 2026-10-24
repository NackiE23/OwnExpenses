package com.nackie.ownexpenses.domain.repository

import com.nackie.ownexpenses.domain.model.Transaction
import com.nackie.ownexpenses.domain.model.TransactionInput
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface TransactionRepository {
    /**
     * Running balance in minor units: all incomes minus all expenses dated on or before
     * [asOf]. Computed by the database on every change, never stored. May be negative.
     */
    fun observeBalance(asOf: LocalDate): Flow<Long>

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
