package com.nackie.ownexpenses.ui.period

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodKind
import com.nackie.ownexpenses.ui.format.periodLabel
import com.nackie.ownexpenses.ui.format.toLocalDateFromUtcMillis
import com.nackie.ownexpenses.ui.format.toUtcMillis
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.dialog_cancel
import com.nackie.ownexpenses.ui.resources.dialog_ok
import com.nackie.ownexpenses.ui.resources.ic_chevron_left
import com.nackie.ownexpenses.ui.resources.ic_chevron_right
import com.nackie.ownexpenses.ui.resources.period_all_time
import com.nackie.ownexpenses.ui.resources.period_kind_custom
import com.nackie.ownexpenses.ui.resources.period_kind_day
import com.nackie.ownexpenses.ui.resources.period_kind_month
import com.nackie.ownexpenses.ui.resources.period_kind_week
import com.nackie.ownexpenses.ui.resources.period_kind_year
import com.nackie.ownexpenses.ui.resources.period_next
import com.nackie.ownexpenses.ui.resources.period_pick
import com.nackie.ownexpenses.ui.resources.period_picker_title
import com.nackie.ownexpenses.ui.resources.period_previous
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Arrows step to the previous/next period of the same kind; tapping the label opens the
 * picker. The one period selection is shared by the main screen, history and statistics.
 */
@Composable
internal fun PeriodSwitcher(
    period: Period,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val canShift = period != Period.AllTime

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = canShift) {
            Icon(painterResource(Res.drawable.ic_chevron_left), stringResource(Res.string.period_previous))
        }
        TextButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
            // Slides in the direction of the step, so arrows and swipes show where the period went.
            AnimatedContent(
                targetState = period,
                transitionSpec = {
                    val forward = targetState.start > initialState.start
                    (slideInHorizontally { if (forward) it / 2 else -it / 2 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 2 else it / 2 } + fadeOut())
                },
                label = "period",
            ) { shown ->
                Text(
                    text = periodLabel(shown),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        IconButton(onClick = onNext, enabled = canShift) {
            Icon(painterResource(Res.drawable.ic_chevron_right), stringResource(Res.string.period_next))
        }
    }

    if (showPicker) {
        PeriodPickerDialog(
            current = period,
            today = today,
            onSelect = {
                showPicker = false
                onSelect(it)
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** Day, week (Monday–Sunday), month, year — each around today — all time, or a custom range. */
@Composable
internal fun PeriodPickerDialog(
    current: Period,
    today: LocalDate,
    onSelect: (Period) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickRange by rememberSaveable { mutableStateOf(false) }

    if (pickRange) {
        DateRangePickerDialog(
            initial = current as? Period.Custom,
            onConfirm = onSelect,
            onDismiss = { pickRange = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.period_picker_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                PeriodKind.entries.forEach { kind ->
                    RadioRow(
                        label = stringResource(kind.label),
                        selected = current.kind == kind,
                        onClick = {
                            if (kind == PeriodKind.CUSTOM) pickRange = true else onSelect(Period.of(kind, today))
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

/** Material range picker; both ends must be chosen before confirming. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRangePickerDialog(
    initial: Period.Custom?,
    onConfirm: (Period.Custom) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = initial?.start?.toUtcMillis(),
            initialSelectedEndDateMillis = initial?.endInclusive?.toUtcMillis(),
        )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (start != null && end != null) {
                        onConfirm(Period.Custom(start.toLocalDateFromUtcMillis(), end.toLocalDateFromUtcMillis()))
                    }
                },
                enabled = start != null && end != null,
            ) { Text(stringResource(Res.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    ) {
        DateRangePicker(
            state = state,
            title = {
                Text(
                    stringResource(Res.string.period_pick),
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

private val PeriodKind.label: StringResource
    get() =
        when (this) {
            PeriodKind.DAY -> Res.string.period_kind_day
            PeriodKind.WEEK -> Res.string.period_kind_week
            PeriodKind.MONTH -> Res.string.period_kind_month
            PeriodKind.YEAR -> Res.string.period_kind_year
            PeriodKind.ALL_TIME -> Res.string.period_all_time
            PeriodKind.CUSTOM -> Res.string.period_kind_custom
        }
