package com.nackie.ownexpenses.ui.quickentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nackie.ownexpenses.domain.model.AmountKey
import com.nackie.ownexpenses.domain.model.AmountOperator
import com.nackie.ownexpenses.presentation.format.symbol
import com.nackie.ownexpenses.ui.UiTestTags
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.ic_add_comment
import com.nackie.ownexpenses.ui.resources.ic_backspace
import com.nackie.ownexpenses.ui.resources.quick_entry_add_description
import com.nackie.ownexpenses.ui.resources.quick_entry_delete_digit
import com.nackie.ownexpenses.ui.resources.quick_entry_divide
import com.nackie.ownexpenses.ui.resources.quick_entry_minus
import com.nackie.ownexpenses.ui.resources.quick_entry_plus
import com.nackie.ownexpenses.ui.resources.quick_entry_times
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val KeyHeight = 64.dp
private val KeySpacing = 8.dp
private val KeyShape = RoundedCornerShape(16.dp)
private const val COLUMNS = 5

/** Digits run 7-8-9 down to 1-2-3, as on a calculator; each row starts with its operator. */
private val DigitRows =
    listOf(
        AmountOperator.DIVIDE to listOf(7, 8, 9),
        AmountOperator.TIMES to listOf(4, 5, 6),
        AmountOperator.MINUS to listOf(1, 2, 3),
    )

/**
 * Calculator layout from the design reference on an even 5×4 grid: operators on the left,
 * backspace and description on the right, and a double-height "Add" key below them.
 *
 * ```
 * ÷ 7 8 9 ⌫
 * × 4 5 6 ✎
 * − 1 2 3 A
 * + 0 0 , A
 * ```
 */
@Composable
internal fun AmountKeypad(
    decimalSeparator: String,
    submitLabel: String,
    canSubmit: Boolean,
    hasDescription: Boolean,
    onKey: (AmountKey) -> Unit,
    onDescription: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cell = (maxWidth - KeySpacing * (COLUMNS - 1)) / COLUMNS
        val cellModifier = Modifier.width(cell)
        Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
            Column(verticalArrangement = Arrangement.spacedBy(KeySpacing)) {
                DigitRows.forEach { (operator, digits) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
                        OperatorKey(operator, onKey, cellModifier)
                        digits.forEach { digit -> DigitKey(digit, onKey, cellModifier) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
                    OperatorKey(AmountOperator.PLUS, onKey, cellModifier)
                    DigitKey(0, onKey, Modifier.width(span(cell, 2)))
                    KeypadKey(
                        onClick = { onKey(AmountKey.DecimalSeparator) },
                        modifier = cellModifier.testTag(UiTestTags.DECIMAL_SEPARATOR_KEY),
                    ) {
                        Text(decimalSeparator, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(KeySpacing)) {
                KeypadKey(onClick = { onKey(AmountKey.Backspace) }, modifier = cellModifier) {
                    Icon(
                        painterResource(Res.drawable.ic_backspace),
                        stringResource(Res.string.quick_entry_delete_digit),
                    )
                }
                KeypadKey(
                    onClick = onDescription,
                    color =
                        if (hasDescription) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    modifier = cellModifier.testTag(UiTestTags.DESCRIPTION_KEY),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_add_comment),
                        stringResource(Res.string.quick_entry_add_description),
                    )
                }
                Button(
                    onClick = onSubmit,
                    enabled = canSubmit,
                    shape = KeyShape,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier =
                        cellModifier
                            .height(span(KeyHeight, 2))
                            .testTag(UiTestTags.ADD_BUTTON),
                ) {
                    Text(submitLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/** Size of a key spanning [count] grid cells, gaps between them included. */
private fun span(
    cell: Dp,
    count: Int,
): Dp = cell * count + KeySpacing * (count - 1)

@Composable
private fun DigitKey(
    digit: Int,
    onKey: (AmountKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    KeypadKey(onClick = { onKey(AmountKey.Digit(digit)) }, modifier = modifier.testTag(UiTestTags.digitKey(digit))) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun OperatorKey(
    operator: AmountOperator,
    onKey: (AmountKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(operator.description)
    KeypadKey(
        onClick = { onKey(AmountKey.Operator(operator)) },
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier =
            modifier
                .testTag(
                    UiTestTags.operatorKey(operator.name),
                ).semantics { contentDescription = description },
    ) {
        Text(operator.symbol.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun KeypadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = KeyShape,
        color = color,
        modifier = modifier.height(KeyHeight),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

private val AmountOperator.description: StringResource
    get() =
        when (this) {
            AmountOperator.PLUS -> Res.string.quick_entry_plus
            AmountOperator.MINUS -> Res.string.quick_entry_minus
            AmountOperator.TIMES -> Res.string.quick_entry_times
            AmountOperator.DIVIDE -> Res.string.quick_entry_divide
        }
