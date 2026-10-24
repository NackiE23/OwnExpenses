package com.nackie.ownexpenses.domain

import com.nackie.ownexpenses.domain.model.CategoryWithSubcategories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the operations list shows within the period. [categoryIds] holds the exact categories a
 * record may be in: a top-level id stands for records directly in it (no subcategory), so a whole
 * category is its id plus its subcategories' ids (see [leafIds]). Empty means every category.
 */
data class OperationsFilter(
    val categoryIds: Set<Long> = emptySet(),
    /** Text the description must contain, ignoring case; blank means any. */
    val query: String = "",
) {
    val isActive: Boolean get() = categoryIds.isNotEmpty() || query.isNotBlank()
}

/** Ids a record of this category can carry: the category itself and each subcategory. */
fun CategoryWithSubcategories.leafIds(): Set<Long> = setOf(category.id) + subcategories.map { it.id }

/**
 * The operations filter shared by the operations tab and the main screen (which opens the tab for
 * one category). Lives for the process, like [PeriodSelection]; it is a view filter, not user data.
 */
class OperationsFilterSelection {
    private val selected = MutableStateFlow(OperationsFilter())

    val filter: StateFlow<OperationsFilter> = selected.asStateFlow()

    fun set(filter: OperationsFilter) {
        selected.value = filter
    }

    /** Only [category]'s records, with no text query; used from the main screen. */
    fun showOnly(category: CategoryWithSubcategories) = set(OperationsFilter(categoryIds = category.leafIds()))
}
