package com.nackie.ownexpenses.backup

import kotlinx.serialization.Serializable

/**
 * JSON backup file, version [BackupService.FORMAT_VERSION]. Field names are part of the format:
 * rename only together with a version bump and a reader for the old version.
 */
@Serializable
internal data class BackupDocument(
    val format: String,
    val version: Int,
    /** ISO-8601 instant. */
    val createdAt: String,
    val currencyCode: String,
    val settings: BackupSettings,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
)

@Serializable
internal data class BackupSettings(
    val csvDelimiter: String,
    val themeMode: String,
)

@Serializable
internal data class BackupCategory(
    val id: Long,
    val parentId: Long? = null,
    val name: String,
    /** "expense" or "income". */
    val type: String,
    /** "#AARRGGBB". */
    val color: String,
    val icon: String,
    val sortOrder: Int,
    val archived: Boolean = false,
)

@Serializable
internal data class BackupTransaction(
    val id: Long,
    /** "expense" or "income". */
    val type: String,
    val amountMinor: Long,
    val categoryId: Long,
    /** ISO-8601 calendar date. */
    val date: String,
    val description: String? = null,
    /** Epoch milliseconds. */
    val createdAt: Long,
    /** Epoch milliseconds. */
    val updatedAt: Long,
)
