package com.nackie.ownexpenses.ui.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nackie.ownexpenses.ui.components.PlaceholderScreen
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.tab_categories
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CategoriesScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(Res.string.tab_categories), modifier = modifier)
}
