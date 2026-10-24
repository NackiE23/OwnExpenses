package com.nackie.ownexpenses.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Current calendar date in the device time zone. */
interface TodayProvider {
    fun today(): LocalDate

    /**
     * Emits today and again after each midnight, so screens that stay open roll over
     * (e.g. records dated tomorrow start counting towards the balance).
     */
    fun observeToday(): Flow<LocalDate>
}

class SystemTodayProvider(
    private val clock: Clock,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : TodayProvider {
    override fun today(): LocalDate = clock.todayIn(timeZone())

    override fun observeToday(): Flow<LocalDate> =
        flow {
            while (true) {
                val zone = timeZone()
                val today = clock.todayIn(zone)
                emit(today)
                val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
                delay(nextMidnight - clock.now() + MIDNIGHT_SLACK)
            }
        }.distinctUntilChanged()

    private companion object {
        val MIDNIGHT_SLACK = 1.seconds
    }
}
