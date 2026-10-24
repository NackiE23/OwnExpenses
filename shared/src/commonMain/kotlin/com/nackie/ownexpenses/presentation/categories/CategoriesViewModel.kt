package com.nackie.ownexpenses.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryRemoval
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.NewCategory
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.domain.repository.CategoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CategoriesUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    /** Categories in use, in the user's order; subcategories include archived ones (marked). */
    val active: List<CategoryWithSubcategories> = emptyList(),
    /** Archived top-level categories: hidden on the main screen, kept for history. */
    val archived: List<CategoryWithSubcategories> = emptyList(),
    val editor: CategoryEditor? = null,
    val isLoading: Boolean = true,
)

/** Form for creating or editing a category or subcategory. */
data class CategoryEditor(
    /** Null when creating. */
    val categoryId: Long?,
    /** Set for subcategories; their type always follows the parent. */
    val parent: Category?,
    val type: TransactionType,
    val name: String,
    /** ARGB color. */
    val color: Int,
    val icon: String,
) {
    val isNew: Boolean get() = categoryId == null

    val canSave: Boolean get() = name.isNotBlank()
}

sealed interface CategoriesEvent {
    /** Deleted ones can be put back from the snackbar; archived ones are only announced. */
    data class Removed(
        val name: String,
        val removal: CategoryRemoval,
    ) : CategoriesEvent
}

/**
 * Categories screen (docs/TZ.md, FR-3): create, rename, recolor, change icon, subcategories one
 * level deep, archive instead of delete when records exist, and the order of tiles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private val type = MutableStateFlow(TransactionType.EXPENSE)
    private val editor = MutableStateFlow<CategoryEditor?>(null)

    val uiState: StateFlow<CategoriesUiState> =
        type
            .flatMapLatest { selected ->
                combine(categoryRepository.observeCategories(selected, includeArchived = true), editor) { tree, form ->
                    CategoriesUiState(
                        type = selected,
                        active = tree.filterNot { it.category.isArchived },
                        archived = tree.filter { it.category.isArchived },
                        editor = form,
                        isLoading = false,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CategoriesUiState())

    private val eventChannel = Channel<CategoriesEvent>(Channel.BUFFERED)
    val events: Flow<CategoriesEvent> = eventChannel.receiveAsFlow()

    fun selectType(selected: TransactionType) {
        type.value = selected
    }

    /** New top-level category of the shown type, or a subcategory of [parent]. */
    fun startCreate(parent: Category? = null) {
        val siblings = uiState.value.active.size + uiState.value.archived.size
        editor.value =
            CategoryEditor(
                categoryId = null,
                parent = parent,
                type = parent?.type ?: type.value,
                name = "",
                color = parent?.color ?: CategoryPalette.colors[siblings % CategoryPalette.colors.size],
                icon = parent?.icon ?: CategoryPalette.DEFAULT_ICON,
            )
    }

    fun startEdit(category: Category) {
        val parent = category.parentId?.let { id -> uiState.value.allTopLevel().firstOrNull { it.id == id } }
        editor.value = CategoryEditor(category.id, parent, category.type, category.name, category.color, category.icon)
    }

    fun onNameChange(name: String) = editor.update { it?.copy(name = name.take(MAX_NAME_LENGTH)) }

    fun onColorChange(color: Int) = editor.update { it?.copy(color = color) }

    fun onIconChange(icon: String) = editor.update { it?.copy(icon = icon) }

    fun cancelEdit() {
        editor.value = null
    }

    fun save() {
        val form = editor.value?.takeIf { it.canSave } ?: return
        editor.value = null
        viewModelScope.launch {
            persist {
                if (form.categoryId == null) {
                    categoryRepository.createCategory(
                        NewCategory(form.name, form.type, form.color, form.icon, form.parent?.id),
                    )
                } else {
                    categoryRepository.updateCategory(form.categoryId, form.name, form.color, form.icon)
                }
            }
        }
    }

    /** Deletes an unused category with its subcategories, or archives one that has records. */
    fun remove(category: Category) {
        viewModelScope.launch {
            val removal = persist { categoryRepository.removeCategory(category.id) }
            eventChannel.send(CategoriesEvent.Removed(category.name, removal))
        }
    }

    fun undoRemove(removal: CategoryRemoval.Deleted) {
        viewModelScope.launch { persist { categoryRepository.restoreCategories(removal.removed) } }
    }

    fun setArchived(
        category: Category,
        archived: Boolean,
    ) {
        viewModelScope.launch { persist { categoryRepository.setArchived(category.id, archived) } }
    }

    /** Saves a new order of siblings, e.g. after dragging; ids must share one level. */
    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch { persist { categoryRepository.reorder(orderedIds) } }
    }

    /** Accessible alternative to dragging: one step up (-1) or down (+1) among siblings. */
    fun move(
        category: Category,
        steps: Int,
    ) {
        val siblings = siblingsOf(category).map { it.id }.toMutableList()
        val from = siblings.indexOf(category.id)
        val to = from + steps
        if (from < 0 || to !in siblings.indices) return
        siblings.add(to, siblings.removeAt(from))
        reorder(siblings)
    }

    private fun siblingsOf(category: Category): List<Category> {
        val state = uiState.value
        val parentId = category.parentId ?: return state.active.map { it.category }
        return (state.active + state.archived).firstOrNull { it.category.id == parentId }?.subcategories.orEmpty()
    }

    private fun CategoriesUiState.allTopLevel(): List<Category> = (active + archived).map { it.category }

    /** Writes finish even if the user leaves the screen right away. */
    private suspend fun <T> persist(write: suspend () -> T): T = withContext(NonCancellable) { write() }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_NAME_LENGTH = 40
    }
}
