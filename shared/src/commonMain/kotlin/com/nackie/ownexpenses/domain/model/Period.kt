package com.nackie.ownexpenses.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Date filter shared by the main screen, history and statistics. Every period is an inclusive
 * range of calendar dates; it never affects the balance, which always covers all time.
 */
sealed interface Period {
    val start: LocalDate
    val endInclusive: LocalDate

    /** Previous (negative) or next (positive) period of the same kind. */
    fun shift(steps: Int): Period

    operator fun contains(date: LocalDate): Boolean = date in start..endInclusive

    data class Day(
        val date: LocalDate,
    ) : Period {
        override val start get() = date
        override val endInclusive get() = date

        override fun shift(steps: Int) = Day(date.plus(steps, DateTimeUnit.DAY))
    }

    /** Monday to Sunday. */
    data class Week(
        override val start: LocalDate,
    ) : Period {
        init {
            require(start.dayOfWeek == DayOfWeek.MONDAY) { "A week starts on Monday, was $start" }
        }

        override val endInclusive get() = start.plus(6, DateTimeUnit.DAY)

        override fun shift(steps: Int) = Week(start.plus(steps * 7, DateTimeUnit.DAY))
    }

    /** From the 1st to the last day of the month. */
    data class Month(
        override val start: LocalDate,
    ) : Period {
        init {
            require(start.day == 1) { "A month starts on the 1st, was $start" }
        }

        override val endInclusive get() = start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

        override fun shift(steps: Int) = Month(start.plus(steps, DateTimeUnit.MONTH))
    }

    data class Year(
        val year: Int,
    ) : Period {
        override val start get() = LocalDate(year, 1, 1)
        override val endInclusive get() = LocalDate(year, 12, 31)

        override fun shift(steps: Int) = Year(year + steps)
    }

    data object AllTime : Period {
        override val start = LocalDate(1, 1, 1)
        override val endInclusive = LocalDate(9999, 12, 31)

        override fun shift(steps: Int) = this
    }

    /** Arbitrary range; shifting moves it by its own length. */
    data class Custom(
        override val start: LocalDate,
        override val endInclusive: LocalDate,
    ) : Period {
        init {
            require(start <= endInclusive) { "Empty range $start..$endInclusive" }
        }

        override fun shift(steps: Int): Custom {
            val length = start.daysUntil(endInclusive) + 1
            return Custom(
                start.plus(steps * length, DateTimeUnit.DAY),
                endInclusive.plus(steps * length, DateTimeUnit.DAY),
            )
        }
    }

    companion object {
        fun dayOf(date: LocalDate): Period = Day(date)

        fun weekOf(date: LocalDate): Period = Week(date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY))

        fun monthOf(date: LocalDate): Period = Month(LocalDate(date.year, date.month, 1))

        fun yearOf(date: LocalDate): Period = Year(date.year)
    }
}
