package com.nackie.ownexpenses.presentation.home

import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildTilesTest {
    private val categories = listOf(1L, 2L, 3L, 4L).map(::category)

    @Test
    fun mostUsedFirstAndTiesKeepUserOrder() {
        val tiles = buildTiles(categories, totals = emptyMap(), usage = mapOf(3L to 5L, 4L to 5L, 2L to 1L))

        assertEquals(listOf(3L, 4L, 2L, 1L), tiles.map { it.category.category.id })
    }

    @Test
    fun withoutUsageOrderIsUnchanged() {
        val tiles = buildTiles(categories, totals = emptyMap(), usage = emptyMap())

        assertEquals(listOf(1L, 2L, 3L, 4L), tiles.map { it.category.category.id })
    }

    @Test
    fun totalsDefaultToZero() {
        val tiles = buildTiles(categories, totals = mapOf(2L to 300_00L), usage = emptyMap())

        assertEquals(listOf(0L, 300_00L, 0L, 0L), tiles.map { it.totalMinor })
    }

    @Test
    fun usageWindowIsLastThirtyDaysIncludingToday() {
        val window = usageWindow(LocalDate(2026, 10, 24))

        assertEquals(Period.Custom(LocalDate(2026, 9, 25), LocalDate(2026, 10, 24)), window)
    }

    private fun category(id: Long) =
        CategoryWithSubcategories(
            Category(
                id = id,
                parentId = null,
                name = "c$id",
                type = TransactionType.EXPENSE,
                color = 0,
                icon = "other",
                sortOrder = id.toInt(),
                isArchived = false,
            ),
            subcategories = emptyList(),
        )
}
