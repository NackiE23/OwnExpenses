package com.nackie.ownexpenses

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nackie.ownexpenses.domain.model.AmountOperator
import com.nackie.ownexpenses.ui.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The core scenario from docs/TZ.md: an expense takes three actions (tile, amount, Add),
 * shows up in the balance at once and is undone from the snackbar. Works on the app's real
 * database in any language: it only compares the balance before and after.
 */
@RunWith(AndroidJUnit4::class)
class QuickEntryFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun recordExpenseInThreeActionsThenUndo() {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(UiTestTags.CATEGORY_TILE).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val balanceBefore = balanceText()

        // 1. Category tile.
        compose.onAllNodesWithTag(UiTestTags.CATEGORY_TILE)[0].performClick()
        // 2. Amount.
        listOf(1, 2, 5).forEach { compose.onNodeWithTag(UiTestTags.digitKey(it)).performClick() }
        compose.onNodeWithTag(UiTestTags.AMOUNT).assertTextContains("125", substring = true)
        // 3. Add.
        compose.onNodeWithTag(UiTestTags.ADD_BUTTON).performClick()

        compose.waitUntil(TIMEOUT_MILLIS) { balanceText() != balanceBefore }
        compose.onNodeWithTag(UiTestTags.AMOUNT).assertDoesNotExist()

        compose.onNodeWithTag(UiTestTags.UNDO).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { balanceText() == balanceBefore }
    }

    @Test
    fun keypadCalculatesAnExpression() {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(UiTestTags.CATEGORY_TILE).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag(UiTestTags.CATEGORY_TILE)[0].performClick()

        // 120 + 35 × 2 = 190: multiplication first.
        listOf(1, 2, 0).forEach { compose.onNodeWithTag(UiTestTags.digitKey(it)).performClick() }
        compose.onNodeWithTag(UiTestTags.operatorKey(AmountOperator.PLUS.name)).performClick()
        listOf(3, 5).forEach { compose.onNodeWithTag(UiTestTags.digitKey(it)).performClick() }
        compose.onNodeWithTag(UiTestTags.operatorKey(AmountOperator.TIMES.name)).performClick()
        compose.onNodeWithTag(UiTestTags.digitKey(2)).performClick()

        compose.onNodeWithTag(UiTestTags.AMOUNT).assertTextContains("190", substring = true)
        compose.onNodeWithTag(UiTestTags.ADD_BUTTON).assertIsEnabled()

        // Division by zero cannot be saved.
        compose.onNodeWithTag(UiTestTags.operatorKey(AmountOperator.DIVIDE.name)).performClick()
        compose.onNodeWithTag(UiTestTags.digitKey(0)).performClick()
        compose.onNodeWithTag(UiTestTags.ADD_BUTTON).assertIsNotEnabled()
    }

    private fun balanceText(): String =
        compose
            .onNodeWithTag(UiTestTags.BALANCE)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString { it.text }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
