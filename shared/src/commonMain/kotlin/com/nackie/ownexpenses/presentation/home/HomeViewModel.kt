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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class HomeUiState(
    val balanceMinor: Long = 0,
    val period: Period,
    /** Used by the period picker; changes at midnight. */
    val today: LocalDate,
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
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val selectedType = MutableStateFlow(TransactionType.EXPENSE)

    private val today: Flow<LocalDate> =
        todayProvider.observeToday().shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    /** Balance as of today, paired with that day so both change together at midnight. */
    private val balance: Flow<Pair<LocalDate, Long>> =
        today.flatMapLatest { day -> transactionRepository.observeBalance(day).map { day to it } }

    private val totals: Flow<PeriodTotals> =
        periodSelection.period.flatMapLatest(
            transactionRepository::observePeriodTotals,
        )

    private val tiles: Flow<List<CategoryTile>> =
        combine(selectedType, periodSelection.period, ::TileQuery).flatMapLatest { query ->
            combine(
                categoryRepository.observeCategories(query.type),
                transactionRepository.observeCategoryTotals(query.type, query.period),
                ::buildTiles,
            )
        }

    val uiState: StateFlow<HomeUiState> =
        combine(balance, periodSelection.period, selectedType, totals, tiles) { balance, period, type, totals, tiles ->
            HomeUiState(
                balanceMinor = balance.second,
                period = period,
                today = balance.first,
                selectedType = type,
                totals = totals,
                tiles = tiles,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HomeUiState(period = periodSelection.period.value, today = todayProvider.today()),
        )

    fun selectType(type: TransactionType) {
        selectedType.value = type
    }

    fun showPreviousPeriod() = periodSelection.shift(-1)

    fun showNextPeriod() = periodSelection.shift(1)

    fun selectPeriod(period: Period) = periodSelection.select(period)

    private data class TileQuery(
        val type: TransactionType,
        val period: Period,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Tiles keep the user's own category order (set on the categories screen), never reshuffled. */
internal fun buildTiles(
    categories: List<CategoryWithSubcategories>,
    totals: Map<Long, Long>,
): List<CategoryTile> = categories.map { CategoryTile(it, totals[it.category.id] ?: 0) }
