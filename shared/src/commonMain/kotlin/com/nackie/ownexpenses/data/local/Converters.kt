package com.nackie.ownexpenses.data.local

import androidx.room.TypeConverter
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.datetime.LocalDate

/**
 * Column encodings. Dates are stored as epoch days so range filters and sorting are plain
 * integer comparisons; enums are stored by a stable string key, not by ordinal or name.
 */
internal object Converters {
    @TypeConverter
    fun localDateToEpochDays(date: LocalDate): Long = date.toEpochDays()

    @TypeConverter
    fun epochDaysToLocalDate(epochDays: Long): LocalDate = LocalDate.fromEpochDays(epochDays)

    @TypeConverter
    fun transactionTypeToKey(type: TransactionType): String =
        when (type) {
            TransactionType.EXPENSE -> EXPENSE_KEY
            TransactionType.INCOME -> INCOME_KEY
        }

    @TypeConverter
    fun keyToTransactionType(key: String): TransactionType =
        when (key) {
            EXPENSE_KEY -> TransactionType.EXPENSE
            INCOME_KEY -> TransactionType.INCOME
            else -> error("Unknown transaction type key: $key")
        }

    private const val EXPENSE_KEY = "expense"
    private const val INCOME_KEY = "income"
}
