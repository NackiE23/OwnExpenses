package com.nackie.ownexpenses.presentation.statistics

import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatisticsModelsTest {
    @Test
    fun monthHasOneBarPerDayIncludingEmptyDays() {
        val chart =
            buildSpendingChart(
                Period.monthOf(LocalDate(2026, 10, 1)),
                mapOf(LocalDate(2026, 10, 3) to 300_00L, LocalDate(2026, 10, 17) to 327_00L),
            )

        assertEquals(BarGranularity.DAY, chart.granularity)
        assertEquals(31, chart.bars.size)
        assertEquals(LocalDate(2026, 10, 1), chart.bars.first().start)
        assertEquals(300_00, chart.bars[2].totalMinor)
        assertEquals(0, chart.bars[3].totalMinor)
        assertEquals(327_00, chart.maxMinor)
    }

    @Test
    fun weekHasSevenBars() {
        val chart = buildSpendingChart(Period.weekOf(LocalDate(2026, 10, 24)), emptyMap())

        assertEquals(7, chart.bars.size)
        assertTrue(chart.bars.all { it.totalMinor == 0L })
    }

    @Test
    fun yearIsGroupedByMonth() {
        val chart =
            buildSpendingChart(
                Period.Year(2026),
                mapOf(
                    LocalDate(2026, 2, 1) to 10_00L,
                    LocalDate(2026, 2, 28) to 5_00L,
                    LocalDate(2026, 12, 31) to 1_00L,
                ),
            )

        assertEquals(BarGranularity.MONTH, chart.granularity)
        assertEquals(12, chart.bars.size)
        assertEquals(LocalDate(2026, 2, 1), chart.bars[1].start)
        assertEquals(15_00, chart.bars[1].totalMinor)
        assertEquals(1_00, chart.bars[11].totalMinor)
    }

    @Test
    fun customRangeSwitchesToMonthsAfterSixtyTwoDays() {
        val short = Period.Custom(LocalDate(2026, 9, 1), LocalDate(2026, 11, 1)) // 62 days
        val long = Period.Custom(LocalDate(2026, 9, 1), LocalDate(2026, 11, 2)) // 63 days

        assertEquals(BarGranularity.DAY, buildSpendingChart(short, emptyMap()).granularity)
        val months = buildSpendingChart(long, emptyMap())
        assertEquals(BarGranularity.MONTH, months.granularity)
        assertEquals(listOf(9, 10, 11), months.bars.map { it.start.month.ordinal + 1 })
    }

    @Test
    fun allTimeSpansTheRecordedDates() {
        val chart =
            buildSpendingChart(
                Period.AllTime,
                mapOf(LocalDate(2025, 11, 20) to 1_00L, LocalDate(2026, 10, 24) to 2_00L),
            )

        assertEquals(BarGranularity.MONTH, chart.granularity)
        assertEquals(LocalDate(2025, 11, 1), chart.bars.first().start)
        assertEquals(LocalDate(2026, 10, 1), chart.bars.last().start)
        assertEquals(12, chart.bars.size)
        assertTrue(buildSpendingChart(Period.AllTime, emptyMap()).bars.isEmpty())
    }

    @Test
    fun sharesAreLargestFirstAndIncludeSubcategories() {
        val groceries = category(1, "Groceries")
        val transport = category(2, "Transport")
        val taxi = category(3, "Taxi", parentId = 2)
        val bus = category(4, "Bus", parentId = 2)
        val cafe = category(5, "Cafe")
        val tree =
            listOf(
                CategoryWithSubcategories(groceries, emptyList()),
                CategoryWithSubcategories(transport, listOf(taxi, bus)),
                CategoryWithSubcategories(cafe, emptyList()),
            )

        val shares = buildCategoryShares(tree, mapOf(1L to 300_00L, 2L to 50_00L, 3L to 150_00L))

        assertEquals(listOf("Groceries", "Transport"), shares.map { it.category.name })
        assertEquals(0.6f, shares[0].share, 0.0001f)
        assertEquals(200_00, shares[1].totalMinor)
        assertEquals(0.4f, shares[1].share, 0.0001f)
        assertTrue(shares[0].parts.isEmpty())

        val parts = shares[1].parts
        assertEquals(listOf("Taxi", null), parts.map { it.subcategory?.name })
        assertEquals(listOf(0.75f, 0.25f), parts.map { it.share })
    }

    @Test
    fun noExpensesNoShares() {
        assertTrue(
            buildCategoryShares(listOf(CategoryWithSubcategories(category(1, "A"), emptyList())), emptyMap()).isEmpty(),
        )
    }

    @Test
    fun ownRecordsAloneNeedNoBreakdown() {
        val transport = category(2, "Transport")
        val taxi = category(3, "Taxi", parentId = 2)

        val share = buildCategoryShares(listOf(CategoryWithSubcategories(transport, listOf(taxi))), mapOf(2L to 10_00L))

        assertTrue(share.single().parts.isEmpty())
        assertNull(share.single().parts.firstOrNull())
    }

    private fun category(
        id: Long,
        name: String,
        parentId: Long? = null,
    ) = Category(
        id = id,
        parentId = parentId,
        name = name,
        type = TransactionType.EXPENSE,
        color = 0,
        icon = "other",
        sortOrder = id.toInt(),
        isArchived = false,
    )

    @Test
    fun axisUsesRoundStepsUpToFourLines() {
        // 589 ₴: 148 ₴ per line rounds up to 200 ₴.
        assertEquals(listOf(200_00L, 400_00L, 600_00L), valueAxisTicks(589_00))
        // 5 126 ₴: 2 000 ₴ steps.
        assertEquals(listOf(2000_00L, 4000_00L, 6000_00L), valueAxisTicks(5126_00))
        // Exactly on a line: no extra line above.
        assertEquals(listOf(1000_00L, 2000_00L, 3000_00L, 4000_00L), valueAxisTicks(4000_00))
        // 13 000 ₴: 5 000 ₴ steps.
        assertEquals(listOf(5000_00L, 10000_00L, 15000_00L), valueAxisTicks(13000_00))
    }

    @Test
    fun axisStepIsAtLeastOneHryvnia() {
        assertEquals(listOf(1_00L, 2_00L), valueAxisTicks(1_50))
        assertEquals(listOf(1_00L), valueAxisTicks(5))
        assertEquals(emptyList(), valueAxisTicks(0))
    }
}
