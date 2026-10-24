package com.nackie.ownexpenses.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** App-private Documents directory, where the database and settings files live. */
@OptIn(ExperimentalForeignApi::class)
internal fun documentDirectoryPath(): String {
    val url =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(url?.path) { "Documents directory is unavailable" }
}
