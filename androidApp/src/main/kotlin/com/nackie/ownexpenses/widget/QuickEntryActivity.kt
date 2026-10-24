package com.nackie.ownexpenses.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import com.nackie.ownexpenses.ui.quickentry.OverlayQuickEntry

/**
 * Transparent activity started from the widget: only the entry panel, over the home screen.
 * Lives in its own task and stays out of recents, so the app itself is not brought up.
 */
class QuickEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, NO_CATEGORY)
        if (categoryId == NO_CATEGORY) {
            finish()
            return
        }
        setContent {
            OverlayQuickEntry(categoryId = categoryId, onFinish = ::finish)
        }
    }

    companion object {
        private const val EXTRA_CATEGORY_ID = "category_id"
        private const val NO_CATEGORY = -1L

        /** The data URI keeps the widget's pending intents distinct per category. */
        fun intent(
            context: Context,
            categoryId: Long,
        ): Intent =
            Intent(context, QuickEntryActivity::class.java)
                .setData("ownexpenses://widget/category/$categoryId".toUri())
                .putExtra(EXTRA_CATEGORY_ID, categoryId)
    }
}
