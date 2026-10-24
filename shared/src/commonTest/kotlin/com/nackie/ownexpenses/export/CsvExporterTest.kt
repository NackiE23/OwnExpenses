package com.nackie.ownexpenses.export

import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.Transaction
import com.nackie.ownexpenses.domain.model.TransactionDetails
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CsvExporterTest {
    @Test
    fun matchesTheExampleFromTheSpec() {
        val rows =
            listOf(
                row(LocalDate(2026, 10, 1), TransactionType.INCOME, "Надходження", null, 1000_00, null),
                row(LocalDate(2026, 10, 3), TransactionType.EXPENSE, "Продукти", "На роботі", 300_00, "Сосиски"),
                row(LocalDate(2026, 10, 9), TransactionType.EXPENSE, "Транспорт", "Таксі", 150_00, null),
                row(LocalDate(2026, 10, 17), TransactionType.EXPENSE, "Кафе", null, 327_00, "Круасани"),
            )

        val csv = CsvExporter.export(rows, CsvDelimiter.SEMICOLON, "UAH")

        val expected =
            "\uFEFF" +
                "date;type;category;subcategory;amount;currency;description\r\n" +
                "2026-10-01;income;Надходження;;1000,00;UAH;\r\n" +
                "2026-10-03;expense;Продукти;На роботі;300,00;UAH;Сосиски\r\n" +
                "2026-10-09;expense;Транспорт;Таксі;150,00;UAH;\r\n" +
                "2026-10-17;expense;Кафе;;327,00;UAH;Круасани\r\n"
        assertEquals(expected, csv)
    }

    @Test
    fun startsWithUtf8ByteOrderMark() {
        val bytes = CsvExporter.export(emptyList(), CsvDelimiter.SEMICOLON, "UAH").encodeToByteArray()

        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
    }

    @Test
    fun commaDelimiterUsesDecimalDot() {
        val csv = CsvExporter.export(listOf(row(amountMinor = 12_05)), CsvDelimiter.COMMA, "UAH")

        assertTrue(csv.lines()[1].startsWith("2026-10-03,expense,Кафе,,12.05,UAH,"), csv)
    }

    @Test
    fun fieldsWithDelimiterQuotesOrLineBreaksAreQuoted() {
        val semicolon = CsvExporter.export(listOf(row(description = "Кава; круасан")), CsvDelimiter.SEMICOLON, "UAH")
        val comma = CsvExporter.export(listOf(row(description = "Кава, круасан")), CsvDelimiter.COMMA, "UAH")
        val quotes = CsvExporter.export(listOf(row(description = "Кафе \"Львів\"")), CsvDelimiter.SEMICOLON, "UAH")
        val newline = CsvExporter.export(listOf(row(description = "два\nрядки")), CsvDelimiter.SEMICOLON, "UAH")

        assertTrue(semicolon.contains(";\"Кава; круасан\"\r\n"), semicolon)
        assertTrue(comma.contains(",\"Кава, круасан\"\r\n"), comma)
        assertTrue(quotes.contains(";\"Кафе \"\"Львів\"\"\"\r\n"), quotes)
        assertTrue(newline.contains(";\"два\nрядки\"\r\n"), newline)
    }

    @Test
    fun commaInDescriptionNeedsNoQuotesWithSemicolonDelimiter() {
        val csv = CsvExporter.export(listOf(row(description = "Кава, круасан")), CsvDelimiter.SEMICOLON, "UAH")

        assertTrue(csv.contains(";Кава, круасан\r\n"), csv)
    }

    @Test
    fun textThatLooksLikeAFormulaIsDefused() {
        val csv = CsvExporter.export(listOf(row(description = "=HYPERLINK(\"x\")")), CsvDelimiter.SEMICOLON, "UAH")

        assertTrue(csv.contains(";\"'=HYPERLINK(\"\"x\"\")\"\r\n"), csv)
    }

    @Test
    fun fileNameReflectsPeriod() {
        assertEquals(
            "ownexpenses_2026-10-01_2026-10-31.csv",
            CsvExporter.fileName(Period.monthOf(LocalDate(2026, 10, 24))),
        )
        assertEquals("ownexpenses_all.csv", CsvExporter.fileName(Period.AllTime))
    }

    private fun row(
        date: LocalDate = LocalDate(2026, 10, 3),
        type: TransactionType = TransactionType.EXPENSE,
        category: String = "Кафе",
        subcategory: String? = null,
        amountMinor: Long = 89_00,
        description: String? = null,
    ) = TransactionDetails(
        transaction =
            Transaction(
                id = 1,
                type = type,
                amountMinor = amountMinor,
                categoryId = 1,
                date = date,
                description = description,
                createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0),
            ),
        categoryName = category,
        subcategoryName = subcategory,
        icon = "other",
        color = 0,
    )
}
