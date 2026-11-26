package com.nackie.ownexpenses.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.sync.newSyncId
import kotlinx.datetime.LocalDate

/**
 * Single expense or income. [amountMinor] is always positive and stored in minor units
 * (kopiykas); the sign comes from [type]. The balance is never stored, it is computed
 * from this table.
 *
 * [syncId] identifies the record on the sync server for life; [syncPending] marks records the
 * server has not received in their current state yet (set on every insert and update).
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("date"),
        Index("category_id"),
        Index(value = ["sync_id"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: TransactionType,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    /** Category or subcategory. */
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    /** Calendar date without time, so time zone changes never move a record between days. */
    val date: LocalDate,
    val description: String? = null,
    /** Notable expense ("star"); always has a description, never set on incomes. */
    @ColumnInfo(name = "is_starred", defaultValue = "0")
    val isStarred: Boolean = false,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    /** The default only serves the migration; new rows always get a fresh UUID. */
    @ColumnInfo(name = "sync_id", defaultValue = "''")
    val syncId: String = newSyncId(),
    @ColumnInfo(name = "sync_pending", defaultValue = "1")
    val syncPending: Boolean = true,
)
