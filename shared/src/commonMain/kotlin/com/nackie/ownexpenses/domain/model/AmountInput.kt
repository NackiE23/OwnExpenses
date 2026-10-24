package com.nackie.ownexpenses.domain.model

/** Key on the amount keypad. */
sealed interface AmountKey {
    data class Digit(
        val value: Int,
    ) : AmountKey {
        init {
            require(value in 0..9) { "Not a digit: $value" }
        }
    }

    data object DecimalSeparator : AmountKey

    data object Backspace : AmountKey
}

/**
 * Amount being typed on the keypad. Accepts both `.` and `,` as the decimal separator, at most
 * two decimal places and [MAX_INTEGER_DIGITS] whole digits; keys that would break these rules
 * are ignored, so the input is always a valid amount or empty.
 */
data class AmountInput(
    /** Whole part as typed, without leading zeros except a single "0". */
    val whole: String = "",
    /** Null until the separator is typed. */
    val fraction: String? = null,
) {
    val isEmpty: Boolean get() = whole.isEmpty() && fraction == null

    /** Value in minor units (kopiykas); 0 for empty input. */
    val minorUnits: Long
        get() {
            val wholeUnits = whole.toLongOrNull() ?: 0L
            val fractionUnits = fraction.orEmpty().padEnd(FRACTION_DIGITS, '0').toLong()
            return wholeUnits * 100 + fractionUnits
        }

    fun press(key: AmountKey): AmountInput =
        when (key) {
            is AmountKey.Digit -> {
                appendDigit(key.value)
            }

            AmountKey.DecimalSeparator -> {
                appendSeparator()
            }

            AmountKey.Backspace -> {
                when {
                    fraction == null -> copy(whole = whole.dropLast(1))
                    fraction.isEmpty() -> copy(fraction = null)
                    else -> copy(fraction = fraction.dropLast(1))
                }
            }
        }

    /** Hardware keyboard / paste: digits, `.` or `,`; anything else is ignored. */
    fun type(char: Char): AmountInput =
        when {
            char.isDigit() -> press(AmountKey.Digit(char.digitToInt()))
            char == '.' || char == ',' -> press(AmountKey.DecimalSeparator)
            else -> this
        }

    private fun appendSeparator(): AmountInput =
        when (fraction) {
            null -> copy(whole = whole.ifEmpty { "0" }, fraction = "")
            else -> this
        }

    private fun appendDigit(digit: Int): AmountInput =
        when {
            fraction != null -> if (fraction.length < FRACTION_DIGITS) copy(fraction = fraction + digit) else this
            whole == "0" -> copy(whole = digit.toString())
            whole.length < MAX_INTEGER_DIGITS -> copy(whole = whole + digit)
            else -> this
        }

    companion object {
        const val FRACTION_DIGITS = 2

        /** Up to 999 999 999.99, far above any personal expense and safe for Long minor units. */
        const val MAX_INTEGER_DIGITS = 9
    }
}
