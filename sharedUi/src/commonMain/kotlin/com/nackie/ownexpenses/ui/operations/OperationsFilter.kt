package com.nackie.ownexpenses.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nackie.ownexpenses.domain.leafIds
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.ui.components.CategoryIconBadge
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.filter_archived
import com.nackie.ownexpenses.ui.resources.filter_clear_search
import com.nackie.ownexpenses.ui.resources.filter_done
import com.nackie.ownexpenses.ui.resources.filter_hint
import com.nackie.ownexpenses.ui.resources.filter_open
import com.nackie.ownexpenses.ui.resources.filter_remove
import com.nackie.ownexpenses.ui.resources.filter_reset
import com.nackie.ownexpenses.ui.resources.filter_search_hint
import com.nackie.ownexpenses.ui.resources.filter_title
import com.nackie.ownexpenses.ui.resources.filter_without_subcategory
import com.nackie.ownexpenses.ui.resources.ic_close
import com.nackie.ownexpenses.ui.resources.ic_filter
import com.nackie.ownexpenses.ui.resources.ic_search
import com.nackie.ownexpenses.ui.resources.type_expenses
import com.nackie.ownexpenses.ui.resources.type_incomes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Description search plus the button that opens the category filter; tinted while categories are chosen. */
@Composable
internal fun OperationsSearchBar(
    query: String,
    categoriesFiltered: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(Res.string.filter_search_hint)) },
            leadingIcon = { Icon(painterResource(Res.drawable.ic_search), contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.filter_clear_search))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenFilter) {
            Icon(
                painter = painterResource(Res.drawable.ic_filter),
                contentDescription = stringResource(Res.string.filter_open),
                tint = if (categoriesFiltered) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
    }
}

/** One chip per category with something chosen; tapping opens the filter, the cross removes it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryFilterChips(
    selectedIds: Set<Long>,
    categories: List<CategoryWithSubcategories>,
    onOpenFilter: () -> Unit,
    onRemove: (CategoryWithSubcategories) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = categories.filter { group -> group.leafIds().any { it in selectedIds } }
    if (chosen.isEmpty()) return
    val withoutSubcategory = stringResource(Res.string.filter_without_subcategory)
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chosen.forEach { group ->
            val label =
                if (selectedIds.containsAll(group.leafIds())) {
                    group.category.name
                } else {
                    val parts =
                        listOfNotNull(withoutSubcategory.takeIf { group.category.id in selectedIds }) +
                            group.subcategories.filter { it.id in selectedIds }.map { it.name }
                    group.category.name + ": " + parts.joinToString()
                }
            InputChip(
                selected = true,
                onClick = onOpenFilter,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    CategoryIconBadge(
                        icon = group.category.icon,
                        color = Color(group.category.color),
                        size = 24.dp,
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { onRemove(group) }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.filter_remove, label),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

/**
 * Category checklist. A category row switches it with all its subcategories; under a category with
 * subcategories, "Without subcategory" and each subcategory switch on their own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OperationsFilterSheet(
    selectedIds: Set<Long>,
    expenseCategories: List<CategoryWithSubcategories>,
    incomeCategories: List<CategoryWithSubcategories>,
    onToggleCategory: (CategoryWithSubcategories) -> Unit,
    onToggleLeaf: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        val actions = FilterActions(onToggleCategory, onToggleLeaf)
        val expenses = stringResource(Res.string.type_expenses)
        val incomes = stringResource(Res.string.type_incomes)
        LazyColumn(contentPadding = PaddingValues(bottom = 8.dp), modifier = Modifier.weight(1f, fill = false)) {
            item(key = "title") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(Res.string.filter_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(Res.string.filter_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            categorySection(expenses, expenseCategories, selectedIds, actions)
            categorySection(incomes, incomeCategories, selectedIds, actions)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.filter_reset))
            }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.filter_done))
            }
        }
    }
}

private class FilterActions(
    val onToggleCategory: (CategoryWithSubcategories) -> Unit,
    val onToggleLeaf: (Long) -> Unit,
)

private fun LazyListScope.categorySection(
    title: String,
    categories: List<CategoryWithSubcategories>,
    selectedIds: Set<Long>,
    actions: FilterActions,
) {
    if (categories.isEmpty()) return
    item(key = "section-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(categories, key = { "category-${it.category.id}" }) { group ->
        CategoryFilterGroup(group = group, selectedIds = selectedIds, actions = actions)
    }
}

@Composable
private fun CategoryFilterGroup(
    group: CategoryWithSubcategories,
    selectedIds: Set<Long>,
    actions: FilterActions,
    modifier: Modifier = Modifier,
) {
    val leaves = group.leafIds()
    val state =
        when {
            selectedIds.containsAll(leaves) -> ToggleableState.On
            leaves.any { it in selectedIds } -> ToggleableState.Indeterminate
            else -> ToggleableState.Off
        }
    val archived = stringResource(Res.string.filter_archived)
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .triStateToggleable(
                        state = state,
                        role = Role.Checkbox,
                        onClick = { actions.onToggleCategory(group) },
                    ).heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TriStateCheckbox(state = state, onClick = null)
            CategoryIconBadge(icon = group.category.icon, color = Color(group.category.color), size = 32.dp)
            Text(
                text = group.category.name + if (group.category.isArchived) " ($archived)" else "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (group.subcategories.isNotEmpty()) {
            LeafRow(
                label = stringResource(Res.string.filter_without_subcategory),
                checked = group.category.id in selectedIds,
                onToggle = { actions.onToggleLeaf(group.category.id) },
            )
            group.subcategories.forEach { sub ->
                LeafRow(
                    label = sub.name + if (sub.isArchived) " ($archived)" else "",
                    checked = sub.id in selectedIds,
                    onToggle = { actions.onToggleLeaf(sub.id) },
                )
            }
        }
    }
}

@Composable
private fun LeafRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .heightIn(min = 48.dp)
                .padding(start = 56.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
