package com.nackie.ownexpenses.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nackie.ownexpenses.presentation.format.MoneyFormatter
import com.nackie.ownexpenses.presentation.home.CategoryDetails
import com.nackie.ownexpenses.presentation.statistics.SubcategoryShare
import com.nackie.ownexpenses.ui.components.CategoryIconBadge
import com.nackie.ownexpenses.ui.components.categoryContentColor
import com.nackie.ownexpenses.ui.format.percentLabel
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.details_edit
import com.nackie.ownexpenses.ui.resources.details_operations
import com.nackie.ownexpenses.ui.resources.details_operations_button
import com.nackie.ownexpenses.ui.resources.details_share
import com.nackie.ownexpenses.ui.resources.filter_without_subcategory
import com.nackie.ownexpenses.ui.resources.ic_edit
import com.nackie.ownexpenses.ui.resources.ic_tab_operations
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Opened by holding a tile: the category's period total, share and subcategories, with shortcuts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryDetailsSheet(
    details: CategoryDetails,
    formatter: MoneyFormatter,
    onEdit: () -> Unit,
    onShowOperations: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = details.category.category
    val color = Color(category.color)
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryIconBadge(icon = category.icon, color = color, size = 52.dp)
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ShareRow(
                title =
                    pluralStringResource(
                        Res.plurals.details_operations,
                        details.operationCount,
                        details.operationCount,
                    ),
                amount = formatter.format(details.totalMinor),
                share = details.shareOfPeriod,
                shareLabel = stringResource(Res.string.details_share, percentLabel(details.shareOfPeriod)),
                color = color,
            )
            details.parts.forEach { part ->
                PartRow(part = part, icon = category.icon, color = color, formatter = formatter)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(
                        painterResource(Res.drawable.ic_edit),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(Res.string.details_edit), modifier = Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(onClick = onShowOperations, modifier = Modifier.weight(1f)) {
                    Icon(
                        painterResource(Res.drawable.ic_tab_operations),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(Res.string.details_operations_button),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PartRow(
    part: SubcategoryShare,
    icon: String,
    color: Color,
    formatter: MoneyFormatter,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconBadge(icon = part.subcategory?.icon ?: icon, color = color, size = 36.dp)
        ShareRow(
            title = part.subcategory?.name ?: stringResource(Res.string.filter_without_subcategory),
            amount = formatter.format(part.totalMinor),
            share = part.share,
            shareLabel = percentLabel(part.share),
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Title and amount on one line, a bar of [share] and its label under them. */
@Composable
private fun ShareRow(
    title: String,
    amount: String,
    share: Float,
    shareLabel: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = amount, style = MaterialTheme.typography.titleMedium, color = categoryContentColor(color))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(
                progress = { share },
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = shareLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 48.dp),
            )
        }
    }
}
