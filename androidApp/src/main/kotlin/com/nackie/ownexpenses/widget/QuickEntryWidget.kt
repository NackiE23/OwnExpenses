package com.nackie.ownexpenses.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nackie.ownexpenses.MainActivity
import com.nackie.ownexpenses.R
import com.nackie.ownexpenses.domain.model.Category
import com.nackie.ownexpenses.presentation.format.MoneyFormatter
import com.nackie.ownexpenses.presentation.widget.WidgetSnapshot
import com.nackie.ownexpenses.presentation.widget.WidgetSnapshotSource
import org.koin.core.context.GlobalContext

/**
 * Home-screen widget (docs/TZ.md, FR-9): current balance and the three most used expense
 * categories. A category opens only the entry panel over the home screen, not the app.
 */
class QuickEntryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val source = GlobalContext.get().get<WidgetSnapshotSource>()
        val initial = source.current()
        val language =
            context.resources.configuration.locales[0]
                .language
        val formatter = MoneyFormatter.forLanguage(language)
        provideContent {
            // While the session lives, changes arrive through the flow; updateAll() alone would
            // only recompose with data captured above. After the session ends, the next update
            // runs provideGlance again (see WidgetUpdater).
            val snapshot by source.observe().collectAsState(initial)
            GlanceTheme {
                WidgetContent(snapshot = snapshot, formatter = formatter)
            }
        }
    }
}

class QuickEntryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickEntryWidget()
}

@Composable
private fun WidgetContent(
    snapshot: WidgetSnapshot,
    formatter: MoneyFormatter,
) {
    val context = LocalContext.current
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(12.dp),
    ) {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        ) {
            Text(
                text = context.getString(R.string.widget_balance),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Text(
                text = formatter.format(snapshot.balanceMinor),
                style =
                    TextStyle(
                        color =
                            if (snapshot.balanceMinor <
                                0
                            ) {
                                ColorProvider(NEGATIVE_BALANCE)
                            } else {
                                GlanceTheme.colors.onSurface
                            },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        if (snapshot.categories.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_no_categories),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        } else {
            // Buttons take the remaining height, so a taller widget gets bigger tap targets.
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                snapshot.categories.forEachIndexed { index, category ->
                    if (index > 0) Spacer(GlanceModifier.width(8.dp))
                    CategoryButton(category = category, modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun CategoryButton(
    category: Category,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .background(ColorProvider(Color(category.color).copy(alpha = BUTTON_TINT_ALPHA)))
                .cornerRadius(12.dp)
                .clickable(actionStartActivity(QuickEntryActivity.intent(context, category.id))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = category.name,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.padding(horizontal = 6.dp),
        )
    }
}

private const val BUTTON_TINT_ALPHA = 0.28f

/** Same red as the app's negative balance in light theme; readable on both widget backgrounds. */
private val NEGATIVE_BALANCE = Color(0xFFC62828)
