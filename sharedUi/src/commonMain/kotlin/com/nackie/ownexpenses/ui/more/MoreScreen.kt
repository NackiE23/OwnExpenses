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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.presentation.settings.ExportTarget
import com.nackie.ownexpenses.presentation.settings.SettingsEvent
import com.nackie.ownexpenses.presentation.settings.SettingsUiState
import com.nackie.ownexpenses.presentation.settings.SettingsViewModel
import com.nackie.ownexpenses.ui.format.periodLabel
import com.nackie.ownexpenses.ui.period.DateRangePickerDialog
import com.nackie.ownexpenses.ui.period.RadioRow
import com.nackie.ownexpenses.ui.resources.Res
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
import com.nackie.ownexpenses.ui.resources.period_kind_custom
import com.nackie.ownexpenses.ui.resources.settings_appearance
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** "More" tab: theme, CSV delimiter, currency (fixed), language note and CSV export. */
@Composable
internal fun MoreScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var savedRecords by remember { mutableIntStateOf(0) }
    var showExport by rememberSaveable { mutableStateOf(false) }

    val fileHandler =
        rememberCsvFileHandler { result ->
            scope.launch {
                when (result) {
                    CsvFileResult.SAVED -> {
                        snackbarHostState.showSnackbar(getString(Res.string.export_saved, savedRecords.toString()))
                    }

                    CsvFileResult.FAILED -> {
                        snackbarHostState.showSnackbar(getString(Res.string.export_failed))
                    }

                    CsvFileResult.CANCELLED -> {}
                }
            }
        }

    LaunchedEffect(viewModel, fileHandler) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ExportReady -> {
                    savedRecords = event.recordCount
                    when (event.target) {
                        ExportTarget.SAVE -> fileHandler.save(event.fileName, event.content)
                        ExportTarget.SHARE -> fileHandler.share(event.fileName, event.content)
                    }
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
            onExportClick = { showExport = true },
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (showExport) {
        ExportDialog(
            state = state,
            onExport = { period, target ->
                showExport = false
                viewModel.export(period, target)
            },
            onDismiss = { showExport = false },
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onThemeChange: (ThemeMode) -> Unit,
    onDelimiterChange: (CsvDelimiter) -> Unit,
    onExportClick: () -> Unit,
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
        ListItem(
            headlineContent = { Text(stringResource(Res.string.export_csv)) },
            supportingContent = { Text(stringResource(Res.string.export_csv_hint)) },
            modifier = Modifier.clickable(enabled = !state.isExporting, onClick = onExportClick),
        )
    }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.export_csv)) },
        text = {
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
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onExport(period, ExportTarget.SHARE) }) {
                    Text(stringResource(Res.string.export_share))
                }
                TextButton(onClick = { onExport(period, ExportTarget.SAVE) }) {
                    Text(stringResource(Res.string.export_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
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
