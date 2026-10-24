package com.nackie.ownexpenses.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.domain.PeriodSelection
import com.nackie.ownexpenses.domain.TodayProvider
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodTotals
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.domain.repository.CategoryRepository
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

data class HomeUiState(
    val balanceMinor: Long = 0,
    val period: Period,
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val totals: PeriodTotals = PeriodTotals(),
    val tiles: List<CategoryTile> = emptyList(),
    val isLoading: Boolean = true,
)

/** Main-screen tile: a top-level category and its period total including subcategories. */
data class CategoryTile(
    val category: CategoryWithSubcategories,
    val totalMinor: Long,
)

/** Main screen: balance (all time), period totals and category tiles of the selected type. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val periodSelection: PeriodSelection,
    todayProvider: TodayProvider,
) : ViewModel() {
    private val selectedType = MutableStateFlow(TransactionType.EXPENSE)

    private val today: Flow<LocalDate> =
        todayProvider.observeToday().shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    private val balance: Flow<Long> = today.flatMapLatest(transactionRepository::observeBalance)

    private val totals: Flow<PeriodTotals> =
        periodSelection.period.flatMapLatest(
            transactionRepository::observePeriodTotals,
        )

    private val tiles: Flow<List<CategoryTile>> =
        combine(selectedType, periodSelection.period, today, ::TileQuery).flatMapLatest { query ->
            combine(
                categoryRepository.observeCategories(query.type),
                transactionRepository.observeCategoryTotals(query.type, query.period),
                transactionRepository.observeCategoryUsage(usageWindow(query.today)),
                ::buildTiles,
            )
        }

    val uiState: StateFlow<HomeUiState> =
        combine(balance, periodSelection.period, selectedType, totals, tiles) { balance, period, type, totals, tiles ->
            HomeUiState(
                balanceMinor = balance,
                period = period,
                selectedType = type,
                totals = totals,
                tiles = tiles,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HomeUiState(period = periodSelection.period.value),
        )

    fun selectType(type: TransactionType) {
        selectedType.value = type
    }

    fun showPreviousPeriod() = periodSelection.shift(-1)

    fun showNextPeriod() = periodSelection.shift(1)

    private data class TileQuery(
        val type: TransactionType,
        val period: Period,
        val today: LocalDate,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Tile order is based on how often each category was used in the last [USAGE_WINDOW_DAYS] days. */
internal const val USAGE_WINDOW_DAYS = 30

internal fun usageWindow(today: LocalDate): Period =
    Period.Custom(today.minus(USAGE_WINDOW_DAYS - 1, DateTimeUnit.DAY), today)

/**
 * Most used categories first; ties (including never used ones) keep the user's own order.
 * [categories] arrive already in that order.
 */
internal fun buildTiles(
    categories: List<CategoryWithSubcategories>,
    totals: Map<Long, Long>,
    usage: Map<Long, Long>,
): List<CategoryTile> =
    categories
        .sortedByDescending { usage[it.category.id] ?: 0 }
        .map { CategoryTile(it, totals[it.category.id] ?: 0) }
