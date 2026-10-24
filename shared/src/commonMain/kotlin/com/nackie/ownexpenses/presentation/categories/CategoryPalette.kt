package com.nackie.ownexpenses.presentation.categories

/** Colors offered for categories: saturated, readable on dark; the UI darkens them for text on light. */
object CategoryPalette {
    val colors: List<Int> =
        listOf(
            0xFF7C4DFF,
            0xFFF50057,
            0xFF448AFF,
            0xFFFF6E40,
            0xFF1DE9B6,
            0xFF536DFE,
            0xFFFF9100,
            0xFF76FF03,
            0xFFFF1744,
            0xFF00E676,
            0xFF00E5FF,
            0xFFB0BEC5,
        ).map { it.toInt() }

    /** Neutral icon for new categories until the user picks one. */
    const val DEFAULT_ICON = "other"

    /**
     * The first palette (Material 600 shades, felt dull) and default category colors outside it,
     * mapped to their saturated replacements. Used by the 1 → 2 migration and for older backups.
     */
    val legacyColors: Map<Int, Int> =
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
        ).map { it.toInt() }.zip(colors).toMap() +
            mapOf(
                // Default "Gifts" income and "Other" income.
                0xFFAD1457.toInt() to 0xFFE040FB.toInt(),
                0xFF546E7A.toInt() to 0xFF90A4AE.toInt(),
            )

    /** A color from the first palette becomes its saturated replacement; others stay as they are. */
    fun upgrade(color: Int): Int = legacyColors[color] ?: color
}
