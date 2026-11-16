package com.nackie.ownexpenses.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A deleted record the sync server still has to hear about (a tombstone). Written in the same
 * database transaction as the delete and removed once the server has accepted it.
 */
@Entity(tableName = "sync_deletions")
internal data class SyncDeletionEntity(
    @PrimaryKey
    @ColumnInfo(name = "sync_id")
    val syncId: String,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long,
)
