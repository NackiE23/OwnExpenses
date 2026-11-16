package com.nackie.ownexpenses.sync

import com.nackie.ownexpenses.data.local.Converters
import com.nackie.ownexpenses.data.local.dao.PendingTransaction
import com.nackie.ownexpenses.data.local.dao.SyncCategory
import com.nackie.ownexpenses.data.local.entity.SyncDeletionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body of `POST /api/expenses/sync/` on the OwnBudget server. Field names are the server's
 * contract (OwnBudget README, "Синхронізація з OwnExpenses"): change them only together with it.
 */
@Serializable
internal data class SyncRequest(
    /** New for every batch, the same when the very same batch is retried: the server applies it once. */
    @SerialName("batch_id") val batchId: String,
    @SerialName("app_version") val appVersion: String,
    /** Always the full list: a category missing from it counts as deleted on the server. */
    val categories: List<SyncCategoryDto>,
    /** Records created or changed since they were last sent. */
    val operations: List<SyncOperationDto>,
    @SerialName("deleted_operations") val deletedOperations: List<SyncDeletionDto>,
)

@Serializable
internal data class SyncCategoryDto(
    val id: String,
    @SerialName("parent_id") val parentId: String?,
    val name: String,
    /** "expense" or "income". */
    val type: String,
    val archived: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
internal data class SyncOperationDto(
    val id: String,
    /** "expense" or "income". */
    val type: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("category_id") val categoryId: String,
    /** ISO-8601 calendar date. */
    val date: String,
    val description: String?,
    /** Epoch milliseconds. */
    @SerialName("created_at") val createdAt: Long,
    /** Epoch milliseconds; the newer version of a record wins on the server. */
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
internal data class SyncDeletionDto(
    val id: String,
    /** Epoch milliseconds. */
    @SerialName("deleted_at") val deletedAt: Long,
)

internal fun SyncCategory.toDto() =
    SyncCategoryDto(
        id = syncId,
        parentId = parentSyncId,
        name = name,
        type = Converters.transactionTypeToKey(type),
        archived = isArchived,
        sortOrder = sortOrder,
    )

internal fun PendingTransaction.toDto() =
    SyncOperationDto(
        id = syncId,
        type = Converters.transactionTypeToKey(type),
        amountMinor = amountMinor,
        categoryId = categorySyncId,
        date = date.toString(),
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

internal fun SyncDeletionEntity.toDto() = SyncDeletionDto(id = syncId, deletedAt = deletedAt)
