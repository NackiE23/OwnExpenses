package com.nackie.ownexpenses.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.datetime.LocalDate

/**
 * Single expense or income. [amountMinor] is always positive and stored in minor units
 * (kopiykas); the sign comes from [type]. The balance is never stored, it is computed
 * from this table.
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
    /** Epoch milliseconds. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
