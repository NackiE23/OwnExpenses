package com.nackie.ownexpenses.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.data.local.entity.TransactionEntity
import com.nackie.ownexpenses.data.local.toDomain
import com.nackie.ownexpenses.data.local.toEntity
import com.nackie.ownexpenses.domain.model.Transaction
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

internal class RoomTransactionRepository(
    private val database: AppDatabase,
    private val clock: Clock,
) : TransactionRepository {
    private val dao = database.transactionDao()
    private val categoryDao = database.categoryDao()

    override fun observeBalance(asOf: LocalDate): Flow<Long> = dao.observeBalance(asOf).distinctUntilChanged()

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
