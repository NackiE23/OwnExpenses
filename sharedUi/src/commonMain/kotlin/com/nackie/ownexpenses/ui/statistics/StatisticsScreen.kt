package com.nackie.ownexpenses.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nackie.ownexpenses.ui.components.PlaceholderScreen
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.tab_statistics
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StatisticsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(Res.string.tab_statistics), modifier = modifier)
}
