package com.nackie.ownexpenses.ui.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nackie.ownexpenses.domain.model.Period
import com.nackie.ownexpenses.presentation.format.MoneyFormatter
import com.nackie.ownexpenses.presentation.statistics.BarGranularity
import com.nackie.ownexpenses.presentation.statistics.CategoryShare
import com.nackie.ownexpenses.presentation.statistics.SpendingChart
import com.nackie.ownexpenses.presentation.statistics.StatisticsUiState
import com.nackie.ownexpenses.presentation.statistics.StatisticsViewModel
import com.nackie.ownexpenses.ui.components.CategoryIconBadge
import com.nackie.ownexpenses.ui.components.categoryContentColor
import com.nackie.ownexpenses.ui.format.percentLabel
import com.nackie.ownexpenses.ui.format.rememberMoneyFormatter
import com.nackie.ownexpenses.ui.period.PeriodSwitcher
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.ic_arrow_drop_down
import com.nackie.ownexpenses.ui.resources.stats_bar_chart_description
import com.nackie.ownexpenses.ui.resources.stats_bar_hint
import com.nackie.ownexpenses.ui.resources.stats_bar_value
import com.nackie.ownexpenses.ui.resources.stats_by_category
import com.nackie.ownexpenses.ui.resources.stats_by_day
import com.nackie.ownexpenses.ui.resources.stats_by_month
import com.nackie.ownexpenses.ui.resources.stats_collapse
import com.nackie.ownexpenses.ui.resources.stats_donut_description
import com.nackie.ownexpenses.ui.resources.stats_empty
import com.nackie.ownexpenses.ui.resources.stats_expand
import com.nackie.ownexpenses.ui.resources.stats_total
import com.nackie.ownexpenses.ui.resources.stats_without_subcategory
import com.nackie.ownexpenses.ui.theme.OwnExpensesTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Statistics tab: expenses of the selected period by day (or month) and by category. */
@Composable
internal fun StatisticsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsContent(
        state = state,
        formatter = rememberMoneyFormatter(),
        onPreviousPeriod = viewModel::showPreviousPeriod,
        onNextPeriod = viewModel::showNextPeriod,
        onSelectPeriod = viewModel::selectPeriod,
        modifier = modifier,
    )
}

@Composable
private fun StatisticsContent(
    state: StatisticsUiState,
    formatter: MoneyFormatter,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onSelectPeriod: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "period") {
            PeriodSwitcher(
                period = state.period,
                today = state.today,
                onPrevious = onPreviousPeriod,
                onNext = onNextPeriod,
                onSelect = onSelectPeriod,
            )
        }
        item(key = "total") {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.stats_total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatter.format(state.totalMinor),
                    style = MaterialTheme.typography.headlineMedium,
                    color = OwnExpensesTheme.moneyColors.expense,
                )
            }
        }
        if (!state.isLoading && state.totalMinor == 0L) {
            item(key = "empty") {
                Text(
                    text = stringResource(Res.string.stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
            return@LazyColumn
        }
        item(key = "bars") {
            BarsCard(chart = state.chart, formatter = formatter)
        }
        item(key = "donut") {
            DonutCard(shares = state.shares, totalMinor = state.totalMinor, formatter = formatter)
        }
        items(state.shares, key = { it.category.id }) { share ->
            CategoryShareRow(
                share = share,
                formatter = formatter,
                expanded = expandedCategoryId == share.category.id,
                onToggle = {
                    expandedCategoryId = if (expandedCategoryId == share.category.id) null else share.category.id
                },
            )
        }
    }
}

@Composable
private fun BarsCard(
    chart: SpendingChart,
    formatter: MoneyFormatter,
    modifier: Modifier = Modifier,
) {
    // A new period brings new bars, so the selection starts over.
    var selected by remember(chart) { mutableStateOf<Int?>(null) }
    val maxIndex = chart.bars.indices.maxByOrNull { chart.bars[it].totalMinor }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text =
                    stringResource(
                        if (chart.granularity ==
                            BarGranularity.DAY
                        ) {
                            Res.string.stats_by_day
                        } else {
                            Res.string.stats_by_month
                        },
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    selected?.let {
                        stringResource(
                            Res.string.stats_bar_value,
                            barLabel(chart, it),
                            formatter.format(chart.bars[it].totalMinor),
                        )
                    } ?: stringResource(Res.string.stats_bar_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpendingBarChart(
                chart = chart,
                selectedIndex = selected,
                onSelect = { selected = if (selected == it) null else it },
                description =
                    stringResource(
                        Res.string.stats_bar_chart_description,
                        maxIndex
                            ?.let {
                                barLabel(
                                    chart,
                                    it,
                                ) + " " + formatter.format(chart.bars[it].totalMinor)
                            }.orEmpty(),
                    ),
            )
        }
    }
}

@Composable
private fun DonutCard(
    shares: List<CategoryShare>,
    totalMinor: Long,
    formatter: MoneyFormatter,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.stats_by_category),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            // View-only state like the selected bar: which slice is shown in the centre.
            var selectedId by remember(shares) { mutableStateOf<Long?>(null) }
            val selected = shares.firstOrNull { it.category.id == selectedId }
            SpendingDonut(
                shares = shares.map { it.share to Color(it.category.color) },
                description = stringResource(Res.string.stats_donut_description),
                selectedIndex = selected?.let { shares.indexOf(it) },
                onSelect = { index -> selectedId = index?.let { shares[it].category.id }?.takeIf { it != selectedId } },
            ) {
                if (selected == null) {
                    Text(text = formatter.format(totalMinor), style = MaterialTheme.typography.titleMedium)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 36.dp),
                    ) {
                        Text(
                            text = selected.category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = percentLabel(selected.share),
                            style = MaterialTheme.typography.headlineSmall,
                            color = categoryContentColor(Color(selected.category.color)),
                        )
                        Text(
                            text = formatter.format(selected.totalMinor),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryShareRow(
    share: CategoryShare,
    formatter: MoneyFormatter,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canExpand = share.parts.isNotEmpty()
    val toggleLabel = stringResource(if (expanded) Res.string.stats_collapse else Res.string.stats_expand)
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canExpand, onClickLabel = toggleLabel, onClick = onToggle)
                    .heightIn(min = 56.dp)
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryIconBadge(icon = share.category.icon, color = Color(share.category.color), size = 40.dp)
            Text(
                text = share.category.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (canExpand) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            AmountWithPercent(amount = formatter.format(share.totalMinor), percent = percentLabel(share.share))
        }
        if (expanded) {
            share.parts.forEach { part ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = part.subcategory?.name ?: stringResource(Res.string.stats_without_subcategory),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    AmountWithPercent(amount = formatter.format(part.totalMinor), percent = percentLabel(part.share))
                }
            }
        }
    }
}

@Composable
private fun AmountWithPercent(
    amount: String,
    percent: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(text = amount, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = percent,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
