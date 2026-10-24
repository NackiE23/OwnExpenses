package com.nackie.ownexpenses.ui

/** Stable, language-independent handles for UI tests. */
object UiTestTags {
    const val BALANCE = "balance"
    const val CATEGORY_TILE = "category_tile"
    const val AMOUNT = "amount"
    const val ADD_BUTTON = "add_button"
    const val UNDO = "undo"
    const val DELETE_BUTTON = "delete_button"
    const val HISTORY_ROW = "history_row"

    const val TAB_CATEGORIES = "tab_categories"
    const val TAB_OPERATIONS = "tab_operations"
    const val TAB_STATISTICS = "tab_statistics"
    const val TAB_MORE = "tab_more"

    fun digitKey(digit: Int): String = "key_$digit"

    const val DECIMAL_SEPARATOR_KEY = "key_separator"
}
