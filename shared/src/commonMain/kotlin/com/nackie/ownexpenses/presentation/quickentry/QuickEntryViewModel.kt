package com.nackie.ownexpenses.presentation.quickentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.domain.TodayProvider
import com.nackie.ownexpenses.domain.model.AmountInput
import com.nackie.ownexpenses.domain.model.AmountKey
import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import com.nackie.ownexpenses.domain.repository.TransactionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** State of the open entry panel. Amount and category are the only required inputs. */
data class QuickEntryUiState(
    val category: Category,
    val subcategories: List<Category>,
    val selectedSubcategoryId: Long? = null,
    val amount: AmountInput = AmountInput(),
    val date: LocalDate,
    val today: LocalDate,
    val description: String = "",
    val isDescriptionExpanded: Boolean = false,
    val isSaving: Boolean = false,
) {
    val canSubmit: Boolean get() = amount.minorUnits > 0 && !isSaving
}

sealed interface QuickEntryEvent {
    /** Shown as a snackbar with an undo action. */
    data class Added(
        val transactionId: Long,
        val type: TransactionType,
    ) : QuickEntryEvent

    data object SaveFailed : QuickEntryEvent
}

/**
 * Bottom panel for recording an operation in three actions: tap a category, type the amount,
 * tap Add. No confirmations; a mistake is undone from the snackbar.
 */
class QuickEntryViewModel(
    private val transactionRepository: TransactionRepository,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val state = MutableStateFlow<QuickEntryUiState?>(null)

    /** Null while the panel is closed. */
    val uiState: StateFlow<QuickEntryUiState?> = state.asStateFlow()

    private val eventChannel = Channel<QuickEntryEvent>(Channel.BUFFERED)
    val events: Flow<QuickEntryEvent> = eventChannel.receiveAsFlow()

    fun open(category: CategoryWithSubcategories) {
        val today = todayProvider.today()
        state.value =
            QuickEntryUiState(
                category = category.category,
                subcategories = category.subcategories,
                date = today,
                today = today,
            )
    }

    fun close() {
        state.value = null
    }

    fun onKey(key: AmountKey) = edit { it.copy(amount = it.amount.press(key)) }

    /** Tapping the selected chip again clears it: the record then goes to the parent category. */
    fun onSubcategoryClick(id: Long) =
        edit { it.copy(selectedSubcategoryId = if (it.selectedSubcategoryId == id) null else id) }

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
                    amountMinor = current.amount.minorUnits,
                    date = current.date,
                    description = current.description,
                )
            try {
                val id = transactionRepository.addTransaction(input)
                state.value = null
                eventChannel.send(QuickEntryEvent.Added(id, current.category.type))
            } catch (e: IllegalArgumentException) {
                // The category disappeared meanwhile (e.g. deleted elsewhere); keep the input.
                state.update { it?.copy(isSaving = false) }
                eventChannel.send(QuickEntryEvent.SaveFailed)
            }
        }
    }

    fun undo(transactionId: Long) {
        viewModelScope.launch { transactionRepository.deleteTransaction(transactionId) }
    }

    private fun edit(transform: (QuickEntryUiState) -> QuickEntryUiState) {
        state.update { current -> current?.takeUnless { it.isSaving }?.let(transform) ?: current }
    }

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 200
    }
}
