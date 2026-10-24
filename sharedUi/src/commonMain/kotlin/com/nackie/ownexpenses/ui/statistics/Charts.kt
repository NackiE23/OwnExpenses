package com.nackie.ownexpenses.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nackie.ownexpenses.presentation.statistics.BarGranularity
import com.nackie.ownexpenses.presentation.statistics.SpendingChart
import com.nackie.ownexpenses.ui.format.dayMonth
import com.nackie.ownexpenses.ui.format.monthShort
import com.nackie.ownexpenses.ui.format.monthYear

private const val BAR_FILL = 0.7f
private const val MIN_VISIBLE_BAR_FRACTION = 0.02f
private const val DONUT_GAP_DEGREES = 1.5f

/**
 * Expense bars drawn on a Canvas. [selectedIndex] is highlighted; tapping picks the bar under
 * the finger, so the screen can show its date and amount.
 */
@Composable
internal fun SpendingBarChart(
    chart: SpendingChart,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val dimmedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val bars = chart.bars
    val max = chart.maxMinor.coerceAtLeast(1)

    Column(modifier = modifier) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .semantics { contentDescription = description }
                    .pointerInput(bars.size) {
                        detectTapGestures { offset ->
                            if (bars.isNotEmpty()) {
                                onSelect((offset.x / (size.width / bars.size)).toInt().coerceIn(0, bars.lastIndex))
                            }
                        }
                    },
        ) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val barWidth = slot * BAR_FILL
            val radius = CornerRadius(barWidth / 4, barWidth / 4)
            bars.forEachIndexed { index, bar ->
                if (bar.totalMinor == 0L) return@forEachIndexed
                val fraction = (bar.totalMinor.toFloat() / max).coerceAtLeast(MIN_VISIBLE_BAR_FRACTION)
                val barHeight = size.height * fraction
                val highlighted = selectedIndex == null || selectedIndex == index
                drawRoundRect(
                    color = if (highlighted) barColor else dimmedColor,
                    topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
            drawLine(baselineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        if (bars.isNotEmpty()) {
            AxisLabels(chart)
        }
    }
}

/** First, middle and last bar labels: enough to orient without crowding narrow screens. */
@Composable
private fun AxisLabels(chart: SpendingChart) {
    val bars = chart.bars
    val indices = listOf(0, bars.lastIndex / 2, bars.lastIndex).distinct()
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        indices.forEachIndexed { position, index ->
            val date = bars[index].start
            val label = if (chart.granularity == BarGranularity.DAY) date.day.toString() else monthShort(date)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign =
                    when {
                        indices.size == 1 -> TextAlign.Center
                        position == 0 -> TextAlign.Start
                        position == indices.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "24 жовтня" for a day bar, "Жовтень 2026" for a month bar. */
@Composable
internal fun barLabel(
    chart: SpendingChart,
    index: Int,
): String {
    val date = chart.bars[index].start
    return if (chart.granularity == BarGranularity.DAY) dayMonth(date) else monthYear(date)
}

/** One ring segment per category in the given order, sized by [shares] (0..1, summing to 1). */
@Composable
internal fun SpendingDonut(
    shares: List<Pair<Float, Color>>,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    center: @Composable () -> Unit,
) {
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size).semantics { contentDescription = description }) {
            val stroke = this.size.minDimension * 0.14f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            if (shares.isEmpty()) {
                drawArc(
                    emptyColor,
                    0f,
                    360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke),
                )
                return@Canvas
            }
            val gap = if (shares.size > 1) DONUT_GAP_DEGREES else 0f
            var start = -90f
            shares.forEach { (share, color) ->
                val sweep = 360f * share
                drawArc(
                    color = color,
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        center()
    }
}
