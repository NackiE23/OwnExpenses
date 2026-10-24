package com.nackie.ownexpenses.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.presentation.format.MoneyFormatter
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.date_day_month
import com.nackie.ownexpenses.ui.resources.date_day_month_year
import com.nackie.ownexpenses.ui.resources.date_month_year
import com.nackie.ownexpenses.ui.resources.date_range
import com.nackie.ownexpenses.ui.resources.date_today
import com.nackie.ownexpenses.ui.resources.date_tomorrow
import com.nackie.ownexpenses.ui.resources.date_yesterday
import com.nackie.ownexpenses.ui.resources.months_standalone
import com.nackie.ownexpenses.ui.resources.months_with_day
import com.nackie.ownexpenses.ui.resources.period_all_time
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Money formatter matching the UI language (decimal comma for Ukrainian). */
@Composable
internal fun rememberMoneyFormatter(): MoneyFormatter {
    val language = Locale.current.language
    return remember(language) { MoneyFormatter.forLanguage(language) }
}

/** "Жовтень 2026", "19 жовтня – 25 жовтня 2026", "2026", "Увесь час". */
@Composable
internal fun periodLabel(period: Period): String =
    when (period) {
        is Period.Month -> {
            stringResource(
                Res.string.date_month_year,
                stringArrayResource(Res.array.months_standalone)[period.start.month.number - 1],
                period.start.year.toString(),
            )
        }

        is Period.Year -> {
            period.year.toString()
        }

        Period.AllTime -> {
            stringResource(Res.string.period_all_time)
        }

        is Period.Day -> {
            dayMonthYear(period.date)
        }

        is Period.Week, is Period.Custom -> {
            stringResource(Res.string.date_range, dayMonth(period.start), dayMonthYear(period.endInclusive))
        }
    }

/** "Сьогодні, 24 жовтня", "Вчора, 23 жовтня", "20 жовтня", "31 грудня 2025". */
@Composable
internal fun entryDateLabel(
    date: LocalDate,
    today: LocalDate,
): String =
    when (today.daysUntil(date)) {
        0 -> stringResource(Res.string.date_today, dayMonth(date))
        -1 -> stringResource(Res.string.date_yesterday, dayMonth(date))
        1 -> stringResource(Res.string.date_tomorrow, dayMonth(date))
        else -> if (date.year == today.year) dayMonth(date) else dayMonthYear(date)
    }

@Composable
private fun dayMonth(date: LocalDate): String =
    stringResource(Res.string.date_day_month, date.day.toString(), monthWithDay(date))

@Composable
private fun dayMonthYear(date: LocalDate): String =
    stringResource(Res.string.date_day_month_year, date.day.toString(), monthWithDay(date), date.year.toString())

@Composable
private fun monthWithDay(date: LocalDate): String =
    stringArrayResource(Res.array.months_with_day)[date.month.number - 1]
