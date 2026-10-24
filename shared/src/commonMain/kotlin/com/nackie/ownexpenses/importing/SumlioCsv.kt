package com.nackie.ownexpenses.importing

import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.time.Instant

/** Names of the categories that receive Sumlio records with a negative amount (localized by the UI). */
data class SumlioCategoryNames(
    /** Income category for refunds: expenses with a negative amount. */
    val refunds: String,
    /** Expense category for corrections: incomes with a negative amount. */
    val other: String,
)

/** A Sumlio operation mapped onto our model; the category is resolved by name when importing. */
internal data class SumlioRecord(
    val type: TransactionType,
    val category: String,
    val subcategory: String?,
    val amountMinor: Long,
    val date: LocalDate,
    /** Sumlio's own timestamp: keeps the original order within a day and identifies re-imports. */
    val createdAt: Long,
    val description: String?,
)

internal class SumlioFile(
    val records: List<SumlioRecord>,
    /** Transfers, other operation types and amounts in another currency. */
    val skippedCount: Int,
    /** Negative amounts imported with the opposite type (refunds and corrections). */
    val reversedCount: Int,
)

internal sealed interface SumlioParseResult {
    data class Parsed(
        val file: SumlioFile,
    ) : SumlioParseResult

    data class Failed(
        val error: SumlioImportError,
        val line: Int? = null,
    ) : SumlioParseResult
}

/**
 * Reads Sumlio's CSV export:
 *
 * ```
 * Date (UTC),Type,From account / from category,From subcategory,To account / to category,To subcategory,
 *   Amount 1,Currency 1,Amount 2,Currency 2,Commission,...,Comment,Tags
 * 2024-11-29T15:33:50.020Z,Expense,Card,"",Cafe,"",700.0,UAH,700.0,UAH,"",...,sushi,""
 * ```
 *
 * Operations come first; a blank line ends them and an accounts table follows, which is ignored
 * (one balance, no accounts). Expenses go from an account to a category, incomes from a category
 * to an account. Timestamps are UTC; the record date is the local date in [timeZone].
 */
internal fun parseSumlioCsv(
    content: String,
    currencyCode: String,
    names: SumlioCategoryNames,
    timeZone: TimeZone,
): SumlioParseResult {
    val rows = readCsv(content)
    val header = rows.firstOrNull()?.fields ?: return SumlioParseResult.Failed(SumlioImportError.NOT_SUMLIO)
    val columns = Columns.of(header) ?: return SumlioParseResult.Failed(SumlioImportError.NOT_SUMLIO)

    val records = mutableListOf<SumlioRecord>()
    var skipped = 0
    var reversed = 0
    for (row in rows.drop(1)) {
        if (row.isBlank) break
        val record =
            try {
                columns.toRecord(row.fields, currencyCode, names, timeZone)
            } catch (e: IllegalArgumentException) {
                return SumlioParseResult.Failed(SumlioImportError.INVALID_ROW, row.line)
            }
        when {
            record == null -> skipped++
            record.reversed -> reversed++
        }
        record?.let { records += it.record }
    }
    return SumlioParseResult.Parsed(SumlioFile(records, skipped, reversed))
}

private class ParsedRow(
    val record: SumlioRecord,
    val reversed: Boolean,
)

private class Columns(
    private val date: Int,
    private val type: Int,
    private val fromCategory: Int,
    private val fromSubcategory: Int,
    private val toCategory: Int,
    private val toSubcategory: Int,
    private val amounts: List<Pair<Int, Int>>,
    private val comment: Int,
    private val tags: Int,
) {
    /** Null for rows that are not imported: transfers, unknown types, another currency, zero. */
    fun toRecord(
        fields: List<String>,
        currencyCode: String,
        names: SumlioCategoryNames,
        timeZone: TimeZone,
    ): ParsedRow? {
        fun field(index: Int) = fields.getOrElse(index) { "" }.trim()

        val originalType =
            when (field(type)) {
                "Expense" -> TransactionType.EXPENSE
                "Income" -> TransactionType.INCOME
                else -> return null
            }
        val amountText =
            amounts
                .firstOrNull { (_, currency) ->
                    field(currency) == currencyCode
                }?.let { (amount, _) -> field(amount) }
                ?: return null
        val amount = requireNotNull(amountText.toDoubleOrNull()) { "Bad amount $amountText" }
        require(amount.isFinite()) { "Bad amount $amountText" }
        val amountMinor = (amount * MINOR_PER_MAJOR).roundToLong()
        if (amountMinor == 0L) return null

        val instant = Instant.parse(field(date))
        val (category, subcategory) =
            if (originalType == TransactionType.EXPENSE) {
                field(toCategory) to field(toSubcategory)
            } else {
                field(fromCategory) to field(fromSubcategory)
            }
        require(category.isNotEmpty()) { "Missing category" }
        val note = listOf(field(comment), field(tags)).filter { it.isNotEmpty() }.joinToString(SEPARATOR)

        val reversed = amountMinor < 0
        val record =
            if (!reversed) {
                SumlioRecord(
                    type = originalType,
                    category = category,
                    subcategory = subcategory.ifEmpty { null },
                    amountMinor = amountMinor,
                    date = instant.toLocalDateTime(timeZone).date,
                    createdAt = instant.toEpochMilliseconds(),
                    description = note.ifEmpty { null },
                )
            } else {
                // A negative expense is money back; keeping its original category in the note.
                val origin = listOf(category, subcategory).filter { it.isNotEmpty() }.joinToString(" / ")
                SumlioRecord(
                    type = originalType.opposite(),
                    category = if (originalType == TransactionType.EXPENSE) names.refunds else names.other,
                    subcategory = null,
                    amountMinor = -amountMinor,
                    date = instant.toLocalDateTime(timeZone).date,
                    createdAt = instant.toEpochMilliseconds(),
                    description = listOf(origin, note).filter { it.isNotEmpty() }.joinToString(SEPARATOR),
                )
            }
        return ParsedRow(record, reversed)
    }

    companion object {
        /** Null when a required column is missing: not a Sumlio export. */
        fun of(header: List<String>): Columns? {
            val names = header.map { it.trim() }

            fun column(name: String): Int? = names.indexOf(name).takeIf { it >= 0 }
            return Columns(
                date = column("Date (UTC)") ?: return null,
                type = column("Type") ?: return null,
                fromCategory = column("From account / from category") ?: return null,
                fromSubcategory = column("From subcategory") ?: return null,
                toCategory = column("To account / to category") ?: return null,
                toSubcategory = column("To subcategory") ?: return null,
                amounts =
                    listOf(
                        (column("Amount 1") ?: return null) to (column("Currency 1") ?: return null),
                        (column("Amount 2") ?: return null) to (column("Currency 2") ?: return null),
                    ),
                comment = column("Comment") ?: return null,
                tags = column("Tags") ?: return null,
            )
        }
    }
}

private fun TransactionType.opposite() =
    when (this) {
        TransactionType.EXPENSE -> TransactionType.INCOME
        TransactionType.INCOME -> TransactionType.EXPENSE
    }

private const val MINOR_PER_MAJOR = 100
private const val SEPARATOR = " · "
