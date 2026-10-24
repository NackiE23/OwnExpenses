package com.nackie.ownexpenses.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.presentation.format.MoneyFormatter
import com.nackie.ownexpenses.presentation.home.CategoryTile
import com.nackie.ownexpenses.presentation.home.HomeUiState
import com.nackie.ownexpenses.presentation.home.HomeViewModel
import com.nackie.ownexpenses.presentation.quickentry.QuickEntryViewModel
import com.nackie.ownexpenses.ui.UiTestTags
import com.nackie.ownexpenses.ui.components.CategoryIconBadge
import com.nackie.ownexpenses.ui.format.rememberMoneyFormatter
import com.nackie.ownexpenses.ui.period.PeriodSwitcher
import com.nackie.ownexpenses.ui.quickentry.QuickEntryHost
import com.nackie.ownexpenses.ui.quickentry.UndoSnackbarHost
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.balance_title
import com.nackie.ownexpenses.ui.resources.home_add_category
import com.nackie.ownexpenses.ui.resources.home_edit_categories
import com.nackie.ownexpenses.ui.resources.home_no_categories
import com.nackie.ownexpenses.ui.resources.ic_add
import com.nackie.ownexpenses.ui.resources.ic_edit
import com.nackie.ownexpenses.ui.resources.type_expenses
import com.nackie.ownexpenses.ui.resources.type_incomes
import com.nackie.ownexpenses.ui.theme.OwnExpensesTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Main tab: balance, period, expense/income switch and category tiles that open quick entry. */
@Composable
internal fun HomeScreen(
    onManageCategories: (type: TransactionType, create: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = koinViewModel(),
    quickEntryViewModel: QuickEntryViewModel = koinViewModel(),
) {
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val formatter = rememberMoneyFormatter()

    Scaffold(
        modifier = modifier,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        HomeContent(
            state = state,
            formatter = formatter,
            onPreviousPeriod = homeViewModel::showPreviousPeriod,
            onNextPeriod = homeViewModel::showNextPeriod,
            onSelectPeriod = homeViewModel::selectPeriod,
            onSelectType = homeViewModel::selectType,
            onTileClick = quickEntryViewModel::open,
            onManageCategories = { create -> onManageCategories(state.selectedType, create) },
            modifier = Modifier.padding(innerPadding),
        )
    }

    QuickEntryHost(snackbarHostState = snackbarHostState, formatter = formatter)
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    formatter: MoneyFormatter,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onSelectPeriod: (Period) -> Unit,
    onSelectType: (TransactionType) -> Unit,
    onTileClick: (CategoryWithSubcategories) -> Unit,
    onManageCategories: (create: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    BalanceHeader(balanceMinor = state.balanceMinor, formatter = formatter)
                    IconButton(onClick = { onManageCategories(false) }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(painterResource(Res.drawable.ic_edit), stringResource(Res.string.home_edit_categories))
                    }
                }
                PeriodSwitcher(
                    period = state.period,
                    today = state.today,
                    onPrevious = onPreviousPeriod,
                    onNext = onNextPeriod,
                    onSelect = onSelectPeriod,
                )
                TypeSwitch(
                    selectedType = state.selectedType,
                    expenseMinor = state.totals.expenseMinor,
                    incomeMinor = state.totals.incomeMinor,
                    formatter = formatter,
                    onSelect = onSelectType,
                )
            }
        }
        if (!state.isLoading && state.tiles.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(Res.string.home_no_categories),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
        }
        items(state.tiles, key = { it.category.category.id }) { tile ->
            CategoryTileCard(
                tile = tile,
                formatter = formatter,
                onClick = { onTileClick(tile.category) },
                modifier = Modifier.animateItem(),
            )
        }
        if (!state.isLoading) {
            item(key = "add") { AddCategoryTile(onClick = { onManageCategories(true) }) }
        }
    }
}

@Composable
private fun BalanceHeader(
    balanceMinor: Long,
    formatter: MoneyFormatter,
    modifier: Modifier = Modifier,
) {
    val isNegative = balanceMinor < 0
    val color = if (isNegative) OwnExpensesTheme.moneyColors.negativeBalance else MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(Res.string.balance_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatter.format(balanceMinor),
            style = MaterialTheme.typography.displaySmall,
            color = color,
            modifier = Modifier.testTag(UiTestTags.BALANCE),
        )
    }
}

@Composable
private fun TypeSwitch(
    selectedType: TransactionType,
    expenseMinor: Long,
    incomeMinor: Long,
    formatter: MoneyFormatter,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(6.dp)
                .selectableGroup(),
    ) {
        TypeOption(
            label = stringResource(Res.string.type_expenses),
            amount = formatter.format(expenseMinor),
            amountColor = OwnExpensesTheme.moneyColors.expense,
            selected = selectedType == TransactionType.EXPENSE,
            onClick = { onSelect(TransactionType.EXPENSE) },
            modifier = Modifier.weight(1f),
        )
        TypeOption(
            label = stringResource(Res.string.type_incomes),
            amount = formatter.format(incomeMinor),
            amountColor = OwnExpensesTheme.moneyColors.income,
            selected = selectedType == TransactionType.INCOME,
            onClick = { onSelect(TransactionType.INCOME) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TypeOption(
    label: String,
    amount: String,
    amountColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                .selectable(selected = selected, onClick = onClick, role = Role.Tab)
                .heightIn(min = 64.dp)
                .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.titleLarge,
            color = if (selected) amountColor else amountColor.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryTileCard(
    tile: CategoryTile,
    formatter: MoneyFormatter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = tile.category.category
    val color = Color(category.color)
    OutlinedCard(onClick = onClick, modifier = modifier.testTag(UiTestTags.CATEGORY_TILE)) {
        Row(
            modifier = Modifier.padding(12.dp).heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconBadge(icon = category.icon, color = color)
            Column {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatter.format(tile.totalMinor),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Last tile: opens the categories screen with the editor for a new category of this type. */
@Composable
private fun AddCategoryTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.padding(12.dp).heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = null)
            }
            Text(
                text = stringResource(Res.string.home_add_category),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
