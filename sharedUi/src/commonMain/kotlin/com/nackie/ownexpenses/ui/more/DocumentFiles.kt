package com.nackie.ownexpenses.ui.more

import androidx.compose.runtime.Composable

/**
 * Exchange of whole files with the platform: save to a user-picked location, send through the
 * share sheet, or open a user-picked file. Used for CSV exports and backups.
 */
internal interface DocumentFiles {
    fun save(
        fileName: String,
        mimeType: String,
        content: String,
    )

    fun share(
        fileName: String,
        mimeType: String,
        content: String,
    )

    /** Lets the user pick a file; its text arrives as [DocumentResult.Opened]. */
    fun open(mimeTypes: List<String>)
}

internal sealed interface DocumentResult {
    data object Saved : DocumentResult

    data object Cancelled : DocumentResult

    data object Failed : DocumentResult

    data class Opened(
        val content: String,
    ) : DocumentResult
}

/** [onResult] reports saving and opening; sharing is up to the target app. */
@Composable
internal expect fun rememberDocumentFiles(onResult: (DocumentResult) -> Unit): DocumentFiles
