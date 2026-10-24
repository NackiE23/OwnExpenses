package com.nackie.ownexpenses.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }

@Composable
fun OwnExpensesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMoneyColors provides if (darkTheme) DarkMoneyColors else LightMoneyColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            content = content,
        )
    }
}

/** Accessors for app-specific theme values, alongside [MaterialTheme]. */
object OwnExpensesTheme {
    val moneyColors: MoneyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMoneyColors.current
}
