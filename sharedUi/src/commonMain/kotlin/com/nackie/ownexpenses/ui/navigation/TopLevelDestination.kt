package com.nackie.ownexpenses.ui.navigation

import com.nackie.ownexpenses.ui.UiTestTags
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.ic_tab_categories
import com.nackie.ownexpenses.ui.resources.ic_tab_more
import com.nackie.ownexpenses.ui.resources.ic_tab_operations
import com.nackie.ownexpenses.ui.resources.ic_tab_statistics
import com.nackie.ownexpenses.ui.resources.tab_categories
import com.nackie.ownexpenses.ui.resources.tab_more
import com.nackie.ownexpenses.ui.resources.tab_operations
import com.nackie.ownexpenses.ui.resources.tab_statistics
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

@Serializable
internal data object CategoriesRoute

@Serializable
internal data object OperationsRoute

@Serializable
internal data object StatisticsRoute

@Serializable
internal data object MoreRoute

/**
 * Categories screen, above the tabs. [type] is a TransactionType name; [create] opens the
 * editor for a new category right away (the "Add" tile).
 */
@Serializable
internal data class ManageCategoriesRoute(
    val type: String,
    val create: Boolean = false,
)

/** Bottom navigation tabs, in display order. */
internal enum class TopLevelDestination(
    val route: Any,
    val label: StringResource,
    val icon: DrawableResource,
    val testTag: String,
) {
    CATEGORIES(CategoriesRoute, Res.string.tab_categories, Res.drawable.ic_tab_categories, UiTestTags.TAB_CATEGORIES),
    OPERATIONS(OperationsRoute, Res.string.tab_operations, Res.drawable.ic_tab_operations, UiTestTags.TAB_OPERATIONS),
    STATISTICS(StatisticsRoute, Res.string.tab_statistics, Res.drawable.ic_tab_statistics, UiTestTags.TAB_STATISTICS),
    MORE(MoreRoute, Res.string.tab_more, Res.drawable.ic_tab_more, UiTestTags.TAB_MORE),
}
