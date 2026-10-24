package com.nackie.ownexpenses.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nackie.ownexpenses.data.local.entity.TransactionEntity
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

internal data class TypeTotal(
    val type: TransactionType,
    val total: Long,
)

internal data class DayTotal(
    val date: LocalDate,
    val total: Long,
)

internal data class CategoryTotal(
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    val total: Long,
)

@Dao
internal interface TransactionDao {
    /** 'income' is the stored key of TransactionType.INCOME, see Converters. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'income' THEN amount_minor ELSE -amount_minor END), 0)
        FROM transactions
        WHERE date <= :asOf
        """,
    )
    fun observeBalance(asOf: LocalDate): Flow<Long>

    @Query(
        """
        SELECT type, SUM(amount_minor) AS total
        FROM transactions
        WHERE date BETWEEN :from AND :to
        GROUP BY type
        """,
    )
    fun observeTotalsByType(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<TypeTotal>>

    /** Totals per top-level category; records in a subcategory count towards its parent. */
    @Query(
        """
        SELECT COALESCE(c.parent_id, c.id) AS category_id, SUM(t.amount_minor) AS total
        FROM transactions t JOIN categories c ON c.id = t.category_id
        WHERE t.type = :type AND t.date BETWEEN :from AND :to
        GROUP BY COALESCE(c.parent_id, c.id)
        """,
    )
    fun observeTopLevelCategoryTotals(
        type: TransactionType,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<CategoryTotal>>

    /** Number of records per top-level category, subcategories included. */
    @Query(
        """
        SELECT COALESCE(c.parent_id, c.id) AS category_id, COUNT(*) AS total
        FROM transactions t JOIN categories c ON c.id = t.category_id
        WHERE t.date BETWEEN :from AND :to
        GROUP BY COALESCE(c.parent_id, c.id)
        """,
    )
    fun observeTopLevelCategoryUsage(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<CategoryTotal>>

    /** Sums per day; days without records are absent. */
    @Query(
        """
        SELECT date, SUM(amount_minor) AS total
        FROM transactions
        WHERE type = :type AND date BETWEEN :from AND :to
        GROUP BY date
        """,
    )
    fun observeDailyTotals(
        type: TransactionType,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<DayTotal>>

    /** Sums per category or subcategory as recorded, without rolling subcategories up. */
    @Query(
        """
        SELECT category_id, SUM(amount_minor) AS total
        FROM transactions
        WHERE type = :type AND date BETWEEN :from AND :to
        GROUP BY category_id
        """,
    )
    fun observeTotalsPerCategory(
        type: TransactionType,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM transactions ORDER BY id")
    suspend fun getAll(): List<TransactionEntity>

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /** Newest first: by date, then by the moment of entry. */
    @Query("$TRANSACTIONS_WITH_CATEGORIES ORDER BY t.date DESC, t.created_at DESC, t.id DESC")
    fun observeWithCategories(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<TransactionWithCategory>>

    /** Oldest first, as exported to CSV. */
    @Query("$TRANSACTIONS_WITH_CATEGORIES ORDER BY t.date, t.created_at, t.id")
    suspend fun getWithCategoriesChronologically(
        from: LocalDate,
        to: LocalDate,
    ): List<TransactionWithCategory>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    companion object {
        const val TRANSACTIONS_WITH_CATEGORIES = """
            SELECT t.*,
                c.name AS category_name, c.icon AS category_icon, c.color AS category_color,
                p.name AS parent_name, p.icon AS parent_icon, p.color AS parent_color
            FROM transactions t
            JOIN categories c ON c.id = t.category_id
            LEFT JOIN categories p ON p.id = c.parent_id
            WHERE t.date BETWEEN :from AND :to
        """
    }
}
