package com.nackie.ownexpenses.importing

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.data.local.entity.TransactionEntity
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.presentation.categories.CategoryPalette
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

enum class SumlioImportError {
    /** Not CSV, or CSV without Sumlio's columns. */
    NOT_SUMLIO,

    /** A row with a date or amount that cannot be read; the line is reported with it. */
    INVALID_ROW,

    /** Every operation is already imported, or none is an expense or income in our currency. */
    NOTHING_TO_IMPORT,

    /** The database refused the write; the transaction rolled back, nothing changed. */
    IMPORT_FAILED,
}

/** What an import adds, shown for confirmation before anything is written. */
data class SumlioImportSummary(
    val recordCount: Int,
    val newCategoryCount: Int,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    /** Σ incomes − Σ expenses of the imported records. */
    val balanceChangeMinor: Long,
    /** Negative amounts imported with the opposite type. */
    val reversedCount: Int,
    /** Transfers, other types and other currencies. */
    val skippedCount: Int,
    /** Already in the database from an earlier import of the same file. */
    val duplicateCount: Int,
)

/** A checked file ready to import. */
class SumlioImport internal constructor(
    internal val records: List<SumlioRecord>,
    val summary: SumlioImportSummary,
)

sealed interface SumlioReadResult {
    data class Ready(
        val file: SumlioImport,
    ) : SumlioReadResult

    data class Failed(
        val error: SumlioImportError,
        /** CSV line of an [SumlioImportError.INVALID_ROW]. */
        val line: Int? = null,
    ) : SumlioReadResult
}

/**
 * One-off move from Sumlio: adds its expenses and incomes to the existing data. Categories and
 * subcategories are matched by name (ignoring case) among the ones in use, missing ones are created
 * after the user's own, most used first. Records already imported (same Sumlio timestamp, amount
 * and type) are skipped, so importing the same file twice adds nothing.
 */
class SumlioImportService internal constructor(
    private val database: AppDatabase,
) {
    suspend fun read(
        content: String,
        currencyCode: String,
        names: SumlioCategoryNames,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): SumlioReadResult {
        val file =
            when (val parsed = parseSumlioCsv(content, currencyCode, names, timeZone)) {
                is SumlioParseResult.Failed -> return SumlioReadResult.Failed(parsed.error, parsed.line)
                is SumlioParseResult.Parsed -> parsed.file
            }
        val existing = database.transactionDao().getAll().mapTo(HashSet()) { it.importKey() }
        val records = file.records.filterNot { it.importKey() in existing }
        if (records.isEmpty()) return SumlioReadResult.Failed(SumlioImportError.NOTHING_TO_IMPORT)

        // Dry run of the category matching, to count what would be created.
        var newCategories = 0
        resolveCategories(records, database.categoryDao().getAll()) {
            newCategories++
            -newCategories.toLong()
        }
        val summary =
            SumlioImportSummary(
                recordCount = records.size,
                newCategoryCount = newCategories,
                firstDate = records.minOf { it.date },
                lastDate = records.maxOf { it.date },
                balanceChangeMinor =
                    records.sumOf {
                        if (it.type ==
                            TransactionType.INCOME
                        ) {
                            it.amountMinor
                        } else {
                            -it.amountMinor
                        }
                    },
                reversedCount = file.reversedCount,
                skippedCount = file.skippedCount,
                duplicateCount = file.records.size - records.size,
            )
        return SumlioReadResult.Ready(SumlioImport(records, summary))
    }

    /** Writes categories and records in one transaction; false if the database refused it. */
    suspend fun import(file: SumlioImport): Boolean {
        val categoryDao = database.categoryDao()
        val transactionDao = database.transactionDao()
        return try {
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    // Checked again: data may have changed since the summary was shown.
                    val existing = transactionDao.getAll().mapTo(HashSet()) { it.importKey() }
                    val records = file.records.filterNot { it.importKey() in existing }
                    val categoryIds = resolveCategories(records, categoryDao.getAll()) { categoryDao.insert(it) }
                    transactionDao.insertAll(
                        records.map { record ->
                            TransactionEntity(
                                type = record.type,
                                amountMinor = record.amountMinor,
                                categoryId = categoryIds.getValue(record.categoryKey()),
                                date = record.date,
                                description = record.description,
                                createdAt = record.createdAt,
                                updatedAt = record.createdAt,
                            )
                        },
                    )
                }
            }
            true
        } catch (e: SQLiteException) {
            false
        }
    }
}

/** Same Sumlio operation: its millisecond timestamp, amount and type. */
private data class ImportKey(
    val createdAt: Long,
    val amountMinor: Long,
    val type: TransactionType,
)

private fun TransactionEntity.importKey() = ImportKey(createdAt, amountMinor, type)

private fun SumlioRecord.importKey() = ImportKey(createdAt, amountMinor, type)

/** Category of a record: type, normalized name and normalized subcategory name. */
private data class CategoryKey(
    val type: TransactionType,
    val name: String,
    val subcategory: String?,
)

private fun SumlioRecord.categoryKey() = CategoryKey(type, category.normalized(), subcategory?.normalized())

private fun String.normalized() = trim().lowercase()

/**
 * Maps every record's category to an id: a category in use with the same name, or a new one made
 * through [insert]. New top-level categories get palette colors; new subcategories inherit the
 * parent's color and icon, as on the categories screen.
 */
private suspend fun resolveCategories(
    records: List<SumlioRecord>,
    existing: List<CategoryEntity>,
    insert: suspend (CategoryEntity) -> Long,
): Map<CategoryKey, Long> {
    val active = existing.filterNot { it.isArchived }
    // Grows with created top-level categories, so each new one gets the next color and position.
    val known = existing.toMutableList()
    val topLevel = mutableMapOf<CategoryKey, CategoryEntity>()
    active.filter { it.parentId == null }.forEach {
        topLevel.getOrPut(CategoryKey(it.type, it.name.normalized(), null)) { it }
    }
    val result = mutableMapOf<CategoryKey, Long>()

    val byCategory = records.groupBy { CategoryKey(it.type, it.category.normalized(), null) }
    for ((key, group) in byCategory.entries.sortedByDescending { it.value.size }) {
        val parent =
            topLevel.getOrPut(key) {
                val siblings = known.filter { it.type == key.type && it.parentId == null }
                val entity =
                    CategoryEntity(
                        name = group.first().category.trim(),
                        type = key.type,
                        color = CategoryPalette.colors[siblings.size % CategoryPalette.colors.size],
                        icon = CategoryPalette.DEFAULT_ICON,
                        sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                    )
                entity.copy(id = insert(entity)).also { known += it }
            }
        result[key] = parent.id

        val bySubcategory = group.filter { it.subcategory != null }.groupBy { it.subcategory!!.normalized() }
        val subcategories = active.filter { it.parentId == parent.id }
        var nextSortOrder = (existing.filter { it.parentId == parent.id }.maxOfOrNull { it.sortOrder } ?: -1) + 1
        for ((name, subGroup) in bySubcategory.entries.sortedByDescending { it.value.size }) {
            val id =
                subcategories.firstOrNull { it.name.normalized() == name }?.id
                    ?: insert(
                        CategoryEntity(
                            parentId = parent.id,
                            name = subGroup.first().subcategory!!.trim(),
                            type = key.type,
                            color = parent.color,
                            icon = parent.icon,
                            sortOrder = nextSortOrder++,
                        ),
                    )
            result[key.copy(subcategory = name)] = id
        }
    }
    return result
}
