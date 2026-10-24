package com.nackie.ownexpenses.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.presentation.AppViewModel
import com.nackie.ownexpenses.ui.home.HomeScreen
import com.nackie.ownexpenses.ui.more.MoreScreen
import com.nackie.ownexpenses.ui.navigation.CategoriesRoute
import com.nackie.ownexpenses.ui.navigation.MoreRoute
import com.nackie.ownexpenses.ui.navigation.OperationsRoute
import com.nackie.ownexpenses.ui.navigation.StatisticsRoute
import com.nackie.ownexpenses.ui.navigation.TopLevelDestination
import com.nackie.ownexpenses.ui.operations.OperationsScreen
import com.nackie.ownexpenses.ui.statistics.StatisticsScreen
import com.nackie.ownexpenses.ui.theme.OwnExpensesTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Root of the UI shared by all platforms. [onDarkThemeChange] lets the host match system bar
 * icons to the theme chosen in settings, which may differ from the system one.
 */
@Composable
fun App(
    modifier: Modifier = Modifier,
    onDarkThemeChange: (Boolean) -> Unit = {},
    appViewModel: AppViewModel = koinViewModel(),
) {
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val currentOnDarkThemeChange by rememberUpdatedState(onDarkThemeChange)
    LaunchedEffect(darkTheme) { currentOnDarkThemeChange(darkTheme) }

    OwnExpensesTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()

        Scaffold(
            modifier = modifier,
            bottomBar = {
                BottomBar(
                    currentDestination = backStackEntry?.destination,
                    onNavigate = navController::navigateToTopLevel,
                )
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = CategoriesRoute,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable<CategoriesRoute> { HomeScreen() }
                composable<OperationsRoute> { OperationsScreen() }
                composable<StatisticsRoute> { StatisticsScreen() }
                composable<MoreRoute> { MoreScreen() }
            }
        }
    }
}

@Composable
private fun BottomBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected =
                currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(stringResource(destination.label)) },
                modifier = Modifier.testTag(destination.testTag),
            )
        }
    }
}

/** Switches tabs keeping one back stack entry per tab and restoring its state. */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
