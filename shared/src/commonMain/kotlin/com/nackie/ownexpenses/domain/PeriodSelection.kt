package com.nackie.ownexpenses.domain

import com.nackie.ownexpenses.domain.model.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The one period selection shared by the main screen, history and statistics. Starts at the
 * current month and lives for the process; it is a view filter, not user data.
 */
class PeriodSelection(
    todayProvider: TodayProvider,
) {
    private val selected = MutableStateFlow(Period.monthOf(todayProvider.today()))

    val period: StateFlow<Period> = selected.asStateFlow()

    fun select(period: Period) {
        selected.value = period
    }

    fun shift(steps: Int) {
        selected.update { it.shift(steps) }
    }
}
