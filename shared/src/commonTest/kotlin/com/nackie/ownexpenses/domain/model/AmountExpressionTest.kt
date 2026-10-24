package com.nackie.ownexpenses.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmountExpressionTest {
    /** Types with keyboard characters: digits, "." "," and "+ - * /". */
    private fun typed(text: String): AmountExpression =
        text.fold(AmountExpression()) { expression, char -> expression.type(char) }

    @Test
    fun plainNumberIsItsOwnResult() {
        val expression = typed("125,5")

        assertFalse(expression.hasOperators)
        assertEquals(125_50, expression.result)
    }

    @Test
    fun emptyExpressionIsZero() {
        assertTrue(AmountExpression().isEmpty)
        assertEquals(0, AmountExpression().result)
    }

    @Test
    fun multiplicationAndDivisionGoFirst() {
        assertEquals(190_00, typed("120+35*2").result)
        assertEquals(110_00, typed("100+30/3").result)
        assertEquals(70_00, typed("100-15*2").result)
    }

    @Test
    fun samePriorityGoesLeftToRight() {
        assertEquals(50_00, typed("100-30-20").result)
        assertEquals(5_00, typed("100/10/2").result)
        assertEquals(60_00, typed("100/10*6").result)
    }

    @Test
    fun decimalsWorkInEveryOperand() {
        assertEquals(25_00, typed("12,5*2").result)
        assertEquals(30_30, typed("10.1*3").result)
        assertEquals(1_25, typed("2.5/2").result)
    }

    @Test
    fun divisionRoundsHalfUpToKopiykas() {
        assertEquals(33_33, typed("100/3").result)
        assertEquals(66_67, typed("200/3").result)
        // Each step is rounded, like a pocket calculator showing kopiykas.
        assertEquals(99_99, typed("100/3*3").result)
    }

    @Test
    fun divisionByZeroCannotBeComputed() {
        assertNull(typed("5/0").result)
        assertNull(typed("5/0+1").result)
    }

    @Test
    fun resultMayBeNegativeOrZero() {
        assertEquals(-30_00, typed("50-80").result)
        assertEquals(0, typed("50-50").result)
    }

    @Test
    fun trailingOperatorIsIgnored() {
        val expression = typed("120+")

        assertTrue(expression.hasOperators)
        assertEquals(120_00, expression.result)
    }

    @Test
    fun newOperatorReplacesThePendingOne() {
        assertEquals(typed("120*"), typed("120+*"))
        assertEquals(240_00, typed("120+*2").result)
    }

    @Test
    fun operatorBeforeAnyNumberIsIgnored() {
        assertEquals(AmountExpression(), typed("+-"))
        assertEquals(5_00, typed("-5").result)
    }

    @Test
    fun backspaceRemovesDigitsThenTheOperator() {
        var expression = typed("12+34")
        val results = mutableListOf<Long?>()
        repeat(5) {
            expression = expression.press(AmountKey.Backspace)
            results += expression.result
        }

        // "12+3" -> "12+" -> "12" -> "1" -> ""
        assertEquals(listOf<Long?>(15_00, 12_00, 12_00, 1_00, 0), results)
        assertTrue(expression.isEmpty)
    }

    @Test
    fun operandsKeepNumberRules() {
        // At most two decimals and one separator per number.
        assertEquals(typed("1.99+2"), typed("1.999+2"))
        assertEquals(3_99, typed("1.99+2").result)
    }

    @Test
    fun tooLargeResultCannotBeComputed() {
        assertNull(typed("999999999*999999999").result)
        assertNull(typed("999999999+999999999").result)
        assertEquals(AmountExpression.MAX_RESULT_MINOR, typed("999999999.99").result)
    }

    @Test
    fun operatorCountIsCapped() {
        val long = typed("1+".repeat(30) + "1")

        assertEquals(AmountExpression.MAX_OPERATORS, long.operators.size)
    }

    @Test
    fun editingStartsFromStoredAmount() {
        val stored = AmountExpression.of(89_00)
        val expression = stored.press(AmountKey.Operator(AmountOperator.TIMES)).press(AmountKey.Digit(2))

        assertEquals(178_00, expression.result)
    }
}
