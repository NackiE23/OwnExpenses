package com.nackie.ownexpenses.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF3A5F94),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD5E3FF),
        onPrimaryContainer = Color(0xFF001C3B),
        secondaryContainer = Color(0xFFDAE2F9),
        onSecondaryContainer = Color(0xFF131C2B),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF191C20),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFE0E2EC),
        onSurfaceVariant = Color(0xFF44474E),
        surfaceContainer = Color(0xFFEDEDF4),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6CF),
        error = Color(0xFFBA1A1A),
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFA6C8FF),
        onPrimary = Color(0xFF00315F),
        primaryContainer = Color(0xFF214A7A),
        onPrimaryContainer = Color(0xFFD5E3FF),
        secondaryContainer = Color(0xFF3E4759),
        onSecondaryContainer = Color(0xFFDAE2F9),
        background = Color(0xFF111318),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF44474E),
        onSurfaceVariant = Color(0xFFC4C6CF),
        surfaceContainer = Color(0xFF1D2024),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474E),
        error = Color(0xFFFFB4AB),
    )

/** Money colors that Material roles do not cover. */
data class MoneyColors(
    val expense: Color,
    val income: Color,
    val negativeBalance: Color,
)

internal val LightMoneyColors =
    MoneyColors(
        expense = Color(0xFFC62828),
        income = Color(0xFF2E7D32),
        negativeBalance = Color(0xFFC62828),
    )

internal val DarkMoneyColors =
    MoneyColors(
        expense = Color(0xFFEF6F6C),
        income = Color(0xFF6CC38A),
        negativeBalance = Color(0xFFEF6F6C),
    )
