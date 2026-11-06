package com.nackie.ownexpenses.ui.quickentry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nackie.ownexpenses.domain.model.AmountExpression
import com.nackie.ownexpenses.domain.model.AmountKey
import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.presentation.format.MoneyFormatter
import com.nackie.ownexpenses.presentation.quickentry.QuickEntryUiState
import com.nackie.ownexpenses.presentation.quickentry.QuickEntryViewModel
import com.nackie.ownexpenses.ui.UiTestTags
import com.nackie.ownexpenses.ui.components.CategoryIconBadge
import com.nackie.ownexpenses.ui.format.entryDateLabel
import com.nackie.ownexpenses.ui.format.toLocalDateFromUtcMillis
import com.nackie.ownexpenses.ui.format.toUtcMillis
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.dialog_cancel
import com.nackie.ownexpenses.ui.resources.dialog_ok
import com.nackie.ownexpenses.ui.resources.ic_arrow_drop_down
import com.nackie.ownexpenses.ui.resources.ic_calendar
import com.nackie.ownexpenses.ui.resources.ic_chevron_left
import com.nackie.ownexpenses.ui.resources.ic_chevron_right
import com.nackie.ownexpenses.ui.resources.ic_delete
import com.nackie.ownexpenses.ui.resources.ic_notes
import com.nackie.ownexpenses.ui.resources.quick_entry_add
import com.nackie.ownexpenses.ui.resources.quick_entry_add_description
import com.nackie.ownexpenses.ui.resources.quick_entry_amount
import com.nackie.ownexpenses.ui.resources.quick_entry_cannot_calculate
import com.nackie.ownexpenses.ui.resources.quick_entry_change_category
import com.nackie.ownexpenses.ui.resources.quick_entry_delete
import com.nackie.ownexpenses.ui.resources.quick_entry_description
import com.nackie.ownexpenses.ui.resources.quick_entry_edit_title
import com.nackie.ownexpenses.ui.resources.quick_entry_next_day
import com.nackie.ownexpenses.ui.resources.quick_entry_pick_date
import com.nackie.ownexpenses.ui.resources.quick_entry_previous_day
import com.nackie.ownexpenses.ui.resources.quick_entry_result
import com.nackie.ownexpenses.ui.resources.quick_entry_save
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows the entry panel while [QuickEntryViewModel] has one open and turns its events into
 * snackbars. Screens share the ViewModel instance with this host through their
 * ViewModelStoreOwner and only call `open`, `openForEdit` or `deleteTransaction` on it.
 */
@Composable
internal fun QuickEntryHost(
    snackbarHostState: SnackbarHostState,
    formatter: MoneyFormatter,
    viewModel: QuickEntryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    QuickEntryEventsEffect(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onUndoAdd = viewModel::undoAdd,
        onUndoDelete = viewModel::undoDelete,
    )

    state?.let { entry ->
        QuickEntrySheet(
            state = entry,
            formatter = formatter,
            onDismiss = viewModel::close,
            onKey = viewModel::onKey,
            onSubcategoryClick = viewModel::onSubcategoryClick,
            onSelectCategory = viewModel::selectCategory,
            onShiftDate = viewModel::shiftDate,
            onPickDate = viewModel::setDate,
            onOpenDescription = viewModel::openDescriptionDialog,
            onDismissDescription = viewModel::dismissDescriptionDialog,
            onSaveDescription = viewModel::saveDescription,
            onSubmit = viewModel::submit,
            onDelete = viewModel::deleteEdited,
        )
    }
}

/**
 * Entry panel for adding (from a category tile) or editing a record. The keypad is live
 * immediately; subcategory, date and description are optional and never block saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEntrySheet(
    state: QuickEntryUiState,
    formatter: MoneyFormatter,
    onDismiss: () -> Unit,
    onKey: (AmountKey) -> Unit,
    onSubcategoryClick: (Long) -> Unit,
    onSelectCategory: (Long) -> Unit,
    onShiftDate: (Int) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onOpenDescription: () -> Unit,
    onDismissDescription: () -> Unit,
    onSaveDescription: (String) -> Unit,
    onSubmit: () -> Unit,
    onDelete: () -> Unit,
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
            if (state.isEditing) EditHeader(onDelete = onDelete)
            DateSelector(date = state.date, today = state.today, onShift = onShiftDate, onPick = onPickDate)
            CategoryHeader(category = state.category, choices = state.categoryChoices, onSelect = onSelectCategory)
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
            if (state.description.isNotBlank()) DescriptionLine(text = state.description, onClick = onOpenDescription)
            AmountDisplay(amount = state.amount, formatter = formatter)
            AmountKeypad(
                decimalSeparator = formatter.decimalSeparatorLabel,
                submitLabel =
                    stringResource(
                        if (state.isEditing) Res.string.quick_entry_save else Res.string.quick_entry_add,
                    ),
                canSubmit = state.canSubmit,
                hasDescription = state.description.isNotBlank(),
                onKey = onKey,
                onDescription = onOpenDescription,
                onSubmit = onSubmit,
            )
        }
    }
    if (state.isDescriptionDialogOpen) {
        DescriptionDialog(initialText = state.description, onDismiss = onDismissDescription, onSave = onSaveDescription)
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
                        pickerState.selectedDateMillis?.let { onPick(it.toLocalDateFromUtcMillis()) }
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

/**
 * The amount as typed. With operators, the expression goes on a smaller line and the result
 * that will be saved below it, in red when it cannot be saved (not positive, or not computable).
 * The fixed minimum height keeps the keypad from jumping when the first operator appears.
 */
@Composable
private fun AmountDisplay(
    amount: AmountExpression,
    formatter: MoneyFormatter,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.quick_entry_amount)
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().heightIn(min = AmountDisplayMinHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val resultModifier = Modifier.testTag(UiTestTags.AMOUNT).semantics { contentDescription = label }
        if (!amount.hasOperators) {
            Text(
                text = formatter.formatExpression(amount),
                style = MaterialTheme.typography.displayMedium,
                color = if (amount.isEmpty) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
                modifier = resultModifier,
            )
            return@Column
        }
        Text(
            text = formatter.formatExpression(amount),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        val result = amount.result
        Text(
            text =
                if (result == null) {
                    stringResource(Res.string.quick_entry_cannot_calculate)
                } else {
                    stringResource(Res.string.quick_entry_result, formatter.format(result))
                },
            style = MaterialTheme.typography.displaySmall,
            color = if (result == null || result <= 0) colors.error else colors.onSurface,
            maxLines = 1,
            modifier = resultModifier,
        )
    }
}

private val AmountDisplayMinHeight = 96.dp

/** Title row shown while editing: what the panel does, and the delete action. */
@Composable
private fun EditHeader(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.quick_entry_edit_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag(UiTestTags.DELETE_BUTTON),
        ) {
            Icon(painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.quick_entry_delete))
        }
    }
}

/** Category of the record; while editing it opens a menu of other categories of the same type. */
@Composable
private fun CategoryHeader(
    category: Category,
    choices: List<CategoryWithSubcategories>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val canChange = choices.size > 1
    val changeLabel = stringResource(Res.string.quick_entry_change_category)

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = canChange, onClickLabel = changeLabel) { expanded = true }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconBadge(icon = category.icon, color = Color(category.color), size = 36.dp)
            Text(text = category.name, style = MaterialTheme.typography.titleLarge)
            if (canChange) Icon(painterResource(Res.drawable.ic_arrow_drop_down), contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.category.name) },
                    leadingIcon = {
                        CategoryIconBadge(
                            icon = choice.category.icon,
                            color = Color(choice.category.color),
                            size = 28.dp,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(choice.category.id)
                    },
                )
            }
        }
    }
}

/** The saved description in place of a field; tapping it opens the dialog to change it. */
@Composable
private fun DescriptionLine(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.quick_entry_description)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = label, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_notes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(UiTestTags.DESCRIPTION),
        )
    }
}

/**
 * Description editor opened from the keypad. The field takes focus as soon as the dialog window
 * does, so the system keyboard is up without another tap.
 */
@Composable
private fun DescriptionDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.quick_entry_description)) },
        text = {
            val focusRequester = remember { FocusRequester() }
            val windowInfo = LocalWindowInfo.current
            LaunchedEffect(focusRequester) {
                // Focus requested before the dialog window is focused does not bring up the keyboard.
                snapshotFlow { windowInfo.isWindowFocused }.first { it }
                focusRequester.requestFocus()
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(stringResource(Res.string.quick_entry_add_description)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(value.text) }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag(UiTestTags.DESCRIPTION_FIELD),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.text) }, modifier = Modifier.testTag(UiTestTags.DESCRIPTION_SAVE)) {
                Text(stringResource(Res.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
        },
    )
}
