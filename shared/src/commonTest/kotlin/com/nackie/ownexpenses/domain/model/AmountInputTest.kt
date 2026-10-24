package com.nackie.ownexpenses.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmountInputTest {
    private fun typed(text: String): AmountInput = text.fold(AmountInput()) { input, char -> input.type(char) }

    @Test
    fun emptyInputIsZero() {
        assertTrue(AmountInput().isEmpty)
        assertEquals(0, AmountInput().minorUnits)
    }

    @Test
    fun wholeAmountsAreConvertedToMinorUnits() {
        assertEquals(125_00, typed("125").minorUnits)
        assertEquals(1_000_000_00, typed("1000000").minorUnits)
    }

    @Test
    fun commaAndDotAreBothDecimalSeparators() {
        assertEquals(12_50, typed("12,5").minorUnits)
        assertEquals(12_50, typed("12.5").minorUnits)
        assertEquals(12_05, typed("12,05").minorUnits)
    }

    @Test
    fun atMostTwoDecimalPlaces() {
        val input = typed("9.999")

        assertEquals("99", input.fraction)
        assertEquals(9_99, input.minorUnits)
    }

    @Test
    fun secondSeparatorIsIgnored() {
        assertEquals(typed("1.5"), typed("1.,5"))
    }

    @Test
    fun separatorFirstStartsWithZero() {
        val input = typed(",5")

        assertEquals("0", input.whole)
        assertEquals(50, input.minorUnits)
    }

    @Test
    fun leadingZerosAreDropped() {
        assertEquals("7", typed("007").whole)
        assertEquals("0", typed("000").whole)
        assertEquals(0, typed("000").minorUnits)
    }

    @Test
    fun wholePartIsCapped() {
        val input = typed("12345678901")

        assertEquals(AmountInput.MAX_INTEGER_DIGITS, input.whole.length)
        assertEquals(123_456_789_00, input.minorUnits)
    }

    @Test
    fun backspaceRemovesLastTypedCharacter() {
        var input = typed("12,34")
        val steps = mutableListOf<Long>()
        repeat(6) {
            input = input.press(AmountKey.Backspace)
            steps += input.minorUnits
        }

        assertEquals(listOf(12_30L, 12_00, 12_00, 1_00, 0, 0), steps)
        assertTrue(input.isEmpty)
    }

    @Test
    fun prefilledFromStoredAmount() {
        assertEquals(typed("89"), AmountInput.fromMinorUnits(89_00))
        assertEquals(typed("12,50"), AmountInput.fromMinorUnits(12_50))
        assertEquals(typed("0,05"), AmountInput.fromMinorUnits(5))
        // Editing continues from the stored value: "12,50" -> "12,5" -> "12,57".
        val edited = AmountInput.fromMinorUnits(12_50).press(AmountKey.Backspace).press(AmountKey.Digit(7))
        assertEquals(12_57, edited.minorUnits)
    }

    @Test
    fun otherCharactersAreIgnored() {
        assertEquals(typed("15"), typed("1a5-"))
    }
}
