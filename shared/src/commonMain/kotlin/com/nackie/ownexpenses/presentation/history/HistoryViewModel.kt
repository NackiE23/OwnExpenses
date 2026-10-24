package com.nackie.ownexpenses.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.domain.PeriodSelection
import com.nackie.ownexpenses.domain.TodayProvider
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodTotals
import com.nackie.ownexpenses.domain.model.TransactionDetails
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class HistoryUiState(
    val period: Period,
    val today: LocalDate,
    val totals: PeriodTotals = PeriodTotals(),
    val days: List<DayGroup> = emptyList(),
    val isLoading: Boolean = true,
)

/** Records of one calendar day, newest first, with the day's net sum (incomes minus expenses). */
data class DayGroup(
    val date: LocalDate,
    val netMinor: Long,
    val items: List<TransactionDetails>,
)

/**
 * Operations tab: records of the shared period grouped by day. Editing and deleting go
 * through the entry panel's ViewModel, so undo works the same everywhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    transactionRepository: TransactionRepository,
    private val periodSelection: PeriodSelection,
    todayProvider: TodayProvider,
) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> =
        periodSelection.period
            .flatMapLatest { period ->
                combine(
                    transactionRepository.observeTransactionDetails(period),
                    transactionRepository.observePeriodTotals(period),
                    todayProvider.observeToday(),
                ) { details, totals, today ->
                    HistoryUiState(
                        period = period,
                        today = today,
                        totals = totals,
                        days = groupByDay(details),
                        isLoading = false,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = HistoryUiState(period = periodSelection.period.value, today = todayProvider.today()),
            )

    fun showPreviousPeriod() = periodSelection.shift(-1)

    fun showNextPeriod() = periodSelection.shift(1)

    fun selectPeriod(period: Period) = periodSelection.select(period)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Groups records that are already sorted by date (either direction), keeping their order. */
internal fun groupByDay(details: List<TransactionDetails>): List<DayGroup> {
    val groups = mutableListOf<DayGroup>()
    var currentDate: LocalDate? = null
    var currentItems = mutableListOf<TransactionDetails>()

    fun flush() {
        val date = currentDate ?: return
        groups += DayGroup(date, currentItems.sumOf { it.transaction.signedAmountMinor }, currentItems)
    }

    details.forEach { item ->
        if (item.transaction.date != currentDate) {
            flush()
            currentDate = item.transaction.date
            currentItems = mutableListOf()
        }
        currentItems += item
    }
    flush()
    return groups
}
