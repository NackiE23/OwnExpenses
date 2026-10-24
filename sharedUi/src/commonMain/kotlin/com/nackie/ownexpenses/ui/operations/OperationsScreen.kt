package com.nackie.ownexpenses.ui.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.PeriodTotals
import com.nackie.ownexpenses.domain.model.TransactionDetails
import com.nackie.ownexpenses.presentation.format.MoneyFormatter
import com.nackie.ownexpenses.presentation.history.DayGroup
import com.nackie.ownexpenses.presentation.history.HistoryUiState
import com.nackie.ownexpenses.presentation.history.HistoryViewModel
import com.nackie.ownexpenses.presentation.quickentry.QuickEntryViewModel
import com.nackie.ownexpenses.ui.UiTestTags
import com.nackie.ownexpenses.ui.components.CategoryIconBadge
import com.nackie.ownexpenses.ui.format.monthYear
import com.nackie.ownexpenses.ui.format.rememberMoneyFormatter
import com.nackie.ownexpenses.ui.format.weekdayName
import com.nackie.ownexpenses.ui.period.PeriodSwitcher
import com.nackie.ownexpenses.ui.quickentry.QuickEntryHost
import com.nackie.ownexpenses.ui.quickentry.UndoSnackbarHost
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.category_with_subcategory
import com.nackie.ownexpenses.ui.resources.filter_no_matches
import com.nackie.ownexpenses.ui.resources.history_delete
import com.nackie.ownexpenses.ui.resources.history_empty
import com.nackie.ownexpenses.ui.resources.ic_delete
import com.nackie.ownexpenses.ui.resources.type_expenses
import com.nackie.ownexpenses.ui.resources.type_incomes
import com.nackie.ownexpenses.ui.theme.OwnExpensesTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Operations tab: records of the selected period grouped by day with day totals. Tap edits a
 * record, swipe left deletes it; both deletions are undone from the snackbar.
 */
@Composable
internal fun OperationsScreen(
    modifier: Modifier = Modifier,
    historyViewModel: HistoryViewModel = koinViewModel(),
    quickEntryViewModel: QuickEntryViewModel = koinViewModel(),
) {
    val state by historyViewModel.uiState.collectAsStateWithLifecycle()
    val filter by historyViewModel.filter.collectAsStateWithLifecycle()
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val formatter = rememberMoneyFormatter()

    Scaffold(
        modifier = modifier,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        OperationsContent(
            state = state,
            formatter = formatter,
            onPreviousPeriod = historyViewModel::showPreviousPeriod,
            onNextPeriod = historyViewModel::showNextPeriod,
            onSelectPeriod = historyViewModel::selectPeriod,
            onEdit = quickEntryViewModel::openForEdit,
            onDelete = quickEntryViewModel::deleteTransaction,
            filterActions =
                FilterBarActions(
                    query = filter.query,
                    onQueryChange = historyViewModel::setQuery,
                    onOpenFilter = { showFilter = true },
                    onRemoveCategory = historyViewModel::removeCategory,
                ),
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (showFilter) {
        OperationsFilterSheet(
            selectedIds = filter.categoryIds,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            onToggleCategory = historyViewModel::toggleCategory,
            onToggleLeaf = historyViewModel::toggleLeaf,
            onReset = historyViewModel::clearCategories,
            onDismiss = { showFilter = false },
        )
    }

    QuickEntryHost(snackbarHostState = snackbarHostState, formatter = formatter)
}

@Composable
private fun OperationsContent(
    state: HistoryUiState,
    formatter: MoneyFormatter,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onSelectPeriod: (Period) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    filterActions: FilterBarActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OperationsSearchBar(
                    query = filterActions.query,
                    categoriesFiltered = state.filter.categoryIds.isNotEmpty(),
                    onQueryChange = filterActions.onQueryChange,
                    onOpenFilter = filterActions.onOpenFilter,
                )
                CategoryFilterChips(
                    selectedIds = state.filter.categoryIds,
                    categories = state.expenseCategories + state.incomeCategories,
                    onOpenFilter = filterActions.onOpenFilter,
                    onRemove = filterActions.onRemoveCategory,
                )
                PeriodSwitcher(
                    period = state.period,
                    today = state.today,
                    onPrevious = onPreviousPeriod,
                    onNext = onNextPeriod,
                    onSelect = onSelectPeriod,
                )
                PeriodTotalsRow(totals = state.totals, formatter = formatter)
            }
        }
        if (!state.isLoading && state.days.isEmpty()) {
            item(key = "empty") {
                Text(
                    text =
                        stringResource(
                            if (state.filter.isActive) Res.string.filter_no_matches else Res.string.history_empty,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
        }
        state.days.forEach { day ->
            item(key = "day-${day.date}", contentType = "day") {
                DayHeader(day = day, formatter = formatter, modifier = Modifier.animateItem())
            }
            items(day.items, key = { it.transaction.id }, contentType = { "operation" }) { item ->
                SwipeToDeleteRow(onDelete = { onDelete(item.transaction.id) }, modifier = Modifier.animateItem()) {
                    OperationRow(item = item, formatter = formatter, onClick = { onEdit(item.transaction.id) })
                }
            }
        }
    }
}

/** Search text and the category filter chips' callbacks, bundled to keep the content signature short. */
private class FilterBarActions(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val onOpenFilter: () -> Unit,
    val onRemoveCategory: (CategoryWithSubcategories) -> Unit,
)

@Composable
private fun PeriodTotalsRow(
    totals: PeriodTotals,
    formatter: MoneyFormatter,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TotalLabel(
            label = stringResource(Res.string.type_expenses),
            amount = formatter.format(totals.expenseMinor),
            color = OwnExpensesTheme.moneyColors.expense,
        )
        TotalLabel(
            label = stringResource(Res.string.type_incomes),
            amount = formatter.format(totals.incomeMinor),
            color = OwnExpensesTheme.moneyColors.income,
        )
    }
}

@Composable
private fun TotalLabel(
    label: String,
    amount: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/** "24 | Субота, Жовтень 2026 | -2 134 ₴". */
@Composable
private fun DayHeader(
    day: DayGroup,
    formatter: MoneyFormatter,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(day.date.day.toString(), style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(weekdayName(day.date), style = MaterialTheme.typography.labelLarge)
                Text(
                    monthYear(day.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatter.formatSigned(day.netMinor),
                style = MaterialTheme.typography.titleMedium,
                color = amountColor(day.netMinor),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun OperationRow(
    item: TransactionDetails,
    formatter: MoneyFormatter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        item.subcategoryName?.let { stringResource(Res.string.category_with_subcategory, item.categoryName, it) }
            ?: item.categoryName
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(UiTestTags.HISTORY_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconBadge(icon = item.icon, color = Color(item.color))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.transaction.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatter.formatSigned(item.transaction.signedAmountMinor),
            style = MaterialTheme.typography.titleMedium,
            color = amountColor(item.transaction.signedAmountMinor),
        )
    }
}

/** Swipe from right to left deletes at once; the snackbar offers undo instead of a dialog. */
@Composable
private fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Plain remember on purpose: rememberSwipeToDismissBoxState() is saveable per list key, so a
    // record restored by undo would come back already "dismissed" and be deleted again.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it / 2 }) }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = stringResource(Res.string.history_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
    }
}

@Composable
private fun amountColor(signedMinor: Long): Color =
    when {
        signedMinor < 0 -> OwnExpensesTheme.moneyColors.expense
        signedMinor > 0 -> OwnExpensesTheme.moneyColors.income
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
