package com.nackie.ownexpenses.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** iOS file export (document picker / share sheet) is part of stage 5; until then it fails. */
@Composable
internal actual fun rememberCsvFileHandler(onResult: (CsvFileResult) -> Unit): CsvFileHandler {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember {
        object : CsvFileHandler {
            override fun save(
                fileName: String,
                content: String,
            ) = currentOnResult(CsvFileResult.FAILED)

            override fun share(
                fileName: String,
                content: String,
            ) = currentOnResult(CsvFileResult.FAILED)
        }
    }
}
