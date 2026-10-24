package com.nackie.ownexpenses.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.data.local.entity.TransactionEntity
import com.nackie.ownexpenses.data.local.toDomain
import com.nackie.ownexpenses.data.local.toEntity
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodTotals
import com.nackie.ownexpenses.domain.model.Transaction
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

internal class RoomTransactionRepository(
    private val database: AppDatabase,
    private val clock: Clock,
) : TransactionRepository {
    private val dao = database.transactionDao()
    private val categoryDao = database.categoryDao()

    override fun observeBalance(asOf: LocalDate): Flow<Long> = dao.observeBalance(asOf).distinctUntilChanged()

    override fun observePeriodTotals(period: Period): Flow<PeriodTotals> =
        dao
            .observeTotalsByType(period.start, period.endInclusive)
            .map { rows ->
                val byType = rows.associate { it.type to it.total }
                PeriodTotals(
                    expenseMinor = byType[TransactionType.EXPENSE] ?: 0,
                    incomeMinor = byType[TransactionType.INCOME] ?: 0,
                )
            }.distinctUntilChanged()

    override fun observeCategoryTotals(
        type: TransactionType,
        period: Period,
    ): Flow<Map<Long, Long>> =
        dao
            .observeTopLevelCategoryTotals(type, period.start, period.endInclusive)
            .map { rows -> rows.associate { it.categoryId to it.total } }
            .distinctUntilChanged()

    override fun observeCategoryUsage(period: Period): Flow<Map<Long, Long>> =
        dao
            .observeTopLevelCategoryUsage(period.start, period.endInclusive)
            .map { rows -> rows.associate { it.categoryId to it.total } }
            .distinctUntilChanged()

    override suspend fun getTransaction(id: Long): Transaction? = dao.getById(id)?.toDomain()

    override suspend fun addTransaction(input: TransactionInput): Long =
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val category = requireCategory(input.categoryId)
                val now = clock.now().toEpochMilliseconds()
                dao.insert(
                    TransactionEntity(
                        type = category.type,
                        amountMinor = input.amountMinor,
                        categoryId = category.id,
                        date = input.date,
                        description = input.description.normalizeDescription(),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }

    override suspend fun updateTransaction(
        id: Long,
        input: TransactionInput,
    ) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val existing = requireNotNull(dao.getById(id)) { "Transaction $id not found" }
                val category = requireCategory(input.categoryId)
                dao.update(
                    existing.copy(
                        type = category.type,
                        amountMinor = input.amountMinor,
                        categoryId = category.id,
                        date = input.date,
                        description = input.description.normalizeDescription(),
                        updatedAt = clock.now().toEpochMilliseconds(),
                    ),
                )
            }
        }
    }

    override suspend fun deleteTransaction(id: Long): Transaction? =
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                dao.getById(id)?.also { dao.delete(id) }?.toDomain()
            }
        }

    override suspend fun restoreTransaction(transaction: Transaction) {
        dao.insert(transaction.toEntity())
    }

    private suspend fun requireCategory(id: Long): CategoryEntity =
        requireNotNull(categoryDao.getById(id)) { "Category $id not found" }
}

private fun String?.normalizeDescription(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
