package com.nackie.ownexpenses.presentation.format

import com.nackie.ownexpenses.domain.model.AmountInput
import com.nackie.ownexpenses.domain.model.AmountKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatterTest {
    private val uk = MoneyFormatter.Ukrainian
    private val en = MoneyFormatter.English

    // Real output uses no-break spaces; tests compare against readable strings.
    private fun MoneyFormatter.plain(minor: Long) = format(minor).replace('\u00A0', ' ')

    @Test
    fun wholeAmountsHaveNoKopiykas() {
        assertEquals("0 ₴", uk.plain(0))
        assertEquals("223 ₴", uk.plain(223_00))
        assertEquals("2 086 ₴", uk.plain(2_086_00))
        assertEquals("1 000 000 ₴", uk.plain(1_000_000_00))
    }

    @Test
    fun kopiykasAreAlwaysTwoDigits() {
        assertEquals("12,50 ₴", uk.plain(12_50))
        assertEquals("0,05 ₴", uk.plain(5))
    }

    @Test
    fun negativeAmountsUseMinusSign() {
        assertEquals("\u2212150,50 ₴", uk.plain(-150_50))
    }

    @Test
    fun signedFormatMarksIncomesWithPlus() {
        assertEquals("+1 000 ₴", uk.formatSigned(1_000_00).replace('\u00A0', ' '))
        assertEquals("\u221289 ₴", uk.formatSigned(-89_00).replace('\u00A0', ' '))
        assertEquals("0 ₴", uk.formatSigned(0).replace('\u00A0', ' '))
    }

    @Test
    fun englishUsesDotAndCommaGrouping() {
        assertEquals("27,276.40 ₴", en.plain(27_276_40))
    }

    @Test
    fun inputKeepsWhatWasTyped() {
        val keys = listOf(1, 2, 3, 4).map { AmountKey.Digit(it) } + AmountKey.DecimalSeparator + AmountKey.Digit(0)
        val input = keys.fold(AmountInput()) { acc, key -> acc.press(key) }

        assertEquals("1 234,0 ₴", uk.formatInput(input).replace('\u00A0', ' '))
        assertEquals("0 ₴", uk.formatInput(AmountInput()).replace('\u00A0', ' '))
        assertEquals("0, ₴", uk.formatInput(AmountInput().press(AmountKey.DecimalSeparator)).replace('\u00A0', ' '))
    }

    @Test
    fun formatterIsChosenByLanguage() {
        assertEquals(uk, MoneyFormatter.forLanguage("uk"))
        assertEquals(en, MoneyFormatter.forLanguage("en"))
        assertEquals(en, MoneyFormatter.forLanguage("de"))
    }
}
