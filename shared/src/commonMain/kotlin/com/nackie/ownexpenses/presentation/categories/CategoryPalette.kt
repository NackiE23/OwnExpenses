package com.nackie.ownexpenses.presentation.categories

/** Colors offered for categories; distinct on light and dark backgrounds. */
object CategoryPalette {
    val colors: List<Int> =
        listOf(
            0xFF7E57C2,
            0xFFD81B60,
            0xFF1E88E5,
            0xFF8D6E63,
            0xFF00897B,
            0xFF3949AB,
            0xFFFB8C00,
            0xFF7CB342,
            0xFFE53935,
            0xFF2E7D32,
            0xFF00ACC1,
            0xFF757575,
        ).map { it.toInt() }

    /** Neutral icon for new categories until the user picks one. */
    const val DEFAULT_ICON = "other"
}
