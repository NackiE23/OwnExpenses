package com.nackie.ownexpenses.ui.more

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Saving and opening go through the system document picker, so no storage permission is
 * needed; sharing writes a cache file exposed through the FileProvider declared in this
 * module's manifest.
 */
@Composable
internal actual fun rememberDocumentFiles(onResult: (DocumentResult) -> Unit): DocumentFiles {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    // Held only while the picker is open. Lost if the process dies meanwhile: the user retries.
    val pending = remember { PendingContent() }

    val createDocument =
        rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
            val content = pending.take()
            if (uri == null || content == null) {
                currentOnResult(DocumentResult.Cancelled)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val saved = withContext(Dispatchers.IO) { context.writeTo(uri, content) }
                currentOnResult(if (saved) DocumentResult.Saved else DocumentResult.Failed)
            }
        }

    val openDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                currentOnResult(DocumentResult.Cancelled)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val content = withContext(Dispatchers.IO) { context.readText(uri) }
                currentOnResult(content?.let { DocumentResult.Opened(it) } ?: DocumentResult.Failed)
            }
        }

    return remember(context) {
        object : DocumentFiles {
            override fun save(
                fileName: String,
                mimeType: String,
                content: String,
            ) {
                pending.content = content
                createDocument.launch(TypedDocument(fileName, mimeType))
            }

            override fun share(
                fileName: String,
                mimeType: String,
                content: String,
            ) {
                scope.launch {
                    val uri = withContext(Dispatchers.IO) { context.writeShareFile(fileName, content) }
                    if (uri == null) {
                        currentOnResult(DocumentResult.Failed)
                        return@launch
                    }
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri(fileName, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(send, null))
                }
            }

            override fun open(mimeTypes: List<String>) {
                openDocument.launch(mimeTypes.toTypedArray())
            }
        }
    }
}

private class PendingContent {
    var content: String? = null

    fun take(): String? = content.also { content = null }
}

private data class TypedDocument(
    val fileName: String,
    val mimeType: String,
)

/** Like [ActivityResultContracts.CreateDocument], with the MIME type chosen per call. */
private class CreateTypedDocument : ActivityResultContract<TypedDocument, Uri?>() {
    override fun createIntent(
        context: Context,
        input: TypedDocument,
    ): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.fileName)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? = intent?.data.takeIf { resultCode == Activity.RESULT_OK }
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

/** Null when the file cannot be read or is too large to be one of ours. */
private fun Context.readText(uri: Uri): String? =
    try {
        contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_OPEN_BYTES) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray().decodeToString()
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

/** Keeps a single shared file in the cache, replacing the previous one. */
private fun Context.writeShareFile(
    fileName: String,
    content: String,
): Uri? =
    try {
        val directory = File(cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, fileName).apply { writeBytes(content.encodeToByteArray()) }
        FileProvider.getUriForFile(this, "$packageName$AUTHORITY_SUFFIX", file)
    } catch (e: IOException) {
        null
    }

/** Must match res/xml/shared_files_paths.xml and the provider in the androidMain manifest. */
private const val SHARE_DIRECTORY = "exports"
private const val AUTHORITY_SUFFIX = ".files"

/** Far above a backup of 100 000 records (about 25 MB), well below memory limits. */
private const val MAX_OPEN_BYTES = 64 * 1024 * 1024
