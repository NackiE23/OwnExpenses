package com.nackie.ownexpenses.ui.quickentry

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nackie.ownexpenses.domain.model.AmountKey
import com.nackie.ownexpenses.presentation.format.MoneyFormatter
import com.nackie.ownexpenses.presentation.quickentry.QuickEntryUiState
import com.nackie.ownexpenses.ui.UiTestTags
import com.nackie.ownexpenses.ui.components.CategoryIconBadge
import com.nackie.ownexpenses.ui.format.entryDateLabel
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.dialog_cancel
import com.nackie.ownexpenses.ui.resources.dialog_ok
import com.nackie.ownexpenses.ui.resources.ic_calendar
import com.nackie.ownexpenses.ui.resources.ic_chevron_left
import com.nackie.ownexpenses.ui.resources.ic_chevron_right
import com.nackie.ownexpenses.ui.resources.ic_notes
import com.nackie.ownexpenses.ui.resources.quick_entry_add_description
import com.nackie.ownexpenses.ui.resources.quick_entry_amount
import com.nackie.ownexpenses.ui.resources.quick_entry_description
import com.nackie.ownexpenses.ui.resources.quick_entry_next_day
import com.nackie.ownexpenses.ui.resources.quick_entry_pick_date
import com.nackie.ownexpenses.ui.resources.quick_entry_previous_day
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Entry panel opened from a category tile. The keypad is live immediately; subcategory,
 * date and description are optional and never block "Add".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickEntrySheet(
    state: QuickEntryUiState,
    formatter: MoneyFormatter,
    onDismiss: () -> Unit,
    onKey: (AmountKey) -> Unit,
    onSubcategoryClick: (Long) -> Unit,
    onShiftDate: (Int) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onExpandDescription: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // No half-expanded state: the keypad must be fully visible at once.
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DateSelector(date = state.date, today = state.today, onShift = onShiftDate, onPick = onPickDate)
            CategoryHeader(name = state.category.name, icon = state.category.icon, color = Color(state.category.color))
            if (state.subcategories.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    state.subcategories.forEach { subcategory ->
                        FilterChip(
                            selected = subcategory.id == state.selectedSubcategoryId,
                            onClick = { onSubcategoryClick(subcategory.id) },
                            label = { Text(subcategory.name) },
                        )
                    }
                }
            }
            DescriptionField(
                expanded = state.isDescriptionExpanded,
                text = state.description,
                onExpand = onExpandDescription,
                onChange = onDescriptionChange,
            )
            val amountLabel = stringResource(Res.string.quick_entry_amount)
            Text(
                text = formatter.formatInput(state.amount),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                color =
                    if (state.amount.isEmpty) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.AMOUNT)
                        .semantics { contentDescription = amountLabel },
            )
            AmountKeypad(
                decimalSeparator = formatter.decimalSeparatorLabel,
                canSubmit = state.canSubmit,
                onKey = onKey,
                onSubmit = onSubmit,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelector(
    date: LocalDate,
    today: LocalDate,
    onShift: (Int) -> Unit,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) {
            Icon(painterResource(Res.drawable.ic_chevron_left), stringResource(Res.string.quick_entry_previous_day))
        }
        TextButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
            Icon(
                painter = painterResource(Res.drawable.ic_calendar),
                contentDescription = stringResource(Res.string.quick_entry_pick_date),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(entryDateLabel(date, today))
        }
        IconButton(onClick = { onShift(1) }) {
            Icon(painterResource(Res.drawable.ic_chevron_right), stringResource(Res.string.quick_entry_next_day))
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onPick(it.utcMillisToLocalDate()) }
                        showPicker = false
                    },
                ) { Text(stringResource(Res.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(Res.string.dialog_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun CategoryHeader(
    name: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconBadge(icon = icon, color = color, size = 36.dp)
        Text(text = name, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DescriptionField(
    expanded: Boolean,
    text: String,
    onExpand: () -> Unit,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expanded) {
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            label = { Text(stringResource(Res.string.quick_entry_description)) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        TextButton(onClick = onExpand, modifier = modifier.fillMaxWidth()) {
            Icon(painterResource(Res.drawable.ic_notes), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.quick_entry_add_description))
        }
    }
}

/** Material date pickers work with UTC midnight milliseconds. */
private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.utcMillisToLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
