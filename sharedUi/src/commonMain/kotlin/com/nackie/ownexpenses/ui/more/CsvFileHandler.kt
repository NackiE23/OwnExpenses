package com.nackie.ownexpenses.ui.more

import androidx.compose.runtime.Composable

/** Hands an exported CSV to the platform: a user-picked location or the share sheet. */
internal interface CsvFileHandler {
    fun save(
        fileName: String,
        content: String,
    )

    fun share(
        fileName: String,
        content: String,
    )
}

internal enum class CsvFileResult {
    SAVED,
    CANCELLED,
    FAILED,
}

/** [onResult] reports the outcome of [CsvFileHandler.save]; sharing is up to the target app. */
@Composable
internal expect fun rememberCsvFileHandler(onResult: (CsvFileResult) -> Unit): CsvFileHandler
