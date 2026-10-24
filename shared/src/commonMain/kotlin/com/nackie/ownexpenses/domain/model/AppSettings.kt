package com.nackie.ownexpenses.domain.model

data class AppSettings(
    /** The app supports a single currency (docs/TZ.md), so this is not user-editable. */
    val currencyCode: String = DEFAULT_CURRENCY_CODE,
    val csvDelimiter: CsvDelimiter = CsvDelimiter.SEMICOLON,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        const val DEFAULT_CURRENCY_CODE = "UAH"
    }
}

/** CSV field delimiter. The decimal separator follows from it: comma for `;`, dot for `,`. */
enum class CsvDelimiter(
    val char: Char,
    val decimalSeparator: Char,
) {
    SEMICOLON(';', ','),
    COMMA(',', '.'),
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
