package com.nackie.ownexpenses.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nackie.ownexpenses.data.local.entity.SyncDeletionEntity
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** A record waiting to be sent, with its category already resolved to the category's sync id. */
internal data class PendingTransaction(
    @ColumnInfo(name = "sync_id")
    val syncId: String,
    val type: TransactionType,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "category_sync_id")
    val categorySyncId: String,
    val date: LocalDate,
    val description: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/** Category as the sync server sees it: no color or icon, parent by sync id. */
internal data class SyncCategory(
    @ColumnInfo(name = "sync_id")
    val syncId: String,
    @ColumnInfo(name = "parent_sync_id")
    val parentSyncId: String?,
    val name: String,
    val type: TransactionType,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)

@Dao
internal interface SyncDao {
    @Query(
        """
        SELECT c.sync_id, p.sync_id AS parent_sync_id, c.name, c.type, c.is_archived, c.sort_order
        FROM categories c
        LEFT JOIN categories p ON p.id = c.parent_id
        ORDER BY c.parent_id IS NOT NULL, c.id
        """,
    )
    suspend fun getCategories(): List<SyncCategory>

    /** Oldest changes first, so an interrupted sync resumes where it stopped. */
    @Query(
        """
        SELECT t.sync_id, t.type, t.amount_minor, c.sync_id AS category_sync_id, t.date, t.description,
            t.created_at, t.updated_at
        FROM transactions t
        JOIN categories c ON c.id = t.category_id
        WHERE t.sync_pending = 1
        ORDER BY t.updated_at, t.id
        LIMIT :limit
        """,
    )
    suspend fun getPendingTransactions(limit: Int): List<PendingTransaction>

    @Query("SELECT * FROM sync_deletions ORDER BY deleted_at LIMIT :limit")
    suspend fun getDeletions(limit: Int): List<SyncDeletionEntity>

    /** Waiting records plus waiting deletions, for the status line. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM transactions WHERE sync_pending = 1)
            + (SELECT COUNT(*) FROM sync_deletions)
        """,
    )
    fun observePendingCount(): Flow<Int>

    /**
     * Emits after every write to the tables a sync reads, categories included (they are always sent
     * in full, so any change to them is a reason to sync). The value itself means nothing: Room
     * re-runs the query on each invalidation of the tables it names.
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM transactions WHERE sync_pending = 1)
            + (SELECT COUNT(*) FROM sync_deletions)
            + (SELECT COUNT(*) FROM categories)
        """,
    )
    fun observeChanges(): Flow<Int>

    /** Only if unchanged since it was sent: an edit made during the request must still be sent. */
    @Query("UPDATE transactions SET sync_pending = 0 WHERE sync_id = :syncId AND updated_at = :updatedAt")
    suspend fun markSent(
        syncId: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM sync_deletions WHERE sync_id = :syncId AND deleted_at = :deletedAt")
    suspend fun deleteSentDeletion(
        syncId: String,
        deletedAt: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletion(deletion: SyncDeletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletions(deletions: List<SyncDeletionEntity>)

    /** Returns how many tombstones were removed: 0 if the server has already received this deletion. */
    @Query("DELETE FROM sync_deletions WHERE sync_id = :syncId")
    suspend fun deleteDeletion(syncId: String): Int

    @Query("SELECT sync_id FROM transactions")
    suspend fun getTransactionSyncIds(): List<String>

    /** After a restore the phone's data is what counts, so everything is resent as a fresh change. */
    @Query("UPDATE transactions SET sync_pending = 1, updated_at = MAX(updated_at, :now)")
    suspend fun markAllPending(now: Long)

    /** A new token may be a new device on the server, which has nothing yet: send everything again. */
    @Query("UPDATE transactions SET sync_pending = 1")
    suspend fun markAllPendingKeepingTimes()

    @Query("DELETE FROM sync_deletions WHERE sync_id IN (SELECT sync_id FROM transactions)")
    suspend fun deleteDeletionsOfExistingTransactions()
}
