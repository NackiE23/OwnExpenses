package com.nackie.ownexpenses.backup

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.Converters
import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.data.local.entity.TransactionEntity
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.domain.repository.SettingsRepository
import com.nackie.ownexpenses.presentation.categories.CategoryPalette
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/** A backup ready to be saved as a file. */
class BackupFile(
    val fileName: String,
    val content: String,
    val categoryCount: Int,
    val transactionCount: Int,
)

/** A parsed and fully validated backup; restoring it cannot fail on its content. */
class RestorableBackup internal constructor(
    internal val categories: List<CategoryEntity>,
    internal val transactions: List<TransactionEntity>,
    internal val csvDelimiter: CsvDelimiter?,
    internal val themeMode: ThemeMode?,
    val createdAt: Instant?,
) {
    val categoryCount: Int get() = categories.size
    val transactionCount: Int get() = transactions.size
}

sealed interface BackupReadResult {
    data class Valid(
        val backup: RestorableBackup,
    ) : BackupReadResult

    data class Invalid(
        val error: BackupError,
    ) : BackupReadResult
}

enum class BackupError {
    /** Not JSON, or JSON of something else. */
    NOT_A_BACKUP,

    /** Made by a newer version of the app. */
    NEWER_VERSION,

    /** The database refused the write; the transaction rolled back, nothing changed. */
    RESTORE_FAILED,

    /** Our format, but the data is inconsistent (broken references, bad values). */
    INVALID_DATA,
}

/**
 * Full backup of categories, records and settings as one JSON file (docs/TZ.md, FR-8).
 * Restoring replaces everything in a single database transaction, after validation.
 */
class BackupService internal constructor(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    suspend fun createBackup(): BackupFile {
        val (categories, transactions) =
            database.useWriterConnection { connection ->
                // One transaction so categories and records are a consistent snapshot.
                connection.immediateTransaction {
                    database.categoryDao().getAll() to database.transactionDao().getAll()
                }
            }
        val settings = settingsRepository.settings.first()
        val now = clock.now()
        val document =
            BackupDocument(
                format = FORMAT_ID,
                version = FORMAT_VERSION,
                createdAt = now.toString(),
                currencyCode = settings.currencyCode,
                settings = BackupSettings(settings.csvDelimiter.key, settings.themeMode.key),
                categories = categories.map { it.toBackup() },
                transactions = transactions.map { it.toBackup() },
            )
        return BackupFile(
            fileName = "ownexpenses_backup_${now.toString().take(DATE_LENGTH)}.json",
            content = json.encodeToString(BackupDocument.serializer(), document),
            categoryCount = categories.size,
            transactionCount = transactions.size,
        )
    }

    fun read(content: String): BackupReadResult {
        val document =
            try {
                json.decodeFromString(BackupDocument.serializer(), content)
            } catch (e: SerializationException) {
                return BackupReadResult.Invalid(BackupError.NOT_A_BACKUP)
            } catch (e: IllegalArgumentException) {
                return BackupReadResult.Invalid(BackupError.NOT_A_BACKUP)
            }
        if (document.format != FORMAT_ID) return BackupReadResult.Invalid(BackupError.NOT_A_BACKUP)
        if (document.version > FORMAT_VERSION) return BackupReadResult.Invalid(BackupError.NEWER_VERSION)

        return try {
            BackupReadResult.Valid(document.toRestorable())
        } catch (e: IllegalArgumentException) {
            BackupReadResult.Invalid(BackupError.INVALID_DATA)
        }
    }

    /**
     * Replaces all categories and records with the backup's, then applies its settings. Returns
     * false if the database refused the write; it rolls back, so the old data stays untouched.
     */
    suspend fun restore(backup: RestorableBackup): Boolean {
        val transactionDao = database.transactionDao()
        val categoryDao = database.categoryDao()
        val written =
            try {
                database.useWriterConnection { connection ->
                    connection.immediateTransaction {
                        transactionDao.deleteAll()
                        categoryDao.deleteAllSubcategories()
                        categoryDao.deleteAll()
                        // Parents first, so foreign keys hold at every insert.
                        categoryDao.insertAll(backup.categories.sortedBy { it.parentId != null })
                        transactionDao.insertAll(backup.transactions)
                    }
                }
                true
            } catch (e: SQLiteException) {
                false
            }
        if (!written) return false
        backup.csvDelimiter?.let { settingsRepository.setCsvDelimiter(it) }
        backup.themeMode?.let { settingsRepository.setThemeMode(it) }
        // The user's own category list now exists; never seed defaults on top of it.
        settingsRepository.markDefaultCategoriesSeeded()
        return true
    }

    companion object {
        const val FORMAT_ID = "ownexpenses-backup"
        const val FORMAT_VERSION = 1
        const val MIME_TYPE = "application/json"

        private const val DATE_LENGTH = 10

        private val json =
            Json {
                prettyPrint = true
                encodeDefaults = true
                // Lets an older app read backups that only add optional fields.
                ignoreUnknownKeys = true
            }
    }
}

private fun CategoryEntity.toBackup() =
    BackupCategory(
        id = id,
        parentId = parentId,
        name = name,
        type = Converters.transactionTypeToKey(type),
        color = color.toColorHex(),
        icon = icon,
        sortOrder = sortOrder,
        archived = isArchived,
    )

private fun TransactionEntity.toBackup() =
    BackupTransaction(
        id = id,
        type = Converters.transactionTypeToKey(type),
        amountMinor = amountMinor,
        categoryId = categoryId,
        date = date.toString(),
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** Validates everything the database would otherwise reject or store inconsistently. */
private fun BackupDocument.toRestorable(): RestorableBackup {
    val categoriesById = categories.associateBy { it.id }
    require(categoriesById.size == categories.size) { "Duplicate category ids" }
    require(transactions.map { it.id }.toSet().size == transactions.size) { "Duplicate record ids" }

    val categoryEntities =
        categories.map { category ->
            val type = category.type.toTransactionType()
            category.parentId?.let { parentId ->
                val parent = requireNotNull(categoriesById[parentId]) { "Unknown parent $parentId" }
                require(parent.parentId == null) { "Subcategories are one level deep" }
                require(parent.type == category.type) { "Subcategory type differs from parent" }
            }
            require(category.name.isNotBlank()) { "Blank category name" }
            CategoryEntity(
                id = category.id,
                parentId = category.parentId,
                name = category.name,
                type = type,
                // Backups made before the saturated palette get its colors, as the database migration does.
                color = CategoryPalette.upgrade(category.color.parseColorHex()),
                icon = category.icon,
                sortOrder = category.sortOrder,
                isArchived = category.archived,
            )
        }

    val transactionEntities =
        transactions.map { transaction ->
            val category = requireNotNull(categoriesById[transaction.categoryId]) { "Unknown category" }
            require(category.type == transaction.type) { "Record type differs from its category" }
            require(transaction.amountMinor > 0) { "Amount must be positive" }
            TransactionEntity(
                id = transaction.id,
                type = transaction.type.toTransactionType(),
                amountMinor = transaction.amountMinor,
                categoryId = transaction.categoryId,
                date = LocalDate.parse(transaction.date),
                description = transaction.description?.takeIf { it.isNotBlank() },
                createdAt = transaction.createdAt,
                updatedAt = transaction.updatedAt,
            )
        }

    return RestorableBackup(
        categories = categoryEntities,
        transactions = transactionEntities,
        csvDelimiter = CsvDelimiter.fromKey(settings.csvDelimiter),
        themeMode = ThemeMode.fromKey(settings.themeMode),
        createdAt = runCatching { Instant.parse(createdAt) }.getOrNull(),
    )
}

private fun String.toTransactionType() =
    try {
        Converters.keyToTransactionType(this)
    } catch (e: IllegalStateException) {
        throw IllegalArgumentException("Unknown type $this", e)
    }

private const val COLOR_HEX_DIGITS = 8

private fun Int.toColorHex(): String = "#" + toUInt().toString(16).uppercase().padStart(COLOR_HEX_DIGITS, '0')

private fun String.parseColorHex(): Int {
    val hex = removePrefix("#")
    require(hex.length == COLOR_HEX_DIGITS) { "Color must be #AARRGGBB, was $this" }
    return hex.toLong(16).toInt()
}
