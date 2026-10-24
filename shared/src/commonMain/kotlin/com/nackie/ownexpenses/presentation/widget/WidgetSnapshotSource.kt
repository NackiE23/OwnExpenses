package com.nackie.ownexpenses.presentation.widget

import com.nackie.ownexpenses.domain.TodayProvider
import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.domain.repository.CategoryRepository
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import com.nackie.ownexpenses.presentation.home.buildTiles
import com.nackie.ownexpenses.presentation.home.usageWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

/** What the home-screen widget shows (docs/TZ.md, FR-9). */
data class WidgetSnapshot(
    val balanceMinor: Long,
    /** Most used expense categories, same order as on the main screen. */
    val categories: List<Category>,
)

/** Same balance and ordering rules as the main screen, so the widget never disagrees with it. */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSnapshotSource(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val todayProvider: TodayProvider,
) {
    fun observe(): Flow<WidgetSnapshot> =
        todayProvider
            .observeToday()
            .flatMapLatest { today ->
                combine(
                    transactionRepository.observeBalance(today),
                    categoryRepository.observeCategories(TransactionType.EXPENSE),
                    transactionRepository.observeCategoryUsage(usageWindow(today)),
                ) { balance, categories, usage ->
                    val top = buildTiles(categories, totals = emptyMap(), usage = usage).take(WIDGET_CATEGORY_COUNT)
                    WidgetSnapshot(balance, top.map { it.category.category })
                }
            }.distinctUntilChanged()

    suspend fun current(): WidgetSnapshot = observe().first()

    companion object {
        const val WIDGET_CATEGORY_COUNT = 3
    }
}
