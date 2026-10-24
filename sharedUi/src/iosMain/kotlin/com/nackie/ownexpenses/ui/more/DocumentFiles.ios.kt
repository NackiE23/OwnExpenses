package com.nackie.ownexpenses.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** iOS document picker and share sheet are part of stage 5; until then every call fails. */
@Composable
internal actual fun rememberDocumentFiles(onResult: (DocumentResult) -> Unit): DocumentFiles {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember {
        object : DocumentFiles {
            override fun save(
                fileName: String,
                mimeType: String,
                content: String,
            ) = currentOnResult(DocumentResult.Failed)

            override fun share(
                fileName: String,
                mimeType: String,
                content: String,
            ) = currentOnResult(DocumentResult.Failed)

            override fun open(mimeTypes: List<String>) = currentOnResult(DocumentResult.Failed)
        }
    }
}
