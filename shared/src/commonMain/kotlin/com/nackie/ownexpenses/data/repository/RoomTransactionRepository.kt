package com.nackie.ownexpenses.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.data.local.entity.SyncDeletionEntity
import com.nackie.ownexpenses.data.local.entity.TransactionEntity
import com.nackie.ownexpenses.data.local.toDomain
import com.nackie.ownexpenses.data.local.toEntity
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodTotals
import com.nackie.ownexpenses.domain.model.Transaction
import com.nackie.ownexpenses.domain.model.TransactionDetails
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
    private val syncDao = database.syncDao()

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

    override fun observeDailyTotals(
        type: TransactionType,
        period: Period,
    ): Flow<Map<LocalDate, Long>> =
        dao
            .observeDailyTotals(type, period.start, period.endInclusive)
            .map { rows -> rows.associate { it.date to it.total } }
            .distinctUntilChanged()

    override fun observeTotalsPerCategory(
        type: TransactionType,
        period: Period,
    ): Flow<Map<Long, Long>> =
        dao
            .observeTotalsPerCategory(type, period.start, period.endInclusive)
            .map { rows -> rows.associate { it.categoryId to it.total } }
            .distinctUntilChanged()

    override fun observeTransactionDetails(period: Period): Flow<List<TransactionDetails>> =
        dao.observeWithCategories(period.start, period.endInclusive).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getTransactionDetailsChronologically(period: Period): List<TransactionDetails> =
        dao.getWithCategoriesChronologically(period.start, period.endInclusive).map { it.toDomain() }

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
                        updatedAt = nextVersion(existing.updatedAt),
                        syncPending = true,
                    ),
                )
            }
        }
    }

    override suspend fun deleteTransaction(id: Long): Transaction? =
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val existing = dao.getById(id) ?: return@immediateTransaction null
                dao.delete(id)
                // Tombstone for the sync server, written atomically with the delete. Newer than the
                // record itself, or the server would take the deletion for an outdated one.
                syncDao.insertDeletion(SyncDeletionEntity(existing.syncId, nextVersion(existing.updatedAt)))
                existing.toDomain()
            }
        }

    override suspend fun restoreTransaction(transaction: Transaction) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val entity = transaction.toEntity()
                val deletedAt = syncDao.getDeletedAt(entity.syncId)
                syncDao.deleteDeletion(entity.syncId)
                // Always newer than both the record and its deletion: the deletion may already be on the
                // server, even if its tombstone is still here (the batch is on the way right now).
                val updatedAt = nextVersion(maxOf(entity.updatedAt, deletedAt ?: 0L))
                dao.insert(entity.copy(updatedAt = updatedAt, syncPending = true))
            }
        }
    }

    /**
     * Change time for a record last changed at [previous]: now, but always later than [previous]. The
     * sync server keeps the version with the latest time, so a phone clock set back must not make a
     * fresh edit look older than the one already sent, and two edits never share a time.
     */
    private fun nextVersion(previous: Long): Long = maxOf(clock.now().toEpochMilliseconds(), previous + 1)

    private suspend fun requireCategory(id: Long): CategoryEntity =
        requireNotNull(categoryDao.getById(id)) { "Category $id not found" }
}

private fun String?.normalizeDescription(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
