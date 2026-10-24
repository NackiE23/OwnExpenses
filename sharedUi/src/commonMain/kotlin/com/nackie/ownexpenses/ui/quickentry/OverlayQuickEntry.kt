package com.nackie.ownexpenses.ui.quickentry

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.presentation.AppViewModel
import com.nackie.ownexpenses.presentation.quickentry.QuickEntryViewModel
import com.nackie.ownexpenses.ui.format.rememberMoneyFormatter
import com.nackie.ownexpenses.ui.theme.OwnExpensesTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.koin.compose.viewmodel.koinViewModel

/**
 * Entry panel alone over whatever is behind (the home screen, for the widget). Opens the
 * category, and calls [onFinish] once the panel is closed and its snackbar is gone, so undo
 * stays available after "Add".
 */
@Composable
fun OverlayQuickEntry(
    categoryId: Long,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    appViewModel: AppViewModel = koinViewModel(),
    viewModel: QuickEntryViewModel = koinViewModel(),
) {
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnFinish by rememberUpdatedState(onFinish)
    var wasOpen by remember { mutableStateOf(false) }

    LaunchedEffect(categoryId) { viewModel.openCategory(categoryId) }

    val isOpen = state != null
    LaunchedEffect(isOpen) {
        if (isOpen) {
            wasOpen = true
            return@LaunchedEffect
        }
        // Not opened yet: give loading a moment; a deleted category simply closes the overlay.
        // Closed: let the snackbar (if any) appear, then wait until it is dismissed or times out.
        delay(if (wasOpen) SNACKBAR_APPEAR_DELAY_MILLIS else OPEN_TIMEOUT_MILLIS)
        if (!wasOpen && viewModel.uiState.value != null) return@LaunchedEffect
        snapshotFlow { snackbarHostState.currentSnackbarData }.first { it == null }
        currentOnFinish()
    }

    OwnExpensesTheme(darkTheme = darkTheme) {
        Box(modifier = modifier.fillMaxSize()) {
            UndoSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }
        // Resolves the same QuickEntryViewModel as above: same ViewModelStoreOwner (the activity).
        QuickEntryHost(snackbarHostState = snackbarHostState, formatter = rememberMoneyFormatter())
    }
}

private const val SNACKBAR_APPEAR_DELAY_MILLIS = 300L
private const val OPEN_TIMEOUT_MILLIS = 2_000L
