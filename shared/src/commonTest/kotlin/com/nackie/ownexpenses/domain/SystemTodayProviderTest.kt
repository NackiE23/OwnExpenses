package com.nackie.ownexpenses.domain

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SystemTodayProviderTest {
    private val kyiv = TimeZone.of("Europe/Kyiv")

    /** Wall clock that moves with the test's virtual time. */
    private fun TestScope.clockStartingAt(start: Instant) =
        object : Clock {
            override fun now(): Instant = start + testScheduler.currentTime.milliseconds
        }

    @Test
    fun todayUsesDeviceTimeZone() =
        runTest {
            // 22:30 UTC on the 24th is already 01:30 on the 25th in Kyiv (UTC+3 in October).
            val clock = clockStartingAt(Instant.parse("2026-10-24T22:30:00Z"))

            assertEquals(LocalDate(2026, 10, 25), SystemTodayProvider(clock) { kyiv }.today())
            assertEquals(LocalDate(2026, 10, 24), SystemTodayProvider(clock) { TimeZone.UTC }.today())
        }

    @Test
    fun emitsAgainAfterMidnight() =
        runTest {
            val clock = clockStartingAt(Instant.parse("2026-10-24T20:00:00Z")) // 23:00 in Kyiv

            SystemTodayProvider(clock) { kyiv }.observeToday().test {
                assertEquals(LocalDate(2026, 10, 24), awaitItem())
                testScheduler.advanceTimeBy(1.hours)
                testScheduler.runCurrent()
                assertEquals(LocalDate(2026, 10, 25), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
