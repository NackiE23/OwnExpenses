package com.nackie.ownexpenses.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nackie.ownexpenses.backup.BackupError
import com.nackie.ownexpenses.backup.BackupService
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.importing.SumlioCategoryNames
import com.nackie.ownexpenses.importing.SumlioImportError
import com.nackie.ownexpenses.importing.SumlioImportSummary
import com.nackie.ownexpenses.presentation.settings.ExportTarget
import com.nackie.ownexpenses.presentation.settings.FileKind
import com.nackie.ownexpenses.presentation.settings.RestoreSummary
import com.nackie.ownexpenses.presentation.settings.SettingsEvent
import com.nackie.ownexpenses.presentation.settings.SettingsUiState
import com.nackie.ownexpenses.presentation.settings.SettingsViewModel
import com.nackie.ownexpenses.ui.format.dayMonthYear
import com.nackie.ownexpenses.ui.format.periodLabel
import com.nackie.ownexpenses.ui.format.rememberMoneyFormatter
import com.nackie.ownexpenses.ui.period.DateRangePickerDialog
import com.nackie.ownexpenses.ui.period.RadioRow
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.backup_create
import com.nackie.ownexpenses.ui.resources.backup_create_hint
import com.nackie.ownexpenses.ui.resources.backup_restore
import com.nackie.ownexpenses.ui.resources.backup_restore_hint
import com.nackie.ownexpenses.ui.resources.backup_saved
import com.nackie.ownexpenses.ui.resources.backup_section
import com.nackie.ownexpenses.ui.resources.category_other_expense
import com.nackie.ownexpenses.ui.resources.csv_delimiter_comma
import com.nackie.ownexpenses.ui.resources.csv_delimiter_semicolon
import com.nackie.ownexpenses.ui.resources.dialog_cancel
import com.nackie.ownexpenses.ui.resources.export_csv
import com.nackie.ownexpenses.ui.resources.export_csv_hint
import com.nackie.ownexpenses.ui.resources.export_failed
import com.nackie.ownexpenses.ui.resources.export_period_all
import com.nackie.ownexpenses.ui.resources.export_period_custom
import com.nackie.ownexpenses.ui.resources.export_period_selected
import com.nackie.ownexpenses.ui.resources.export_save
import com.nackie.ownexpenses.ui.resources.export_saved
import com.nackie.ownexpenses.ui.resources.export_share
import com.nackie.ownexpenses.ui.resources.import_confirm
import com.nackie.ownexpenses.ui.resources.import_confirm_backup
import com.nackie.ownexpenses.ui.resources.import_confirm_duplicates
import com.nackie.ownexpenses.ui.resources.import_confirm_reversed
import com.nackie.ownexpenses.ui.resources.import_confirm_skipped
import com.nackie.ownexpenses.ui.resources.import_confirm_text
import com.nackie.ownexpenses.ui.resources.import_confirm_title
import com.nackie.ownexpenses.ui.resources.import_error_not_sumlio
import com.nackie.ownexpenses.ui.resources.import_error_nothing
import com.nackie.ownexpenses.ui.resources.import_error_row
import com.nackie.ownexpenses.ui.resources.import_error_write
import com.nackie.ownexpenses.ui.resources.import_section
import com.nackie.ownexpenses.ui.resources.import_sumlio
import com.nackie.ownexpenses.ui.resources.import_sumlio_hint
import com.nackie.ownexpenses.ui.resources.import_sumlio_refunds
import com.nackie.ownexpenses.ui.resources.imported
import com.nackie.ownexpenses.ui.resources.open_failed
import com.nackie.ownexpenses.ui.resources.period_kind_custom
import com.nackie.ownexpenses.ui.resources.restore_confirm
import com.nackie.ownexpenses.ui.resources.restore_confirm_text
import com.nackie.ownexpenses.ui.resources.restore_confirm_title
import com.nackie.ownexpenses.ui.resources.restore_error_invalid
import com.nackie.ownexpenses.ui.resources.restore_error_newer
import com.nackie.ownexpenses.ui.resources.restore_error_not_backup
import com.nackie.ownexpenses.ui.resources.restore_error_write
import com.nackie.ownexpenses.ui.resources.restore_unknown_date
import com.nackie.ownexpenses.ui.resources.restored
import com.nackie.ownexpenses.ui.resources.settings_appearance
import com.nackie.ownexpenses.ui.resources.settings_categories
import com.nackie.ownexpenses.ui.resources.settings_categories_hint
import com.nackie.ownexpenses.ui.resources.settings_csv_delimiter
import com.nackie.ownexpenses.ui.resources.settings_csv_delimiter_hint
import com.nackie.ownexpenses.ui.resources.settings_currency
import com.nackie.ownexpenses.ui.resources.settings_currency_value
import com.nackie.ownexpenses.ui.resources.settings_data
import com.nackie.ownexpenses.ui.resources.settings_language
import com.nackie.ownexpenses.ui.resources.settings_language_hint
import com.nackie.ownexpenses.ui.resources.settings_theme
import com.nackie.ownexpenses.ui.resources.theme_dark
import com.nackie.ownexpenses.ui.resources.theme_light
import com.nackie.ownexpenses.ui.resources.theme_system
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** "More" tab: theme, CSV delimiter, currency (fixed), language note, CSV export, backups and Sumlio import. */
@Composable
internal fun MoreScreen(
    onManageCategories: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // What the next "saved" message is about; the platform picker only reports success.
    var lastFile by remember { mutableStateOf<Pair<FileKind, Int>?>(null) }
    var dialog by rememberSaveable { mutableStateOf(MoreDialog.NONE) }
    // Which file the pending picker result is (backup or Sumlio CSV); null while saving.
    var opening by remember { mutableStateOf<OpenPurpose?>(null) }
    val sumlioNames =
        SumlioCategoryNames(
            refunds = stringResource(Res.string.import_sumlio_refunds),
            other = stringResource(Res.string.category_other_expense),
        )

    fun showMessage(
        resource: StringResource,
        vararg args: Any,
    ) {
        scope.launch { snackbarHostState.showSnackbar(getString(resource, *args)) }
    }

    val files =
        rememberDocumentFiles { result ->
            val purpose = opening
            opening = null
            when (result) {
                DocumentResult.Saved -> {
                    lastFile?.let { (kind, count) ->
                        showMessage(
                            if (kind ==
                                FileKind.BACKUP
                            ) {
                                Res.string.backup_saved
                            } else {
                                Res.string.export_saved
                            },
                            count.toString(),
                        )
                    }
                }

                DocumentResult.Failed -> {
                    showMessage(if (purpose != null) Res.string.open_failed else Res.string.export_failed)
                }

                is DocumentResult.Opened -> {
                    when (purpose) {
                        OpenPurpose.BACKUP -> {
                            viewModel.openBackup(result.content)
                        }

                        OpenPurpose.SUMLIO -> {
                            viewModel.openSumlioFile(result.content, sumlioNames)
                        }

                        null -> {}
                    }
                }

                DocumentResult.Cancelled -> {}
            }
        }

    LaunchedEffect(viewModel, files) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.FileReady -> {
                    val file = event.file
                    lastFile = file.kind to file.recordCount
                    when (event.target) {
                        ExportTarget.SAVE -> files.save(file.fileName, file.mimeType, file.content)
                        ExportTarget.SHARE -> files.share(file.fileName, file.mimeType, file.content)
                    }
                }

                is SettingsEvent.RestoreFailed -> {
                    showMessage(event.error.message)
                }

                is SettingsEvent.Restored -> {
                    showMessage(Res.string.restored, event.transactionCount.toString())
                }

                is SettingsEvent.ImportFailed -> {
                    if (event.line != null) {
                        showMessage(Res.string.import_error_row, event.line.toString())
                    } else {
                        showMessage(event.error.message)
                    }
                }

                is SettingsEvent.Imported -> {
                    showMessage(Res.string.imported, event.transactionCount.toString())
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        SettingsContent(
            state = state,
            onThemeChange = viewModel::setThemeMode,
            onDelimiterChange = viewModel::setCsvDelimiter,
            onManageCategories = onManageCategories,
            onExportClick = { dialog = MoreDialog.EXPORT },
            onBackupClick = { dialog = MoreDialog.BACKUP },
            onRestoreClick = {
                opening = OpenPurpose.BACKUP
                files.open(BACKUP_MIME_TYPES)
            },
            onImportSumlioClick = {
                opening = OpenPurpose.SUMLIO
                files.open(CSV_MIME_TYPES)
            },
            modifier = Modifier.padding(innerPadding),
        )
    }

    when (dialog) {
        MoreDialog.EXPORT -> {
            ExportDialog(
                state = state,
                onExport = { period, target ->
                    dialog = MoreDialog.NONE
                    viewModel.export(period, target)
                },
                onDismiss = { dialog = MoreDialog.NONE },
            )
        }

        MoreDialog.BACKUP -> {
            TargetDialog(
                title = stringResource(Res.string.backup_create),
                onTarget = { target ->
                    dialog = MoreDialog.NONE
                    viewModel.createBackup(target)
                },
                onDismiss = { dialog = MoreDialog.NONE },
            )
        }

        MoreDialog.NONE -> {}
    }

    state.pendingRestore?.let { summary ->
        RestoreConfirmDialog(
            summary = summary,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore,
        )
    }

    state.pendingImport?.let { summary ->
        ImportConfirmDialog(
            summary = summary,
            names = sumlioNames,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport,
        )
    }
}

private enum class MoreDialog {
    NONE,
    EXPORT,
    BACKUP,
}

private enum class OpenPurpose {
    BACKUP,
    SUMLIO,
}

/** Files saved by other apps or downloaded may not be labelled as JSON; content is validated anyway. */
private val BACKUP_MIME_TYPES = listOf(BackupService.MIME_TYPE, "application/octet-stream", "text/plain")

/** CSV is labelled differently by browsers, file managers and cloud apps. */
private val CSV_MIME_TYPES =
    listOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/octet-stream")

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onThemeChange: (ThemeMode) -> Unit,
    onDelimiterChange: (CsvDelimiter) -> Unit,
    onManageCategories: () -> Unit,
    onExportClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onImportSumlioClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        SectionTitle(stringResource(Res.string.settings_appearance))
        SettingLabel(stringResource(Res.string.settings_theme))
        ChoiceRow(
            options = ThemeMode.entries,
            selected = state.settings.themeMode,
            label = { it.label },
            onSelect = onThemeChange,
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.settings_language)) },
            supportingContent = { Text(stringResource(Res.string.settings_language_hint)) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle(stringResource(Res.string.settings_data))
        ActionItem(
            Res.string.settings_categories,
            Res.string.settings_categories_hint,
            enabled = true,
            onClick = onManageCategories,
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.settings_currency)) },
            supportingContent = { Text(stringResource(Res.string.settings_currency_value)) },
        )
        SettingLabel(stringResource(Res.string.settings_csv_delimiter))
        ChoiceRow(
            options = CsvDelimiter.entries,
            selected = state.settings.csvDelimiter,
            label = { it.label },
            onSelect = onDelimiterChange,
        )
        Text(
            text = stringResource(Res.string.settings_csv_delimiter_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ActionItem(Res.string.export_csv, Res.string.export_csv_hint, enabled = !state.isBusy, onClick = onExportClick)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle(stringResource(Res.string.backup_section))
        ActionItem(
            Res.string.backup_create,
            Res.string.backup_create_hint,
            enabled = !state.isBusy,
            onClick = onBackupClick,
        )
        ActionItem(
            Res.string.backup_restore,
            Res.string.backup_restore_hint,
            enabled = !state.isBusy,
            onClick = onRestoreClick,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle(stringResource(Res.string.import_section))
        ActionItem(
            Res.string.import_sumlio,
            Res.string.import_sumlio_hint,
            enabled = !state.isBusy,
            onClick = onImportSumlioClick,
        )
    }
}

@Composable
private fun ActionItem(
    title: StringResource,
    hint: StringResource,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(hint)) },
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> StringResource,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(label(option)), maxLines = 1)
            }
        }
    }
}

/** Range choice (selected period, all time or custom dates) and where the file goes. */
@Composable
private fun ExportDialog(
    state: SettingsUiState,
    onExport: (Period, ExportTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    var period by remember { mutableStateOf(state.selectedPeriod) }
    var pickRange by remember { mutableStateOf(false) }
    val custom = period as? Period.Custom

    if (pickRange) {
        DateRangePickerDialog(
            initial = custom,
            onConfirm = {
                period = it
                pickRange = false
            },
            onDismiss = { pickRange = false },
        )
        return
    }

    TargetDialog(
        title = stringResource(Res.string.export_csv),
        onTarget = { onExport(period, it) },
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            if (state.selectedPeriod != Period.AllTime) {
                RadioRow(
                    label = stringResource(Res.string.export_period_selected, periodLabel(state.selectedPeriod)),
                    selected = period == state.selectedPeriod,
                    onClick = { period = state.selectedPeriod },
                )
            }
            RadioRow(
                label = stringResource(Res.string.export_period_all),
                selected = period == Period.AllTime,
                onClick = { period = Period.AllTime },
            )
            RadioRow(
                label =
                    if (custom != null && custom != state.selectedPeriod) {
                        stringResource(Res.string.export_period_custom, periodLabel(custom))
                    } else {
                        stringResource(Res.string.period_kind_custom)
                    },
                selected = custom != null && custom != state.selectedPeriod,
                onClick = { pickRange = true },
            )
        }
    }
}

/** Save to a folder or share; the optional [content] chooses what to export. */
@Composable
private fun TargetDialog(
    title: String,
    onTarget: (ExportTarget) -> Unit,
    onDismiss: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = content,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onTarget(ExportTarget.SHARE) }) { Text(stringResource(Res.string.export_share)) }
                TextButton(onClick = { onTarget(ExportTarget.SAVE) }) { Text(stringResource(Res.string.export_save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

/** Restoring cannot be undone, so it asks first. */
@Composable
private fun RestoreConfirmDialog(
    summary: RestoreSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val date =
        summary.createdAt
            ?.let { dayMonthYear(it.toLocalDateTime(TimeZone.currentSystemDefault()).date) }
            ?: stringResource(Res.string.restore_unknown_date)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.restore_confirm_title)) },
        text = {
            Text(
                stringResource(
                    Res.string.restore_confirm_text,
                    date,
                    summary.categoryCount.toString(),
                    summary.transactionCount.toString(),
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.restore_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

/** What the Sumlio import adds; nothing is written until the user confirms. */
@Composable
private fun ImportConfirmDialog(
    summary: SumlioImportSummary,
    names: SumlioCategoryNames,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = rememberMoneyFormatter()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.import_confirm_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(
                        Res.string.import_confirm_text,
                        summary.recordCount.toString(),
                        dayMonthYear(summary.firstDate),
                        dayMonthYear(summary.lastDate),
                        summary.newCategoryCount.toString(),
                        formatter.formatSigned(summary.balanceChangeMinor),
                    ),
                )
                if (summary.reversedCount > 0) {
                    Text(
                        stringResource(
                            Res.string.import_confirm_reversed,
                            summary.reversedCount.toString(),
                            names.refunds,
                            names.other,
                        ),
                    )
                }
                if (summary.skippedCount > 0) {
                    Text(stringResource(Res.string.import_confirm_skipped, summary.skippedCount.toString()))
                }
                if (summary.duplicateCount > 0) {
                    Text(stringResource(Res.string.import_confirm_duplicates, summary.duplicateCount.toString()))
                }
                Text(
                    text = stringResource(Res.string.import_confirm_backup),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.import_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

private val SumlioImportError.message: StringResource
    get() =
        when (this) {
            SumlioImportError.NOT_SUMLIO -> Res.string.import_error_not_sumlio
            SumlioImportError.INVALID_ROW -> Res.string.import_error_not_sumlio
            SumlioImportError.NOTHING_TO_IMPORT -> Res.string.import_error_nothing
            SumlioImportError.IMPORT_FAILED -> Res.string.import_error_write
        }

private val BackupError.message: StringResource
    get() =
        when (this) {
            BackupError.NOT_A_BACKUP -> Res.string.restore_error_not_backup
            BackupError.NEWER_VERSION -> Res.string.restore_error_newer
            BackupError.INVALID_DATA -> Res.string.restore_error_invalid
            BackupError.RESTORE_FAILED -> Res.string.restore_error_write
        }

private val ThemeMode.label: StringResource
    get() =
        when (this) {
            ThemeMode.SYSTEM -> Res.string.theme_system
            ThemeMode.LIGHT -> Res.string.theme_light
            ThemeMode.DARK -> Res.string.theme_dark
        }

private val CsvDelimiter.label: StringResource
    get() =
        when (this) {
            CsvDelimiter.SEMICOLON -> Res.string.csv_delimiter_semicolon
            CsvDelimiter.COMMA -> Res.string.csv_delimiter_comma
        }
