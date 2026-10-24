package com.nackie.ownexpenses.ui.period

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A horizontal swipe steps the period like the arrows: to the right for the previous one, to the
 * left for the next. Vertical scrolling and taps on the content keep working.
 */
@Composable
internal fun Modifier.periodSwipe(
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier {
    if (!enabled) return this
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    return pointerInput(threshold) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragEnd = {
                when {
                    distance > threshold -> previous()
                    distance < -threshold -> next()
                }
            },
            onHorizontalDrag = { change, dragAmount ->
                distance += dragAmount
                if (abs(distance) > threshold) change.consume()
            },
        )
    }
}

private val SWIPE_THRESHOLD = 72.dp
