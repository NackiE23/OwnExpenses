package com.nackie.ownexpenses.ui.operations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nackie.ownexpenses.ui.components.PlaceholderScreen
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.tab_operations
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun OperationsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(Res.string.tab_operations), modifier = modifier)
}
