package com.nackie.ownexpenses.ui

/** Stable, language-independent handles for UI tests. */
object UiTestTags {
    const val BALANCE = "balance"
    const val CATEGORY_TILE = "category_tile"
    const val AMOUNT = "amount"
    const val ADD_BUTTON = "add_button"
    const val UNDO = "undo"

    fun digitKey(digit: Int): String = "key_$digit"

    const val DECIMAL_SEPARATOR_KEY = "key_separator"
}
