package com.timetrack.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timetrack.app.data.TaskSlice
import kotlin.math.min

/**
 * Donut chart drawn directly on a Canvas.
 *
 * Hand-rolled on purpose: a pie/donut is ~20 lines of `drawArc`, and avoiding a
 * charting dependency keeps the APK small and the build reproducible.
 *
 * Slices are expected to be sorted largest-first, so the ring reads clockwise
 * from the top with the biggest contributor first.
 */
@Composable
fun DonutChart(
    slices: List<TaskSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 30.dp,
) {
    val total = slices.sumOf { it.millis }
    if (total <= 0L) return

    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        // Leave half a stroke of margin on each side so the ring is not clipped.
        val diameter = min(size.width, size.height) - stroke
        if (diameter <= 0f) return@Canvas
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f,
        )
        val arcSize = Size(diameter, diameter)

        var startAngle = -90f
        for (slice in slices) {
            val sweep = slice.millis.toFloat() / total.toFloat() * 360f
            if (sweep <= 0f) continue
            drawArc(
                color = Color(slice.colorArgb),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}
