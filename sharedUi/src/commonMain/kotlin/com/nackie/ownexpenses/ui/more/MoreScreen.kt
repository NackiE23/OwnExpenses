package com.nackie.ownexpenses.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nackie.ownexpenses.ui.components.PlaceholderScreen
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.tab_more
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MoreScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(Res.string.tab_more), modifier = modifier)
}
