package com.nackie.ownexpenses.domain.model

/**
 * What the keypad builds: numbers joined by + − × ÷, e.g. "120 + 35 × 2". The record gets
 * [result]; a plain number is an expression without operators.
 *
 * Rules, chosen to match a pocket calculator people expect from a money app:
 * - × and ÷ go before + and −; equal priority goes left to right.
 * - Every × and ÷ step is rounded half up to kopiykas (100 ÷ 3 = 33,33).
 * - A trailing operator is ignored ("120 +" is 120), and a new operator right after another
 *   replaces it, so a mistap is fixed by tapping the right one.
 * - An operator before the first number is ignored: amounts are never negative.
 */
data class AmountExpression(
    /** Never empty; the last one is being typed and is empty right after an operator. */
    val operands: List<AmountInput> = listOf(AmountInput()),
    /** Operator i stands between operand i and i + 1. */
    val operators: List<AmountOperator> = emptyList(),
) {
    init {
        require(operands.size == operators.size + 1) { "Operators must sit between operands" }
    }

    val isEmpty: Boolean get() = operators.isEmpty() && operands.single().isEmpty

    val hasOperators: Boolean get() = operators.isNotEmpty()

    /**
     * Value in minor units, or null when it cannot be computed: division by zero, or a value
     * beyond [MAX_RESULT_MINOR]. May be zero or negative; only a positive one can be saved.
     */
    val result: Long?
        get() {
            var values = operands.map { it.minorUnits }
            var ops = operators
            if (ops.isNotEmpty() && operands.last().isEmpty) {
                values = values.dropLast(1)
                ops = ops.dropLast(1)
            }

            // First pass: × and ÷ fold into the term on their left.
            val terms = mutableListOf(values.first())
            val additive = mutableListOf<AmountOperator>()
            ops.forEachIndexed { index, operator ->
                val value = values[index + 1]
                when (operator) {
                    AmountOperator.TIMES -> {
                        terms[terms.lastIndex] = multiply(terms.last(), value) ?: return null
                    }

                    AmountOperator.DIVIDE -> {
                        terms[terms.lastIndex] = divide(terms.last(), value) ?: return null
                    }

                    AmountOperator.PLUS, AmountOperator.MINUS -> {
                        terms += value
                        additive += operator
                    }
                }
            }

            // Second pass: + and − left to right. Terms are at most MAX_RESULT_MINOR, so no overflow.
            var sum = terms.first()
            additive.forEachIndexed { index, operator ->
                sum = if (operator == AmountOperator.PLUS) sum + terms[index + 1] else sum - terms[index + 1]
            }
            return sum.takeIf { it in -MAX_RESULT_MINOR..MAX_RESULT_MINOR }
        }

    fun press(key: AmountKey): AmountExpression =
        when (key) {
            is AmountKey.Digit, AmountKey.DecimalSeparator -> {
                withCurrent(operands.last().press(key))
            }

            AmountKey.Backspace -> {
                when {
                    !operands.last().isEmpty -> withCurrent(operands.last().press(key))
                    operators.isNotEmpty() -> AmountExpression(operands.dropLast(1), operators.dropLast(1))
                    else -> this
                }
            }

            is AmountKey.Operator -> {
                when {
                    !operands.last().isEmpty -> {
                        if (operators.size < MAX_OPERATORS) {
                            AmountExpression(operands + AmountInput(), operators + key.operator)
                        } else {
                            this
                        }
                    }

                    operators.isNotEmpty() -> {
                        copy(operators = operators.dropLast(1) + key.operator)
                    }

                    else -> {
                        this
                    }
                }
            }
        }

    /** Hardware keyboard / paste: digits, `.` `,` and `+ - * / x × ÷`; anything else is ignored. */
    fun type(char: Char): AmountExpression {
        val operator =
            when (char) {
                '+' -> AmountOperator.PLUS
                '-', '\u2212' -> AmountOperator.MINUS
                '*', 'x', 'X', '\u00D7' -> AmountOperator.TIMES
                '/', '\u00F7' -> AmountOperator.DIVIDE
                else -> null
            }
        return when {
            operator != null -> press(AmountKey.Operator(operator))
            char.isDigit() -> press(AmountKey.Digit(char.digitToInt()))
            char == '.' || char == ',' -> press(AmountKey.DecimalSeparator)
            else -> this
        }
    }

    private fun withCurrent(input: AmountInput) = copy(operands = operands.dropLast(1) + input)

    companion object {
        /** 999 999 999,99, the largest amount the keypad can type. */
        const val MAX_RESULT_MINOR = 99_999_999_999L

        /** Plenty for a receipt; keeps the line readable. */
        const val MAX_OPERATORS = 20

        /** A stored amount to continue from, e.g. "89" then "× 2" when editing. */
        fun of(minorUnits: Long) = AmountExpression(listOf(AmountInput.fromMinorUnits(minorUnits)))
    }
}

/** a × b, both in minor units, rounded half up; null past the limit. Operands are never negative. */
private fun multiply(
    a: Long,
    b: Long,
): Long? {
    if (b != 0L && a > (Long.MAX_VALUE - 50) / b) return null
    return ((a * b + 50) / 100).takeIf { it <= AmountExpression.MAX_RESULT_MINOR }
}

/** a ÷ b, both in minor units, rounded half up; null for b = 0 or past the limit. */
private fun divide(
    a: Long,
    b: Long,
): Long? {
    if (b == 0L || a > Long.MAX_VALUE / 100) return null
    return ((a * 100 + b / 2) / b).takeIf { it <= AmountExpression.MAX_RESULT_MINOR }
}
