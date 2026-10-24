package com.nackie.ownexpenses.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.domain.PeriodSelection
import com.nackie.ownexpenses.domain.TodayProvider
import com.nackie.ownexpenses.domain.model.AppSettings
import com.nackie.ownexpenses.domain.model.CsvDelimiter
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.domain.repository.SettingsRepository
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import com.nackie.ownexpenses.export.CsvExporter
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

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** The period selected on the other tabs; offered as the default export range. */
    val selectedPeriod: Period,
    val today: LocalDate,
    val isExporting: Boolean = false,
)

/** Where the exported file goes: a folder picked by the user, or the system share sheet. */
enum class ExportTarget {
    SAVE,
    SHARE,
}

sealed interface SettingsEvent {
    /** The UI hands [content] to the platform as a file named [fileName]. */
    data class ExportReady(
        val fileName: String,
        val content: String,
        val recordCount: Int,
        val target: ExportTarget,
    ) : SettingsEvent
}

/** "More" tab: theme, CSV delimiter, (fixed) currency and CSV export. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    periodSelection: PeriodSelection,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val exporting = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, periodSelection.period, exporting) { settings, period, isExporting ->
            SettingsUiState(settings, period, todayProvider.today(), isExporting)
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
    ) {
        if (exporting.value) return
        exporting.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                val rows = transactionRepository.getTransactionDetailsChronologically(period)
                val content = CsvExporter.export(rows, settings.csvDelimiter, settings.currencyCode)
                eventChannel.send(SettingsEvent.ExportReady(CsvExporter.fileName(period), content, rows.size, target))
            } finally {
                exporting.value = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
