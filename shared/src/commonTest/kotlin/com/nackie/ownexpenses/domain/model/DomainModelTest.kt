package com.nackie.ownexpenses.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class DomainModelTest {
    @Test
    fun signedAmountFollowsType() {
        assertEquals(1000_00, transaction(TransactionType.INCOME, 1000_00).signedAmountMinor)
        assertEquals(-327_00, transaction(TransactionType.EXPENSE, 327_00).signedAmountMinor)
    }

    @Test
    fun defaultSubcategoriesAreOneLevelDeepAndInheritParent() {
        DefaultCategory.entries.filter { it.parent != null }.forEach { subcategory ->
            val parent = subcategory.parent!!
            assertEquals(null, parent.parent, "$subcategory is nested too deep")
            assertEquals(parent.type, subcategory.type)
            assertEquals(parent.color, subcategory.color)
            assertTrue(
                DefaultCategory.entries.indexOf(parent) < DefaultCategory.entries.indexOf(subcategory),
                "$subcategory must be declared after its parent",
            )
        }
    }

    @Test
    fun defaultCategoriesCoverBothTypes() {
        TransactionType.entries.forEach { type ->
            assertTrue(DefaultCategory.entries.any { it.type == type && it.parent == null }, "No defaults for $type")
        }
    }

    private fun transaction(
        type: TransactionType,
        amountMinor: Long,
    ) = Transaction(
        id = 1,
        type = type,
        amountMinor = amountMinor,
        categoryId = 1,
        date = LocalDate(2026, 10, 1),
        description = null,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )
}
