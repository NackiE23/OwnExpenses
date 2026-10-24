package com.nackie.ownexpenses.presentation.history

import com.nackie.ownexpenses.domain.model.Transaction
import com.nackie.ownexpenses.domain.model.TransactionDetails
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class GroupByDayTest {
    private val oct24 = LocalDate(2026, 10, 24)
    private val oct23 = LocalDate(2026, 10, 23)

    @Test
    fun groupsConsecutiveRecordsOfTheSameDay() {
        val days =
            groupByDay(
                listOf(
                    item(1, oct24, TransactionType.EXPENSE, 89_00),
                    item(2, oct24, TransactionType.EXPENSE, 209_00),
                    item(3, oct23, TransactionType.EXPENSE, 721_00),
                ),
            )

        assertEquals(listOf(oct24, oct23), days.map { it.date })
        assertEquals(listOf(1L, 2L), days[0].items.map { it.transaction.id })
        assertEquals(listOf(3L), days[1].items.map { it.transaction.id })
    }

    @Test
    fun dayTotalIsIncomesMinusExpenses() {
        val days =
            groupByDay(
                listOf(
                    item(1, oct24, TransactionType.INCOME, 1000_00),
                    item(2, oct24, TransactionType.EXPENSE, 300_00),
                    item(3, oct23, TransactionType.EXPENSE, 150_00),
                ),
            )

        assertEquals(listOf(700_00L, -150_00L), days.map { it.netMinor })
    }

    @Test
    fun noRecordsNoDays() {
        assertTrue(groupByDay(emptyList()).isEmpty())
    }

    private fun item(
        id: Long,
        date: LocalDate,
        type: TransactionType,
        amountMinor: Long,
    ) = TransactionDetails(
        transaction =
            Transaction(
                id = id,
                type = type,
                amountMinor = amountMinor,
                categoryId = 1,
                date = date,
                description = null,
                createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0),
            ),
        categoryName = "c",
        subcategoryName = null,
        icon = "other",
        color = 0,
    )
}
