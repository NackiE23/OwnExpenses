package com.nackie.ownexpenses.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryRemoval
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.presentation.categories.CategoriesEvent
import com.nackie.ownexpenses.presentation.categories.CategoriesViewModel
import com.nackie.ownexpenses.ui.components.CategoryIconBadge
import com.nackie.ownexpenses.ui.quickentry.UndoSnackbarHost
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.categories_add_subcategory
import com.nackie.ownexpenses.ui.resources.categories_archive
import com.nackie.ownexpenses.ui.resources.categories_archive_section
import com.nackie.ownexpenses.ui.resources.categories_archived_instead
import com.nackie.ownexpenses.ui.resources.categories_archived_label
import com.nackie.ownexpenses.ui.resources.categories_back
import com.nackie.ownexpenses.ui.resources.categories_create
import com.nackie.ownexpenses.ui.resources.categories_delete
import com.nackie.ownexpenses.ui.resources.categories_deleted
import com.nackie.ownexpenses.ui.resources.categories_drag
import com.nackie.ownexpenses.ui.resources.categories_edit
import com.nackie.ownexpenses.ui.resources.categories_more_actions
import com.nackie.ownexpenses.ui.resources.categories_move_down
import com.nackie.ownexpenses.ui.resources.categories_move_up
import com.nackie.ownexpenses.ui.resources.categories_order_hint
import com.nackie.ownexpenses.ui.resources.categories_title
import com.nackie.ownexpenses.ui.resources.categories_unarchive
import com.nackie.ownexpenses.ui.resources.ic_add
import com.nackie.ownexpenses.ui.resources.ic_arrow_back
import com.nackie.ownexpenses.ui.resources.ic_drag_handle
import com.nackie.ownexpenses.ui.resources.ic_more_vert
import com.nackie.ownexpenses.ui.resources.snackbar_undo
import com.nackie.ownexpenses.ui.resources.type_expenses
import com.nackie.ownexpenses.ui.resources.type_incomes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Categories screen: create, edit, archive or delete categories and subcategories, and set the
 * order of the main-screen tiles by dragging. Opened from the main screen and from "More".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesScreen(
    initialType: TransactionType,
    startWithCreate: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var initialApplied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (initialApplied) return@LaunchedEffect
        initialApplied = true
        viewModel.selectType(initialType)
        if (startWithCreate) viewModel.startCreate()
    }
    RemovalSnackbarEffect(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onUndo = viewModel::undoRemove,
    )

    // The app scaffold already pads for the system bars.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(Res.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), stringResource(Res.string.categories_back))
                    }
                },
            )
        },
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        CategoryList(
            type = state.type,
            active = state.active,
            archived = state.archived,
            onSelectType = viewModel::selectType,
            onCreate = { viewModel.startCreate() },
            actions =
                CategoryActions(
                    onEdit = viewModel::startEdit,
                    onAddSubcategory = { viewModel.startCreate(parent = it) },
                    onMove = viewModel::move,
                    onReorder = viewModel::reorder,
                    onSetArchived = viewModel::setArchived,
                    onDelete = viewModel::remove,
                ),
            modifier = Modifier.padding(innerPadding),
        )
    }

    state.editor?.let { editor ->
        CategoryEditorSheet(
            editor = editor,
            onNameChange = viewModel::onNameChange,
            onColorChange = viewModel::onColorChange,
            onIconChange = viewModel::onIconChange,
            onSave = viewModel::save,
            onDismiss = viewModel::cancelEdit,
        )
    }
}

/** What can be done to a category from its row. */
private class CategoryActions(
    val onEdit: (Category) -> Unit,
    val onAddSubcategory: (Category) -> Unit,
    val onMove: (Category, Int) -> Unit,
    val onReorder: (List<Long>) -> Unit,
    val onSetArchived: (Category, Boolean) -> Unit,
    val onDelete: (Category) -> Unit,
)

@Composable
private fun RemovalSnackbarEffect(
    events: Flow<CategoriesEvent>,
    snackbarHostState: SnackbarHostState,
    onUndo: (CategoryRemoval.Deleted) -> Unit,
) {
    val currentOnUndo by rememberUpdatedState(onUndo)
    LaunchedEffect(events, snackbarHostState) {
        events.collect { event ->
            when (event) {
                is CategoriesEvent.Removed -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    launch {
                        when (val removal = event.removal) {
                            is CategoryRemoval.Deleted -> {
                                val result =
                                    snackbarHostState.showSnackbar(
                                        message = getString(Res.string.categories_deleted, event.name),
                                        actionLabel = getString(Res.string.snackbar_undo),
                                        duration = SnackbarDuration.Long,
                                    )
                                if (result == SnackbarResult.ActionPerformed) currentOnUndo(removal)
                            }

                            CategoryRemoval.Archived -> {
                                snackbarHostState.showSnackbar(
                                    getString(Res.string.categories_archived_instead, event.name),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryList(
    type: TransactionType,
    active: List<CategoryWithSubcategories>,
    archived: List<CategoryWithSubcategories>,
    onSelectType: (TransactionType) -> Unit,
    onCreate: () -> Unit,
    actions: CategoryActions,
    modifier: Modifier = Modifier,
) {
    // Local copy follows the finger while dragging; the database gets the order on drop.
    var order by remember(active) { mutableStateOf(active) }
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            val fromIndex = order.indexOfFirst { it.category.id.reorderKey() == from.key }
            val toIndex = order.indexOfFirst { it.category.id.reorderKey() == to.key }
            if (fromIndex >= 0 && toIndex >= 0) {
                order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "type") { TypeSelector(type = type, onSelect = onSelectType) }
        item(key = "hint") {
            Text(
                text = stringResource(Res.string.categories_order_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "create") {
            FilledTonalButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.categories_create))
            }
        }
        items(order, key = { it.category.id.reorderKey() }) { node ->
            ReorderableItem(reorderState, key = node.category.id.reorderKey()) { isDragging ->
                val index = order.indexOf(node)
                CategoryCard(
                    node = node,
                    canMoveUp = index > 0,
                    canMoveDown = index < order.lastIndex,
                    actions = actions,
                    dragHandle =
                        Modifier.draggableHandle(
                            onDragStopped = { actions.onReorder(order.map { it.category.id }) },
                        ),
                    elevated = isDragging,
                )
            }
        }
        if (archived.isNotEmpty()) {
            item(key = "archive") {
                Text(
                    text = stringResource(Res.string.categories_archive_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(archived, key = { "archived-${it.category.id}" }) { node ->
                CategoryCard(node = node, canMoveUp = false, canMoveDown = false, actions = actions, dragHandle = null)
            }
        }
    }
}

private fun Long.reorderKey(): String = "category-$this"

@Composable
private fun TypeSelector(
    type: TransactionType,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        TransactionType.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == type,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = TransactionType.entries.size),
            ) {
                Text(
                    stringResource(
                        if (option ==
                            TransactionType.EXPENSE
                        ) {
                            Res.string.type_expenses
                        } else {
                            Res.string.type_incomes
                        },
                    ),
                )
            }
        }
    }
}

/**
 * A top-level category with its subcategories. [dragHandle] is null for archived categories,
 * which keep their place in the archive section and cannot be reordered.
 */
@Composable
private fun CategoryCard(
    node: CategoryWithSubcategories,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: CategoryActions,
    dragHandle: Modifier?,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val category = node.category
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors =
            if (elevated) {
                CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            } else {
                CardDefaults.outlinedCardColors()
            },
    ) {
        CategoryRow(
            category = category,
            dragHandle = dragHandle,
            menu = {
                CategoryMenu(
                    category = category,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    actions = actions,
                    canAddSubcategory = !category.isArchived,
                )
            },
        )
        if (node.subcategories.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
            SubcategoryList(subcategories = node.subcategories, reorderable = dragHandle != null, actions = actions)
        }
        if (!category.isArchived) {
            TextButton(
                onClick = { actions.onAddSubcategory(category) },
                modifier = Modifier.padding(start = 52.dp, bottom = 4.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.categories_add_subcategory))
            }
        }
    }
}

/** Subcategories reorder within their parent only, by the same drag handle. */
@Composable
private fun SubcategoryList(
    subcategories: List<Category>,
    reorderable: Boolean,
    actions: CategoryActions,
    modifier: Modifier = Modifier,
) {
    var order by remember(subcategories) { mutableStateOf(subcategories) }
    ReorderableColumn(
        list = order,
        onSettle = { from, to ->
            order = order.toMutableList().apply { add(to, removeAt(from)) }
            actions.onReorder(order.map { it.id })
        },
        modifier = modifier.padding(start = 40.dp),
    ) { index, subcategory, _ ->
        key(subcategory.id) {
            ReorderableItem {
                CategoryRow(
                    category = subcategory,
                    dragHandle = if (reorderable) Modifier.draggableHandle() else null,
                    small = true,
                    menu = {
                        CategoryMenu(
                            category = subcategory,
                            canMoveUp = index > 0,
                            canMoveDown = index < order.lastIndex,
                            actions = actions,
                            canAddSubcategory = false,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    dragHandle: Modifier?,
    menu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = if (small) 48.dp else 64.dp)
                .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dragHandle != null) {
            Box(modifier = dragHandle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(Res.drawable.ic_drag_handle), stringResource(Res.string.categories_drag))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        CategoryIconBadge(icon = category.icon, color = Color(category.color), size = if (small) 32.dp else 40.dp)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = category.name,
                style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (category.isArchived) {
                Text(
                    text = stringResource(Res.string.categories_archived_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        menu()
    }
}

@Composable
private fun CategoryMenu(
    category: Category,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canAddSubcategory: Boolean,
    actions: CategoryActions,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    fun run(action: () -> Unit) {
        expanded = false
        action()
    }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(Res.drawable.ic_more_vert), stringResource(Res.string.categories_more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.categories_edit)) },
                onClick = { run { actions.onEdit(category) } },
            )
            if (canAddSubcategory) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.categories_add_subcategory)) },
                    onClick = { run { actions.onAddSubcategory(category) } },
                )
            }
            if (canMoveUp) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.categories_move_up)) },
                    onClick = { run { actions.onMove(category, -1) } },
                )
            }
            if (canMoveDown) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.categories_move_down)) },
                    onClick = { run { actions.onMove(category, 1) } },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (category.isArchived) Res.string.categories_unarchive else Res.string.categories_archive,
                        ),
                    )
                },
                onClick = { run { actions.onSetArchived(category, !category.isArchived) } },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.categories_delete), color = MaterialTheme.colorScheme.error) },
                onClick = { run { actions.onDelete(category) } },
            )
        }
    }
}
