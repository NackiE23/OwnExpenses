package com.nackie.ownexpenses.data.local

import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.data.local.entity.TransactionEntity
import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.Transaction
import com.nackie.ownexpenses.sync.newSyncId
import kotlin.time.Instant

internal fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        parentId = parentId,
        name = name,
        type = type,
        color = color,
        icon = icon,
        sortOrder = sortOrder,
        isArchived = isArchived,
        syncId = syncId,
    )

internal fun TransactionEntity.toDomain(): Transaction =
    Transaction(
        id = id,
        type = type,
        amountMinor = amountMinor,
        categoryId = categoryId,
        date = date,
        description = description,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        syncId = syncId,
    )

internal fun Transaction.toEntity(): TransactionEntity =
    TransactionEntity(
        id = id,
        type = type,
        amountMinor = amountMinor,
        categoryId = categoryId,
        date = date,
        description = description,
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt.toEpochMilliseconds(),
        syncId = syncId.ifEmpty { newSyncId() },
    )

internal fun Category.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        parentId = parentId,
        name = name,
        type = type,
        color = color,
        icon = icon,
        sortOrder = sortOrder,
        isArchived = isArchived,
        syncId = syncId.ifEmpty { newSyncId() },
    )
