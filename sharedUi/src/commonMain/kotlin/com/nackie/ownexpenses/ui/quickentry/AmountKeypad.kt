package com.nackie.ownexpenses.ui.quickentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nackie.ownexpenses.domain.model.AmountKey
import com.nackie.ownexpenses.ui.UiTestTags
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.ic_backspace
import com.nackie.ownexpenses.ui.resources.quick_entry_add
import com.nackie.ownexpenses.ui.resources.quick_entry_delete_digit
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val KeyHeight = 56.dp
private val KeySpacing = 8.dp
private val KeyShape = RoundedCornerShape(16.dp)
private val DigitRows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))

/**
 * Keys on an even 3×4 grid, with a full-height "Add" key on the right:
 *
 * ```
 * 1 2 3 A
 * 4 5 6 d
 * 7 8 9 d
 * , 0 ⌫ .
 * ```
 */
@Composable
internal fun AmountKeypad(
    decimalSeparator: String,
    canSubmit: Boolean,
    onKey: (AmountKey) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
        Column(modifier = Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(KeySpacing)) {
            DigitRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
                    row.forEach { digit -> DigitKey(digit, onKey, Modifier.weight(1f)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
                KeypadKey(
                    onClick = { onKey(AmountKey.DecimalSeparator) },
                    modifier = Modifier.weight(1f).testTag(UiTestTags.DECIMAL_SEPARATOR_KEY),
                ) {
                    Text(decimalSeparator, style = MaterialTheme.typography.headlineSmall)
                }
                DigitKey(0, onKey, Modifier.weight(1f))
                KeypadKey(onClick = { onKey(AmountKey.Backspace) }, modifier = Modifier.weight(1f)) {
                    Icon(
                        painterResource(Res.drawable.ic_backspace),
                        stringResource(Res.string.quick_entry_delete_digit),
                    )
                }
            }
        }
        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            shape = KeyShape,
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .height(KeyHeight * 4 + KeySpacing * 3)
                    .testTag(UiTestTags.ADD_BUTTON),
        ) {
            Text(stringResource(Res.string.quick_entry_add), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun DigitKey(
    digit: Int,
    onKey: (AmountKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    KeypadKey(onClick = { onKey(AmountKey.Digit(digit)) }, modifier = modifier.testTag(UiTestTags.digitKey(digit))) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun KeypadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = KeyShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.height(KeyHeight),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}
