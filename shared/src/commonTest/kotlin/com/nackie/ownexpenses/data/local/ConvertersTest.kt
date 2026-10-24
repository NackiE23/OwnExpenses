package com.nackie.ownexpenses.data.local

import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConvertersTest {
    @Test
    fun localDateRoundTripsThroughEpochDays() {
        val dates =
            listOf(
                LocalDate(1970, 1, 1),
                LocalDate(2024, 2, 29),
                LocalDate(2026, 10, 24),
                LocalDate(1969, 12, 31),
            )

        dates.forEach { date ->
            val stored = Converters.localDateToEpochDays(date)
            assertEquals(date, Converters.epochDaysToLocalDate(stored))
        }
    }

    @Test
    fun epochDaysPreserveCalendarOrder() {
        val earlier = Converters.localDateToEpochDays(LocalDate(2026, 9, 30))
        val later = Converters.localDateToEpochDays(LocalDate(2026, 10, 1))

        assertEquals(1, later - earlier)
        assertTrue(earlier < later)
    }

    @Test
    fun transactionTypeUsesStableKeys() {
        assertEquals("expense", Converters.transactionTypeToKey(TransactionType.EXPENSE))
        assertEquals("income", Converters.transactionTypeToKey(TransactionType.INCOME))

        TransactionType.entries.forEach { type ->
            assertEquals(type, Converters.keyToTransactionType(Converters.transactionTypeToKey(type)))
        }
    }

    @Test
    fun unknownTransactionTypeKeyFails() {
        assertFailsWith<IllegalStateException> { Converters.keyToTransactionType("transfer") }
    }
}
