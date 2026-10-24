package com.nackie.ownexpenses.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.backup.BackupError
import com.nackie.ownexpenses.backup.BackupReadResult
import com.nackie.ownexpenses.backup.BackupService
import com.nackie.ownexpenses.backup.RestorableBackup
import com.nackie.ownexpenses.domain.PeriodSelection
import com.nackie.ownexpenses.domain.TodayProvider
import com.nackie.ownexpenses.domain.model.AppSettings
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.domain.repository.SettingsRepository
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import com.nackie.ownexpenses.export.CsvExporter
import com.nackie.ownexpenses.importing.SumlioCategoryNames
import com.nackie.ownexpenses.importing.SumlioImport
import com.nackie.ownexpenses.importing.SumlioImportError
import com.nackie.ownexpenses.importing.SumlioImportService
import com.nackie.ownexpenses.importing.SumlioImportSummary
import com.nackie.ownexpenses.importing.SumlioReadResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** The period selected on the other tabs; offered as the default export range. */
    val selectedPeriod: Period,
    val today: LocalDate,
    /** Export, backup or restore in progress. */
    val isBusy: Boolean = false,
    /** A valid backup waiting for the user to confirm that it replaces all data. */
    val pendingRestore: RestoreSummary? = null,
    /** A checked Sumlio file waiting for the user to confirm the import. */
    val pendingImport: SumlioImportSummary? = null,
)

data class RestoreSummary(
    val categoryCount: Int,
    val transactionCount: Int,
    val createdAt: Instant?,
)

/** Where a file goes: a folder picked by the user, or the system share sheet. */
enum class ExportTarget {
    SAVE,
    SHARE,
}

enum class FileKind {
    CSV_EXPORT,
    BACKUP,
}

/** A file the UI hands to the platform. */
data class OutgoingFile(
    val fileName: String,
    val mimeType: String,
    val content: String,
    val kind: FileKind,
    /** Records in the file, for the confirmation message. */
    val recordCount: Int,
)

sealed interface SettingsEvent {
    data class FileReady(
        val file: OutgoingFile,
        val target: ExportTarget,
    ) : SettingsEvent

    data class RestoreFailed(
        val error: BackupError,
    ) : SettingsEvent

    data class Restored(
        val transactionCount: Int,
    ) : SettingsEvent

    data class ImportFailed(
        val error: SumlioImportError,
        val line: Int? = null,
    ) : SettingsEvent

    data class Imported(
        val transactionCount: Int,
    ) : SettingsEvent
}

/** "More" tab: theme, CSV delimiter, (fixed) currency, CSV export, backup and restore, Sumlio import. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val backupService: BackupService,
    private val sumlioImportService: SumlioImportService,
    periodSelection: PeriodSelection,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val pendingRestore = MutableStateFlow<RestorableBackup?>(null)
    private val pendingImport = MutableStateFlow<SumlioImport?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepository.settings,
            periodSelection.period,
            busy,
            pendingRestore,
            pendingImport,
        ) { settings, period, isBusy, pending, sumlio ->
            SettingsUiState(
                settings = settings,
                selectedPeriod = period,
                today = todayProvider.today(),
                isBusy = isBusy,
                pendingRestore = pending?.let { RestoreSummary(it.categoryCount, it.transactionCount, it.createdAt) },
                pendingImport = sumlio?.summary,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue =
                SettingsUiState(
                    selectedPeriod = periodSelection.period.value,
                    today = todayProvider.today(),
                ),
        )

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setCsvDelimiter(delimiter: CsvDelimiter) {
        viewModelScope.launch { settingsRepository.setCsvDelimiter(delimiter) }
    }

    fun export(
        period: Period,
        target: ExportTarget,
    ) = runBusy {
        val settings = settingsRepository.settings.first()
        val rows = transactionRepository.getTransactionDetailsChronologically(period)
        val file =
            OutgoingFile(
                fileName = CsvExporter.fileName(period),
                mimeType = CsvExporter.MIME_TYPE,
                content = CsvExporter.export(rows, settings.csvDelimiter, settings.currencyCode),
                kind = FileKind.CSV_EXPORT,
                recordCount = rows.size,
            )
        eventChannel.send(SettingsEvent.FileReady(file, target))
    }

    fun createBackup(target: ExportTarget) =
        runBusy {
            val backup = backupService.createBackup()
            val file =
                OutgoingFile(
                    fileName = backup.fileName,
                    mimeType = BackupService.MIME_TYPE,
                    content = backup.content,
                    kind = FileKind.BACKUP,
                    recordCount = backup.transactionCount,
                )
            eventChannel.send(SettingsEvent.FileReady(file, target))
        }

    /** Checks a picked file; a valid backup waits in [SettingsUiState.pendingRestore] for confirmation. */
    fun openBackup(content: String) {
        when (val result = backupService.read(content)) {
            is BackupReadResult.Valid -> {
                pendingRestore.value = result.backup
            }

            is BackupReadResult.Invalid -> {
                viewModelScope.launch { eventChannel.send(SettingsEvent.RestoreFailed(result.error)) }
            }
        }
    }

    /** Asks first, like the Sumlio import: restoring cannot be undone. */
    fun confirmRestore() {
        val backup = pendingRestore.value ?: return
        pendingRestore.value = null
        runBusy {
            val event =
                if (backupService.restore(backup)) {
                    SettingsEvent.Restored(backup.transactionCount)
                } else {
                    SettingsEvent.RestoreFailed(BackupError.RESTORE_FAILED)
                }
            eventChannel.send(event)
        }
    }

    fun cancelRestore() {
        pendingRestore.value = null
    }

    /** Checks a picked Sumlio CSV; a valid one waits in [SettingsUiState.pendingImport] for confirmation. */
    fun openSumlioFile(
        content: String,
        names: SumlioCategoryNames,
    ) = runBusy {
        val currency = settingsRepository.settings.first().currencyCode
        when (val result = sumlioImportService.read(content, currency, names)) {
            is SumlioReadResult.Ready -> pendingImport.value = result.file
            is SumlioReadResult.Failed -> eventChannel.send(SettingsEvent.ImportFailed(result.error, result.line))
        }
    }

    /** Adds the records to the existing data; the summary dialog was the confirmation. */
    fun confirmImport() {
        val sumlio = pendingImport.value ?: return
        pendingImport.value = null
        runBusy {
            val event =
                if (sumlioImportService.import(sumlio)) {
                    SettingsEvent.Imported(sumlio.summary.recordCount)
                } else {
                    SettingsEvent.ImportFailed(SumlioImportError.IMPORT_FAILED)
                }
            eventChannel.send(event)
        }
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
