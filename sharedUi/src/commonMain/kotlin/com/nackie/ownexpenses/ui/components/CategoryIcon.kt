package com.nackie.ownexpenses.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.ic_category_cafe
import com.nackie.ownexpenses.ui.resources.ic_category_clothes
import com.nackie.ownexpenses.ui.resources.ic_category_entertainment
import com.nackie.ownexpenses.ui.resources.ic_category_gifts
import com.nackie.ownexpenses.ui.resources.ic_category_groceries
import com.nackie.ownexpenses.ui.resources.ic_category_health
import com.nackie.ownexpenses.ui.resources.ic_category_home
import com.nackie.ownexpenses.ui.resources.ic_category_mobile_internet
import com.nackie.ownexpenses.ui.resources.ic_category_other
import com.nackie.ownexpenses.ui.resources.ic_category_pets
import com.nackie.ownexpenses.ui.resources.ic_category_public_transport
import com.nackie.ownexpenses.ui.resources.ic_category_salary
import com.nackie.ownexpenses.ui.resources.ic_category_taxi
import com.nackie.ownexpenses.ui.resources.ic_category_transport
import com.nackie.ownexpenses.ui.resources.ic_category_utilities
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Icons offered in the category editor, in picker order. */
internal val categoryIconKeys =
    listOf(
        "groceries",
        "cafe",
        "transport",
        "taxi",
        "public_transport",
        "home",
        "utilities",
        "mobile_internet",
        "health",
        "entertainment",
        "clothes",
        "pets",
        "gifts",
        "salary",
        "other",
    )

/** Icon keys stored with categories; unknown keys fall back to a neutral icon. */
internal fun categoryIcon(key: String): DrawableResource =
    when (key) {
        "groceries" -> Res.drawable.ic_category_groceries
        "cafe" -> Res.drawable.ic_category_cafe
        "transport" -> Res.drawable.ic_category_transport
        "taxi" -> Res.drawable.ic_category_taxi
        "public_transport" -> Res.drawable.ic_category_public_transport
        "home" -> Res.drawable.ic_category_home
        "utilities" -> Res.drawable.ic_category_utilities
        "mobile_internet" -> Res.drawable.ic_category_mobile_internet
        "health" -> Res.drawable.ic_category_health
        "entertainment" -> Res.drawable.ic_category_entertainment
        "clothes" -> Res.drawable.ic_category_clothes
        "pets" -> Res.drawable.ic_category_pets
        "gifts" -> Res.drawable.ic_category_gifts
        "salary" -> Res.drawable.ic_category_salary
        else -> Res.drawable.ic_category_other
    }

/** Category icon on a tinted rounded square, as on the main-screen tiles. */
@Composable
internal fun CategoryIconBadge(
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier.size(size).background(color.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(categoryIcon(icon)),
            contentDescription = null,
            tint = categoryContentColor(color),
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * Category colors are saturated for dark surfaces; on light ones they are darkened so icons and
 * amounts drawn in them stay readable. Fills (badge backgrounds, chart slices) use the color as is.
 */
@Composable
@ReadOnlyComposable
internal fun categoryContentColor(color: Color): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > LIGHT_SURFACE_LUMINANCE) {
        lerp(color, Color.Black, LIGHT_THEME_DARKENING)
    } else {
        color
    }

private const val LIGHT_SURFACE_LUMINANCE = 0.5f
private const val LIGHT_THEME_DARKENING = 0.3f
