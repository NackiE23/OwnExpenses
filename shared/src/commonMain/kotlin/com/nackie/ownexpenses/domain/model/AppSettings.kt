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

/**
 * CSV field delimiter. The decimal separator follows from it: comma for `;`, dot for `,`.
 * [key] is how it is stored (settings, backups) and must never change.
 */
enum class CsvDelimiter(
    val char: Char,
    val decimalSeparator: Char,
    val key: String,
) {
    SEMICOLON(';', ',', "semicolon"),
    COMMA(',', '.', "comma"),
    ;

    companion object {
        fun fromKey(key: String?): CsvDelimiter? = entries.firstOrNull { it.key == key }
    }
}

/** [key] is how it is stored (settings, backups) and must never change. */
enum class ThemeMode(
    val key: String,
) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode? = entries.firstOrNull { it.key == key }
    }
}
