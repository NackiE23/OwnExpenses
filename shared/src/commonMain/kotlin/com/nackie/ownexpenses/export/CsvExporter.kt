package com.nackie.ownexpenses.export

import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.TransactionDetails
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlin.math.absoluteValue

/**
 * CSV export as specified in docs/TZ.md:
 *
 * ```
 * date;type;category;subcategory;amount;currency;description
 * 2026-10-03;expense;Продукти;На роботі;300,00;UAH;Сосиски
 * ```
 *
 * UTF-8 with BOM so Excel reads Cyrillic; ISO dates; the decimal separator follows the field
 * delimiter (comma for `;`, dot for `,`); RFC 4180 quoting and CRLF line ends.
 */
object CsvExporter {
    const val MIME_TYPE = "text/csv"

    private const val BOM = "\uFEFF"
    private const val LINE_END = "\r\n"
    private val HEADER = listOf("date", "type", "category", "subcategory", "amount", "currency", "description")

    /** Characters that make spreadsheets treat a cell as a formula (CSV injection). */
    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')

    fun export(
        rows: List<TransactionDetails>,
        delimiter: CsvDelimiter,
        currencyCode: String,
    ): String =
        buildString {
            append(BOM)
            appendRecord(HEADER, delimiter)
            rows.forEach { row ->
                val transaction = row.transaction
                appendRecord(
                    listOf(
                        transaction.date.toString(),
                        transaction.type.key,
                        row.categoryName.asText(),
                        row.subcategoryName.orEmpty().asText(),
                        formatAmount(transaction.amountMinor, delimiter.decimalSeparator),
                        currencyCode,
                        transaction.description.orEmpty().asText(),
                    ),
                    delimiter,
                )
            }
        }

    /** "ownexpenses_2026-10-01_2026-10-31.csv", or "ownexpenses_all.csv" for all time. */
    fun fileName(period: Period): String =
        if (period == Period.AllTime) {
            "ownexpenses_all.csv"
        } else {
            "ownexpenses_${period.start}_${period.endInclusive}.csv"
        }

    private fun StringBuilder.appendRecord(
        fields: List<String>,
        delimiter: CsvDelimiter,
    ) {
        fields.joinTo(this, separator = delimiter.char.toString()) { it.quotedIfNeeded(delimiter.char) }
        append(LINE_END)
    }

    private fun String.quotedIfNeeded(delimiter: Char): String =
        if (any { it == delimiter || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }

    /** User-entered text that a spreadsheet would run as a formula gets a leading apostrophe. */
    private fun String.asText(): String = if (firstOrNull() in FORMULA_PREFIXES) "'$this" else this

    /** Always two decimals, no grouping: "1000,00". */
    private fun formatAmount(
        minorUnits: Long,
        decimalSeparator: Char,
    ): String {
        val absolute = minorUnits.absoluteValue
        return "${absolute / 100}$decimalSeparator${(absolute % 100).toString().padStart(2, '0')}"
    }

    private val TransactionType.key: String
        get() =
            when (this) {
                TransactionType.EXPENSE -> "expense"
                TransactionType.INCOME -> "income"
            }
}
