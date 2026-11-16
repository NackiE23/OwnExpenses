package com.nackie.ownexpenses.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nackie.ownexpenses.sync.SyncError
import com.nackie.ownexpenses.sync.SyncStatus
import com.nackie.ownexpenses.ui.format.dayMonthYear
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.dialog_cancel
import com.nackie.ownexpenses.ui.resources.sync_check
import com.nackie.ownexpenses.ui.resources.sync_check_hint
import com.nackie.ownexpenses.ui.resources.sync_error_no_connection
import com.nackie.ownexpenses.ui.resources.sync_error_rejected
import com.nackie.ownexpenses.ui.resources.sync_error_server
import com.nackie.ownexpenses.ui.resources.sync_error_unauthorized
import com.nackie.ownexpenses.ui.resources.sync_now
import com.nackie.ownexpenses.ui.resources.sync_server
import com.nackie.ownexpenses.ui.resources.sync_status_last
import com.nackie.ownexpenses.ui.resources.sync_status_never
import com.nackie.ownexpenses.ui.resources.sync_status_pending
import com.nackie.ownexpenses.ui.resources.sync_status_running
import com.nackie.ownexpenses.ui.resources.sync_token
import com.nackie.ownexpenses.ui.resources.sync_token_label
import com.nackie.ownexpenses.ui.resources.sync_token_missing
import com.nackie.ownexpenses.ui.resources.sync_token_remove
import com.nackie.ownexpenses.ui.resources.sync_token_save
import com.nackie.ownexpenses.ui.resources.sync_token_set
import com.nackie.ownexpenses.ui.resources.sync_token_text
import com.nackie.ownexpenses.ui.resources.sync_token_title
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** Device token, status of the last sync and the two manual actions. Shown only in builds with a server. */
@Composable
internal fun SyncSection(
    status: SyncStatus,
    onTokenClick: () -> Unit,
    onSyncClick: () -> Unit,
    onCheckClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.sync_token)) },
            supportingContent = {
                Text(stringResource(if (status.hasToken) Res.string.sync_token_set else Res.string.sync_token_missing))
            },
            modifier = Modifier.clickable(onClick = onTokenClick),
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.sync_now)) },
            supportingContent = { SyncStatusText(status) },
            modifier = Modifier.clickable(enabled = status.hasToken && !status.isRunning, onClick = onSyncClick),
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.sync_check)) },
            supportingContent = {
                Column {
                    Text(stringResource(Res.string.sync_check_hint))
                    Text(stringResource(Res.string.sync_server, status.serverUrl))
                }
            },
            modifier = Modifier.clickable(enabled = status.hasToken, onClick = onCheckClick),
        )
    }
}

@Composable
private fun SyncStatusText(
    status: SyncStatus,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (status.isRunning) {
            Text(stringResource(Res.string.sync_status_running))
        } else {
            val lastSuccess = status.lastSuccessAt
            Text(
                if (lastSuccess == null) {
                    stringResource(Res.string.sync_status_never)
                } else {
                    stringResource(Res.string.sync_status_last, dateTimeLabel(lastSuccess))
                },
            )
        }
        if (status.pendingCount > 0) {
            Text(stringResource(Res.string.sync_status_pending, status.pendingCount.toString()))
        }
        status.lastError?.let { error ->
            val details = status.lastErrorMessage?.takeIf { error == SyncError.REJECTED }
            Text(
                text = listOfNotNull(stringResource(error.message), details).joinToString(": "),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun SyncTokenDialog(
    hasToken: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var token by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(Res.string.sync_token_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.sync_token_text))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text(stringResource(Res.string.sync_token_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(token) }, enabled = token.isNotBlank()) {
                Text(stringResource(Res.string.sync_token_save))
            }
        },
        dismissButton = {
            if (hasToken) {
                TextButton(onClick = onRemove) { Text(stringResource(Res.string.sync_token_remove)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
            }
        },
    )
}

internal val SyncError.message: StringResource
    get() =
        when (this) {
            SyncError.NO_CONNECTION -> Res.string.sync_error_no_connection
            SyncError.UNAUTHORIZED -> Res.string.sync_error_unauthorized
            SyncError.REJECTED -> Res.string.sync_error_rejected
            SyncError.SERVER -> Res.string.sync_error_server
        }

/** `17.11.2026 14:05` in the phone's time zone. */
@Composable
private fun dateTimeLabel(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    return "${dayMonthYear(local.date)} $time"
}
