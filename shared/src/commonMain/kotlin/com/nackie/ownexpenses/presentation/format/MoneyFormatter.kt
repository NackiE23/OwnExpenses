package com.nackie.ownexpenses.presentation.format

import com.nackie.ownexpenses.domain.model.AmountExpression
import com.nackie.ownexpenses.domain.model.AmountInput
import com.nackie.ownexpenses.domain.model.AmountOperator
import kotlin.math.absoluteValue

/**
 * Formats amounts for display: grouped thousands, kopiykas only when non-zero, currency
 * symbol after the number ("2 086 ₴", "-150,50 ₴"). Separators come from the UI locale.
 */
class MoneyFormatter(
    private val decimalSeparator: Char,
    private val groupingSeparator: Char,
    private val currencySymbol: String = UAH_SYMBOL,
) {
    fun format(minorUnits: Long): String {
        val sign = if (minorUnits < 0) MINUS_SIGN else ""
        val absolute = minorUnits.absoluteValue
        val whole = group((absolute / 100).toString())
        val fraction = absolute % 100
        val fractionPart = if (fraction == 0L) "" else decimalSeparator + fraction.toString().padStart(2, '0')
        return "$sign$whole$fractionPart$NO_BREAK_SPACE$currencySymbol"
    }

    /** Like [format], with an explicit plus for positive amounts ("+1 000 ₴", "-89 ₴"). */
    fun formatSigned(minorUnits: Long): String = if (minorUnits > 0) "+" + format(minorUnits) else format(minorUnits)

    /** Amount as typed on the keypad, keeping a trailing separator or zeros ("12,", "12,50"). */
    fun formatInput(input: AmountInput): String = "${formatNumber(input)}$NO_BREAK_SPACE$currencySymbol"

    /**
     * Expression as typed, without the currency symbol ("120 + 35 × 2", "1 200 −"); the result
     * line carries the symbol. A plain number looks like [formatInput].
     */
    fun formatExpression(expression: AmountExpression): String {
        if (!expression.hasOperators) return formatInput(expression.operands.single())
        return buildString {
            expression.operands.forEachIndexed { index, operand ->
                if (index > 0) append(' ').append(expression.operators[index - 1].symbol).append(' ')
                if (!operand.isEmpty) append(formatNumber(operand))
            }
        }.trimEnd()
    }

    private fun formatNumber(input: AmountInput): String {
        val whole = group(input.whole.ifEmpty { "0" })
        val fraction = input.fraction?.let { decimalSeparator + it }.orEmpty()
        return "$whole$fraction"
    }

    val decimalSeparatorLabel: String get() = decimalSeparator.toString()

    private fun group(digits: String): String =
        digits
            .reversed()
            .chunked(3)
            .joinToString(groupingSeparator.toString())
            .reversed()

    companion object {
        const val UAH_SYMBOL = "₴"
        private const val MINUS_SIGN = "\u2212"
        private const val NO_BREAK_SPACE = '\u00A0'

        /** Ukrainian conventions: decimal comma, (no-break) space between thousands. */
        val Ukrainian = MoneyFormatter(decimalSeparator = ',', groupingSeparator = NO_BREAK_SPACE)

        val English = MoneyFormatter(decimalSeparator = '.', groupingSeparator = ',')

        fun forLanguage(languageCode: String): MoneyFormatter = if (languageCode == "uk") Ukrainian else English
    }
}

/** Symbols as shown on the keypad and in the expression line. */
val AmountOperator.symbol: Char
    get() =
        when (this) {
            AmountOperator.PLUS -> '+'
            AmountOperator.MINUS -> '\u2212'
            AmountOperator.TIMES -> '\u00D7'
            AmountOperator.DIVIDE -> '\u00F7'
        }
