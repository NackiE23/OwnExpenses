package com.nackie.ownexpenses

import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nackie.ownexpenses.ui.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * History scenario from docs/TZ.md: tap edits a record, deleting from the edit panel removes it,
 * the snackbar undoes the deletion. Works on the app's real database: it adds its own record with a random amount,
 * finds it by that amount and deletes it at the end.
 */
@RunWith(AndroidJUnit4::class)
class HistoryFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun editThenDeleteThenUndo() {
        val amount = Random.nextInt(10, 100)
        val extraDigit = Random.nextInt(0, 10)
        val edited = amount * 10 + extraDigit

        addExpense(amount)
        compose.onNodeWithTag(UiTestTags.TAB_OPERATIONS).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { rowsWith(amount).isNotEmpty() }

        // Tap edits: the stored amount is pre-filled and can be extended.
        rows(amount)[0].performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { exists(UiTestTags.DELETE_BUTTON) }
        compose.onNodeWithTag(UiTestTags.AMOUNT).assertTextContains(amount.toString(), substring = true)
        compose.onNodeWithTag(UiTestTags.digitKey(extraDigit)).performClick()
        compose.onNodeWithTag(UiTestTags.ADD_BUTTON).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { rowsWith(edited).isNotEmpty() }

        // Deleting from the edit panel needs no confirmation; undo brings the record back.
        deleteFromEditPanel(edited)
        compose.waitUntil(TIMEOUT_MILLIS) { rowsWith(edited).isEmpty() }
        compose.onNodeWithTag(UiTestTags.UNDO).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { rowsWith(edited).isNotEmpty() }

        // Clean up: delete it for good.
        deleteFromEditPanel(edited)
        compose.waitUntil(TIMEOUT_MILLIS) { rowsWith(edited).isEmpty() }
    }

    private fun deleteFromEditPanel(amount: Int) {
        rows(amount)[0].performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { exists(UiTestTags.DELETE_BUTTON) }
        compose.onNodeWithTag(UiTestTags.DELETE_BUTTON).performClick()
    }

    private fun addExpense(amount: Int) {
        compose.waitUntil(TIMEOUT_MILLIS) { exists(UiTestTags.CATEGORY_TILE) }
        compose.onAllNodesWithTag(UiTestTags.CATEGORY_TILE)[0].performClick()
        amount.toString().forEach { compose.onNodeWithTag(UiTestTags.digitKey(it.digitToInt())).performClick() }
        compose.onNodeWithTag(UiTestTags.ADD_BUTTON).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { !exists(UiTestTags.AMOUNT) }
    }

    /** History rows showing exactly "-<amount> ₴" (minus sign, no-break space before the symbol). */
    private fun rows(amount: Int): SemanticsNodeInteractionCollection =
        compose.onAllNodes(hasTestTag(UiTestTags.HISTORY_ROW) and hasText("\u2212$amount\u00A0", substring = true))

    private fun rowsWith(amount: Int) = rows(amount).fetchSemanticsNodes()

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
