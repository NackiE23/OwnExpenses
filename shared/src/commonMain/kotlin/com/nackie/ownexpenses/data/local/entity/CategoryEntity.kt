package com.nackie.ownexpenses.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nackie.ownexpenses.domain.model.TransactionType

/**
 * Category or subcategory. Subcategories are one level deep: [parentId] points to a
 * top-level category and the subcategory inherits its [type].
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("parent_id"),
        Index("type", "is_archived"),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,
    val name: String,
    val type: TransactionType,
    /** ARGB color. */
    val color: Int,
    /** Key of a bundled icon, resolved by the UI. */
    val icon: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,
)
