package com.nackie.ownexpenses.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.domain.PeriodSelection
import com.nackie.ownexpenses.domain.TodayProvider
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.domain.repository.CategoryRepository
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class StatisticsUiState(
    val period: Period,
    val today: LocalDate,
    val totalMinor: Long = 0,
    val chart: SpendingChart = SpendingChart(BarGranularity.DAY, emptyList()),
    val shares: List<CategoryShare> = emptyList(),
    val isLoading: Boolean = true,
)

/** Statistics tab: expenses of the shared period by day (or month) and by category. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    private val periodSelection: PeriodSelection,
    todayProvider: TodayProvider,
) : ViewModel() {
    val uiState: StateFlow<StatisticsUiState> =
        periodSelection.period
            .flatMapLatest { period ->
                combine(
                    transactionRepository.observeDailyTotals(TransactionType.EXPENSE, period),
                    transactionRepository.observeTotalsPerCategory(TransactionType.EXPENSE, period),
                    categoryRepository.observeCategories(TransactionType.EXPENSE, includeArchived = true),
                    todayProvider.observeToday(),
                ) { daily, perCategory, categories, today ->
                    StatisticsUiState(
                        period = period,
                        today = today,
                        totalMinor = perCategory.values.sum(),
                        chart = buildSpendingChart(period, daily),
                        shares = buildCategoryShares(categories, perCategory),
                        isLoading = false,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = StatisticsUiState(period = periodSelection.period.value, today = todayProvider.today()),
            )

    fun showPreviousPeriod() = periodSelection.shift(-1)

    fun showNextPeriod() = periodSelection.shift(1)

    fun selectPeriod(period: Period) = periodSelection.select(period)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
