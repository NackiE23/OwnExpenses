package com.nackie.ownexpenses.importing

/** One CSV record and the line it starts on (1-based), for error messages. */
internal class CsvRecord(
    val line: Int,
    val fields: List<String>,
) {
    /** A blank line, or a line holding only `""`: Sumlio separates its sections with these. */
    val isBlank: Boolean get() = fields.all { it.isEmpty() }
}

/**
 * RFC 4180 reader: [delimiter]-separated fields, optional double quotes with `""` as an escaped
 * quote, line breaks inside quotes, LF or CRLF line ends, an optional UTF-8 BOM.
 */
internal fun readCsv(
    content: String,
    delimiter: Char = ',',
): List<CsvRecord> {
    val text = content.removePrefix("\uFEFF")
    val records = mutableListOf<CsvRecord>()
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var line = 1
    var recordLine = 1
    var inQuotes = false
    var index = 0

    fun endRecord() {
        fields += field.toString()
        field.clear()
        records += CsvRecord(recordLine, fields.toList())
        fields.clear()
    }

    while (index < text.length) {
        val char = text[index]
        when {
            inQuotes && char == '"' && text.getOrNull(index + 1) == '"' -> {
                field.append('"')
                index++
            }

            char == '"' && (inQuotes || field.isEmpty()) -> {
                inQuotes = !inQuotes
            }

            inQuotes -> {
                if (char == '\n') line++
                field.append(char)
            }

            char == delimiter -> {
                fields += field.toString()
                field.clear()
            }

            char == '\r' && text.getOrNull(index + 1) == '\n' -> {}

            char == '\n' || char == '\r' -> {
                endRecord()
                line++
                recordLine = line
            }

            else -> {
                field.append(char)
            }
        }
        index++
    }
    if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
    return records
}
