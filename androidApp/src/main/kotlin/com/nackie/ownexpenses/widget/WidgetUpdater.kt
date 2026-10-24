package com.nackie.ownexpenses.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.nackie.ownexpenses.presentation.widget.WidgetSnapshotSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Redraws placed widgets whenever their data changes while the app process runs; all changes
 * come from the app, so nothing is missed. Day rollover while the process is gone is covered
 * by the widget's periodic update (res/xml/quick_entry_widget_info.xml).
 */
class WidgetUpdater(
    private val context: Context,
    private val source: WidgetSnapshotSource,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        source
            .observe()
            // The first value is what the widget already shows after its own load.
            .drop(1)
            .debounce(DEBOUNCE_MILLIS)
            .onEach {
                val widgets = GlanceAppWidgetManager(context).getGlanceIds(QuickEntryWidget::class.java)
                if (widgets.isNotEmpty()) QuickEntryWidget().updateAll(context)
            }.launchIn(scope)
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
    }
}
