package com.nackie.ownexpenses.ui.more

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.nackie.ownexpenses.export.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Saving goes through the system "create document" picker, so no storage permission is
 * needed; sharing writes a cache file exposed through the FileProvider declared in this
 * module's manifest.
 */
@Composable
internal actual fun rememberCsvFileHandler(onResult: (CsvFileResult) -> Unit): CsvFileHandler {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    // Held only while the picker is open. Lost if the process dies meanwhile: the user exports again.
    val pending = remember { PendingContent() }

    val createDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CsvExporter.MIME_TYPE)) { uri ->
            val content = pending.take()
            if (uri == null || content == null) {
                currentOnResult(CsvFileResult.CANCELLED)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val saved = withContext(Dispatchers.IO) { context.writeTo(uri, content) }
                currentOnResult(if (saved) CsvFileResult.SAVED else CsvFileResult.FAILED)
            }
        }

    return remember(context) {
        object : CsvFileHandler {
            override fun save(
                fileName: String,
                content: String,
            ) {
                pending.content = content
                createDocument.launch(fileName)
            }

            override fun share(
                fileName: String,
                content: String,
            ) {
                scope.launch {
                    val uri = withContext(Dispatchers.IO) { context.writeShareFile(fileName, content) }
                    if (uri == null) {
                        currentOnResult(CsvFileResult.FAILED)
                        return@launch
                    }
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = CsvExporter.MIME_TYPE
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri(fileName, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(send, null))
                }
            }
        }
    }
}

private class PendingContent {
    var content: String? = null

    fun take(): String? = content.also { content = null }
}

private fun Context.writeTo(
    uri: Uri,
    content: String,
): Boolean =
    try {
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.encodeToByteArray()) } != null
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }

/** Keeps a single exported file in the cache, replacing the previous one. */
private fun Context.writeShareFile(
    fileName: String,
    content: String,
): Uri? =
    try {
        val directory = File(cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, fileName).apply { writeBytes(content.encodeToByteArray()) }
        FileProvider.getUriForFile(this, "$packageName$AUTHORITY_SUFFIX", file)
    } catch (e: IOException) {
        null
    }

/** Must match res/xml/csv_export_paths.xml and the provider in the androidMain manifest. */
private const val EXPORT_DIRECTORY = "exports"
private const val AUTHORITY_SUFFIX = ".csvexport"
