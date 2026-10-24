package com.nackie.ownexpenses.ui.quickentry

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.presentation.quickentry.QuickEntryEvent
import com.nackie.ownexpenses.ui.UiTestTags
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.snackbar_expense_added
import com.nackie.ownexpenses.ui.resources.snackbar_income_added
import com.nackie.ownexpenses.ui.resources.snackbar_save_failed
import com.nackie.ownexpenses.ui.resources.snackbar_undo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Turns quick-entry events into snackbars. "Added" carries an undo action instead of any
 * confirmation dialog; a newer snackbar replaces the current one.
 */
@Composable
internal fun QuickEntryEventsEffect(
    events: Flow<QuickEntryEvent>,
    snackbarHostState: SnackbarHostState,
    onUndo: (transactionId: Long) -> Unit,
) {
    val expenseAdded = stringResource(Res.string.snackbar_expense_added)
    val incomeAdded = stringResource(Res.string.snackbar_income_added)
    val saveFailed = stringResource(Res.string.snackbar_save_failed)
    val undo = stringResource(Res.string.snackbar_undo)
    val currentOnUndo by rememberUpdatedState(onUndo)

    LaunchedEffect(events, snackbarHostState) {
        events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            launch {
                when (event) {
                    is QuickEntryEvent.Added -> {
                        val message = if (event.type == TransactionType.EXPENSE) expenseAdded else incomeAdded
                        val result =
                            snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = undo,
                                duration = SnackbarDuration.Long,
                            )
                        if (result == SnackbarResult.ActionPerformed) currentOnUndo(event.transactionId)
                    }

                    QuickEntryEvent.SaveFailed -> {
                        snackbarHostState.showSnackbar(saveFailed)
                    }
                }
            }
        }
    }
}

/** Snackbar host whose action button has a test tag, so UI tests can undo in any language. */
@Composable
internal fun UndoSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        val actionLabel = data.visuals.actionLabel
        Snackbar(
            action =
                actionLabel?.let { label ->
                    {
                        TextButton(
                            onClick = data::performAction,
                            colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                            modifier = Modifier.testTag(UiTestTags.UNDO),
                        ) { Text(label) }
                    }
                },
        ) {
            Text(data.visuals.message)
        }
    }
}
