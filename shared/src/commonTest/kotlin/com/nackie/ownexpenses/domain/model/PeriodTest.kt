package com.nackie.ownexpenses.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeriodTest {
    @Test
    fun monthStartsOnFirstAndEndsOnLastDay() {
        val october = Period.monthOf(LocalDate(2026, 10, 24))

        assertEquals(LocalDate(2026, 10, 1), october.start)
        assertEquals(LocalDate(2026, 10, 31), october.endInclusive)
    }

    @Test
    fun monthHandlesFebruaryAndLeapYears() {
        assertEquals(LocalDate(2026, 2, 28), Period.monthOf(LocalDate(2026, 2, 10)).endInclusive)
        assertEquals(LocalDate(2028, 2, 29), Period.monthOf(LocalDate(2028, 2, 10)).endInclusive)
    }

    @Test
    fun monthShiftCrossesYearBoundary() {
        val december = Period.monthOf(LocalDate(2026, 12, 5))

        assertEquals(Period.monthOf(LocalDate(2027, 1, 1)), december.shift(1))
        assertEquals(Period.monthOf(LocalDate(2026, 11, 1)), december.shift(-1))
        assertEquals(Period.monthOf(LocalDate(2025, 12, 1)), december.shift(-12))
    }

    @Test
    fun weekRunsMondayToSunday() {
        // 24.10.2026 is a Saturday.
        val week = Period.weekOf(LocalDate(2026, 10, 24))

        assertEquals(LocalDate(2026, 10, 19), week.start)
        assertEquals(LocalDate(2026, 10, 25), week.endInclusive)
        assertEquals(week, Period.weekOf(LocalDate(2026, 10, 19)))
        assertEquals(week, Period.weekOf(LocalDate(2026, 10, 25)))
    }

    @Test
    fun weekShiftMovesSevenDays() {
        val week = Period.weekOf(LocalDate(2026, 10, 24))

        assertEquals(LocalDate(2026, 10, 26), week.shift(1).start)
        assertEquals(LocalDate(2026, 10, 12), week.shift(-1).start)
    }

    @Test
    fun weekMustStartOnMonday() {
        assertFailsWith<IllegalArgumentException> { Period.Week(LocalDate(2026, 10, 24)) }
    }

    @Test
    fun dayAndYear() {
        val day = Period.dayOf(LocalDate(2026, 12, 31))
        assertEquals(LocalDate(2027, 1, 1), day.shift(1).start)

        val year = Period.yearOf(LocalDate(2026, 10, 24))
        assertEquals(LocalDate(2026, 1, 1), year.start)
        assertEquals(LocalDate(2026, 12, 31), year.endInclusive)
        assertEquals(Period.Year(2025), year.shift(-1))
    }

    @Test
    fun allTimeCoversEverythingAndDoesNotShift() {
        assertTrue(LocalDate(1990, 1, 1) in Period.AllTime)
        assertTrue(LocalDate(2100, 1, 1) in Period.AllTime)
        assertEquals(Period.AllTime, Period.AllTime.shift(3))
    }

    @Test
    fun customRangeShiftsByItsLength() {
        val range = Period.Custom(LocalDate(2026, 10, 1), LocalDate(2026, 10, 10))

        assertEquals(Period.Custom(LocalDate(2026, 10, 11), LocalDate(2026, 10, 20)), range.shift(1))
        assertEquals(Period.Custom(LocalDate(2026, 9, 21), LocalDate(2026, 9, 30)), range.shift(-1))
        assertFailsWith<IllegalArgumentException> { Period.Custom(LocalDate(2026, 10, 2), LocalDate(2026, 10, 1)) }
    }

    @Test
    fun containsIsInclusive() {
        val october = Period.monthOf(LocalDate(2026, 10, 1))

        assertTrue(LocalDate(2026, 10, 1) in october)
        assertTrue(LocalDate(2026, 10, 31) in october)
        assertFalse(LocalDate(2026, 11, 1) in october)
        assertFalse(LocalDate(2026, 9, 30) in october)
    }
}
