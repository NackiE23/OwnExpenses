package com.nackie.ownexpenses.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.domain.OperationsFilter
import com.nackie.ownexpenses.domain.OperationsFilterSelection
import com.nackie.ownexpenses.domain.PeriodSelection
import com.nackie.ownexpenses.domain.TodayProvider
import com.nackie.ownexpenses.domain.leafIds
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodTotals
import com.nackie.ownexpenses.domain.model.TransactionDetails
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

data class HistoryUiState(
    val period: Period,
    val today: LocalDate,
    /** Of the shown records: the filtered ones when a filter is active. */
    val totals: PeriodTotals = PeriodTotals(),
    val days: List<DayGroup> = emptyList(),
    val filter: OperationsFilter = OperationsFilter(),
    /** Every category, archived included (their records are in history), for the filter. */
    val expenseCategories: List<CategoryWithSubcategories> = emptyList(),
    val incomeCategories: List<CategoryWithSubcategories> = emptyList(),
    val isLoading: Boolean = true,
)

/** Records of one calendar day, newest first, with the day's net sum (incomes minus expenses). */
data class DayGroup(
    val date: LocalDate,
    val netMinor: Long,
    val items: List<TransactionDetails>,
)

/**
 * Operations tab: records of the shared period grouped by day, optionally filtered by categories
 * and description text. Editing and deleting go through the entry panel's ViewModel, so undo
 * works the same everywhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val periodSelection: PeriodSelection,
    private val filterSelection: OperationsFilterSelection,
    todayProvider: TodayProvider,
) : ViewModel() {
    private val categories =
        combine(
            categoryRepository.observeCategories(TransactionType.EXPENSE, includeArchived = true),
            categoryRepository.observeCategories(TransactionType.INCOME, includeArchived = true),
        ) { expense, income -> expense to income }

    /** Read directly by the search field: updates without waiting for the filtered list. */
    val filter: StateFlow<OperationsFilter> = filterSelection.filter

    val uiState: StateFlow<HistoryUiState> =
        periodSelection.period
            .flatMapLatest { period ->
                combine(
                    transactionRepository.observeTransactionDetails(period),
                    transactionRepository.observePeriodTotals(period),
                    todayProvider.observeToday(),
                    filterSelection.filter,
                    categories,
                ) { details, totals, today, filter, (expense, income) ->
                    val shown = details.filteredBy(filter)
                    HistoryUiState(
                        period = period,
                        today = today,
                        totals = if (filter.isActive) shown.totals() else totals,
                        days = groupByDay(shown),
                        filter = filter,
                        expenseCategories = expense,
                        incomeCategories = income,
                        isLoading = false,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue =
                    HistoryUiState(
                        period = periodSelection.period.value,
                        today = todayProvider.today(),
                        filter = filterSelection.filter.value,
                    ),
            )

    fun showPreviousPeriod() = periodSelection.shift(-1)

    fun showNextPeriod() = periodSelection.shift(1)

    fun selectPeriod(period: Period) = periodSelection.select(period)

    fun setQuery(query: String) = updateFilter { it.copy(query = query.take(MAX_QUERY_LENGTH)) }

    /** The whole category with its subcategories: all of it on, or (when all on) off. */
    fun toggleCategory(category: CategoryWithSubcategories) =
        updateFilter { it.copy(categoryIds = it.categoryIds.toggleGroup(category.leafIds())) }

    /** One subcategory, or with a top-level id the records directly in that category. */
    fun toggleLeaf(id: Long) = updateFilter { it.copy(categoryIds = it.categoryIds.toggleLeaf(id)) }

    /** Takes the whole category out of the filter, whatever part of it was chosen. */
    fun removeCategory(category: CategoryWithSubcategories) =
        updateFilter { it.copy(categoryIds = it.categoryIds - category.leafIds()) }

    fun clearCategories() = updateFilter { it.copy(categoryIds = emptySet()) }

    fun clearFilter() = filterSelection.set(OperationsFilter())

    private fun updateFilter(transform: (OperationsFilter) -> OperationsFilter) =
        filterSelection.set(transform(filterSelection.filter.value))

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_QUERY_LENGTH = 100
    }
}

/** Records in the chosen categories whose description contains the query, ignoring case. */
internal fun List<TransactionDetails>.filteredBy(criteria: OperationsFilter): List<TransactionDetails> {
    if (!criteria.isActive) return this
    val query = criteria.query.trim()
    return filter { item ->
        (criteria.categoryIds.isEmpty() || item.transaction.categoryId in criteria.categoryIds) &&
            (query.isEmpty() || item.transaction.description?.contains(query, ignoreCase = true) == true)
    }
}

internal fun List<TransactionDetails>.totals(): PeriodTotals =
    PeriodTotals(
        expenseMinor = filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.amountMinor },
        incomeMinor = filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.amountMinor },
    )

/** Everything in [group] switches on, or off when all of it is already on. */
internal fun Set<Long>.toggleGroup(group: Set<Long>): Set<Long> = if (containsAll(group)) this - group else this + group

internal fun Set<Long>.toggleLeaf(id: Long): Set<Long> = if (id in this) this - id else this + id

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
