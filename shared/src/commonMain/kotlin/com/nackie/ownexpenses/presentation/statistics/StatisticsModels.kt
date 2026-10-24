package com.nackie.ownexpenses.presentation.statistics

import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.Period
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** What one bar of the spending chart covers. */
enum class BarGranularity {
    DAY,
    MONTH,
}

/** One bar: the day, or the first day of the month, and the expense sum in minor units. */
data class SpendingBar(
    val start: LocalDate,
    val totalMinor: Long,
)

data class SpendingChart(
    val granularity: BarGranularity,
    val bars: List<SpendingBar>,
) {
    val maxMinor: Long get() = bars.maxOfOrNull { it.totalMinor } ?: 0
}

/** Top-level category with its share of all expenses of the period. */
data class CategoryShare(
    val category: Category,
    val totalMinor: Long,
    /** 0..1 of the period's total. */
    val share: Float,
    /** Breakdown inside the category; empty when it has no subcategories with records. */
    val parts: List<SubcategoryShare>,
)

/** Part of a category: a subcategory, or records put on the category itself ([subcategory] null). */
data class SubcategoryShare(
    val subcategory: Category?,
    val totalMinor: Long,
    /** 0..1 of the parent category's total. */
    val share: Float,
)

/** Periods up to this many days get one bar per day; longer ones one bar per month. */
const val MAX_DAILY_BARS = 62

/**
 * Bars for [period]. "All time" spans from the first to the last day with records. Days or
 * months without records are included with zero, so gaps stay visible.
 */
fun buildSpendingChart(
    period: Period,
    dailyTotals: Map<LocalDate, Long>,
): SpendingChart {
    val start: LocalDate
    val end: LocalDate
    if (period == Period.AllTime) {
        if (dailyTotals.isEmpty()) return SpendingChart(BarGranularity.DAY, emptyList())
        start = dailyTotals.keys.min()
        end = dailyTotals.keys.max()
    } else {
        start = period.start
        end = period.endInclusive
    }

    return if (start.daysUntil(end) + 1 <= MAX_DAILY_BARS) {
        val bars = generateSequence(start) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= end }
        SpendingChart(BarGranularity.DAY, bars.map { SpendingBar(it, dailyTotals[it] ?: 0) }.toList())
    } else {
        val byMonth = dailyTotals.entries.groupBy({ it.key.firstOfMonth() }, { it.value }).mapValues { it.value.sum() }
        val months =
            generateSequence(start.firstOfMonth()) { it.plus(1, DateTimeUnit.MONTH) }.takeWhile { it <= end }
        SpendingChart(BarGranularity.MONTH, months.map { SpendingBar(it, byMonth[it] ?: 0) }.toList())
    }
}

/**
 * Shares of top-level categories, largest first, with subcategory breakdown. [perCategory] holds
 * sums as recorded (category or subcategory id); [categories] must include archived ones so old
 * records are not lost.
 */
fun buildCategoryShares(
    categories: List<CategoryWithSubcategories>,
    perCategory: Map<Long, Long>,
): List<CategoryShare> {
    val grandTotal = perCategory.values.sum()
    if (grandTotal == 0L) return emptyList()

    return categories
        .mapNotNull { node ->
            val own = perCategory[node.category.id] ?: 0
            val subcategoryTotals =
                node.subcategories
                    .map { it to (perCategory[it.id] ?: 0) }
                    .filter { (_, total) -> total > 0 }
            val total = own + subcategoryTotals.sumOf { it.second }
            if (total == 0L) return@mapNotNull null

            val parts =
                if (subcategoryTotals.isEmpty()) {
                    emptyList()
                } else {
                    val withOwn = if (own > 0) subcategoryTotals + (null to own) else subcategoryTotals
                    withOwn
                        .map { (subcategory, sum) -> SubcategoryShare(subcategory, sum, sum.toFloat() / total) }
                        .sortedByDescending { it.totalMinor }
                }
            CategoryShare(node.category, total, total.toFloat() / grandTotal, parts)
        }.sortedByDescending { it.totalMinor }
}

private fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month, 1)
