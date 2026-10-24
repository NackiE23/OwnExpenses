package com.nackie.ownexpenses.presentation.quickentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.domain.TodayProvider
import com.nackie.ownexpenses.domain.model.AmountExpression
import com.nackie.ownexpenses.domain.model.AmountKey
import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.Transaction
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.domain.repository.CategoryRepository
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** State of the open entry panel. Amount and category are the only required inputs. */
data class QuickEntryUiState(
    val category: Category,
    val subcategories: List<Category>,
    val selectedSubcategoryId: Long? = null,
    /** What the keypad built; the record gets its result. */
    val amount: AmountExpression = AmountExpression(),
    val date: LocalDate,
    val today: LocalDate,
    val description: String = "",
    val isDescriptionExpanded: Boolean = false,
    val isSaving: Boolean = false,
    /** The record being edited; null when adding a new one. */
    val editing: Transaction? = null,
    /** Categories the edited record can be moved to (same type); empty when adding. */
    val categoryChoices: List<CategoryWithSubcategories> = emptyList(),
) {
    val isEditing: Boolean get() = editing != null

    /** Only a computable, positive result can be saved; division by zero or "50 − 80" cannot. */
    val canSubmit: Boolean get() = (amount.result ?: 0) > 0 && !isSaving
}

sealed interface QuickEntryEvent {
    /** Shown as a snackbar with an undo action that removes the record again. */
    data class Added(
        val transactionId: Long,
        val type: TransactionType,
    ) : QuickEntryEvent

    data object Updated : QuickEntryEvent

    /** Shown as a snackbar with an undo action that restores [transaction]. */
    data class Deleted(
        val transaction: Transaction,
    ) : QuickEntryEvent

    data object SaveFailed : QuickEntryEvent
}

/**
 * Entry panel for adding (three actions: category, amount, Add) and editing records, plus
 * deletion. Nothing asks for confirmation; adding and deleting are undone from the snackbar.
 */
class QuickEntryViewModel(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val state = MutableStateFlow<QuickEntryUiState?>(null)

    /** Null while the panel is closed. */
    val uiState: StateFlow<QuickEntryUiState?> = state.asStateFlow()

    private val eventChannel = Channel<QuickEntryEvent>(Channel.BUFFERED)
    val events: Flow<QuickEntryEvent> = eventChannel.receiveAsFlow()

    private var openJob: Job? = null

    fun open(category: CategoryWithSubcategories) {
        openJob?.cancel()
        show(category)
    }

    /** Opens the panel for a top-level category known only by id (e.g. from the widget). */
    fun openCategory(categoryId: Long) {
        openJob?.cancel()
        openJob =
            viewModelScope.launch {
                val category = categoryRepository.getCategory(categoryId) ?: return@launch
                val node =
                    categoryRepository
                        .observeCategories(category.type)
                        .first()
                        .firstOrNull { it.category.id == categoryId } ?: return@launch
                show(node)
            }
    }

    private fun show(category: CategoryWithSubcategories) {
        val today = todayProvider.today()
        state.value =
            QuickEntryUiState(
                category = category.category,
                subcategories = category.subcategories,
                date = today,
                today = today,
            )
    }

    /** Opens the panel pre-filled with a stored record. */
    fun openForEdit(transactionId: Long) {
        openJob?.cancel()
        openJob =
            viewModelScope.launch {
                val transaction = transactionRepository.getTransaction(transactionId) ?: return@launch
                // Archived categories are included so that old records stay editable.
                val tree = categoryRepository.observeCategories(transaction.type, includeArchived = true).first()
                val node =
                    tree.firstOrNull { node ->
                        node.category.id == transaction.categoryId ||
                            node.subcategories.any { it.id == transaction.categoryId }
                    } ?: return@launch
                val selectedSubcategoryId = node.subcategories.firstOrNull { it.id == transaction.categoryId }?.id

                state.value =
                    QuickEntryUiState(
                        category = node.category,
                        subcategories = node.subcategories.activeOr(selectedSubcategoryId),
                        selectedSubcategoryId = selectedSubcategoryId,
                        amount = AmountExpression.of(transaction.amountMinor),
                        date = transaction.date,
                        today = todayProvider.today(),
                        description = transaction.description.orEmpty(),
                        isDescriptionExpanded = transaction.description != null,
                        editing = transaction,
                        categoryChoices =
                            tree
                                .filter { !it.category.isArchived || it.category.id == node.category.id }
                                .map { it.copy(subcategories = it.subcategories.activeOr(selectedSubcategoryId)) },
                    )
            }
    }

    fun close() {
        openJob?.cancel()
        state.value = null
    }

    fun onKey(key: AmountKey) = edit { it.copy(amount = it.amount.press(key)) }

    /** Tapping the selected chip again clears it: the record then goes to the parent category. */
    fun onSubcategoryClick(id: Long) =
        edit { it.copy(selectedSubcategoryId = if (it.selectedSubcategoryId == id) null else id) }

    /** Moves an edited record to another top-level category of the same type. */
    fun selectCategory(categoryId: Long) =
        edit { current ->
            val choice = current.categoryChoices.firstOrNull { it.category.id == categoryId } ?: return@edit current
            current.copy(category = choice.category, subcategories = choice.subcategories, selectedSubcategoryId = null)
        }

    fun shiftDate(days: Int) = edit { it.copy(date = it.date.plus(days, DateTimeUnit.DAY)) }

    fun setDate(date: LocalDate) = edit { it.copy(date = date) }

    fun expandDescription() = edit { it.copy(isDescriptionExpanded = true) }

    fun onDescriptionChange(text: String) = edit { it.copy(description = text.take(MAX_DESCRIPTION_LENGTH)) }

    fun submit() {
        val current = state.value ?: return
        if (!current.canSubmit) return
        state.value = current.copy(isSaving = true)

        viewModelScope.launch {
            val input =
                TransactionInput(
                    categoryId = current.selectedSubcategoryId ?: current.category.id,
                    amountMinor = checkNotNull(current.amount.result),
                    date = current.date,
                    description = current.description,
                )
            try {
                val editing = current.editing
                val event =
                    if (editing == null) {
                        QuickEntryEvent.Added(
                            persist { transactionRepository.addTransaction(input) },
                            current.category.type,
                        )
                    } else {
                        persist { transactionRepository.updateTransaction(editing.id, input) }
                        QuickEntryEvent.Updated
                    }
                state.value = null
                eventChannel.send(event)
            } catch (e: IllegalArgumentException) {
                // The category or record disappeared meanwhile; keep the input.
                state.update { it?.copy(isSaving = false) }
                eventChannel.send(QuickEntryEvent.SaveFailed)
            }
        }
    }

    /** Deletes the record open for editing. */
    fun deleteEdited() {
        val editing = state.value?.editing ?: return
        state.value = null
        deleteTransaction(editing.id)
    }

    private fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            val deleted = persist { transactionRepository.deleteTransaction(transactionId) } ?: return@launch
            eventChannel.send(QuickEntryEvent.Deleted(deleted))
        }
    }

    /** Undo for [QuickEntryEvent.Added]. */
    fun undoAdd(transactionId: Long) {
        viewModelScope.launch { persist { transactionRepository.deleteTransaction(transactionId) } }
    }

    /** Undo for [QuickEntryEvent.Deleted]. */
    fun undoDelete(transaction: Transaction) {
        viewModelScope.launch { persist { transactionRepository.restoreTransaction(transaction) } }
    }

    /**
     * User-initiated writes complete even if the screen closes meanwhile (the widget panel
     * finishes its activity right after the snackbar), so a tap never gets lost half-way.
     */
    private suspend fun <T> persist(write: suspend () -> T): T = withContext(NonCancellable) { write() }

    private fun edit(transform: (QuickEntryUiState) -> QuickEntryUiState) {
        state.update { current -> current?.takeUnless { it.isSaving }?.let(transform) ?: current }
    }

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 200
    }
}

/** Non-archived subcategories, plus [keepId] even if archived (the edited record uses it). */
private fun List<Category>.activeOr(keepId: Long?): List<Category> = filter { !it.isArchived || it.id == keepId }
