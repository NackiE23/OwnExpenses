package com.nackie.ownexpenses.presentation.home

import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildTilesTest {
    private val categories = listOf(1L, 2L, 3L, 4L).map(::category)

    @Test
    fun tilesKeepTheUserOrderWhateverTheTotals() {
        val tiles = buildTiles(categories, totals = mapOf(3L to 500_00L, 4L to 900_00L))

        assertEquals(listOf(1L, 2L, 3L, 4L), tiles.map { it.category.category.id })
    }

    @Test
    fun totalsDefaultToZero() {
        val tiles = buildTiles(categories, totals = mapOf(2L to 300_00L))

        assertEquals(listOf(0L, 300_00L, 0L, 0L), tiles.map { it.totalMinor })
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
