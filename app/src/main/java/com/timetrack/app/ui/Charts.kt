package com.timetrack.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
 *
 * The ring sweeps in when the set of tasks changes, which is what makes stepping
 * through days look like a transition instead of a jump cut. The animation is
 * keyed on *which* tasks are present rather than on their durations, because a
 * running task changes its value every second: keying on the values would
 * restart the sweep on every tick and make the chart twitch rather than breathe.
 * The per-second growth still shows, because the slice proportions are read from
 * the current list while the sweep plays.
 */
@Composable
fun DonutChart(
    slices: List<TaskSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 30.dp,
) {
    val total = slices.sumOf { it.millis }
    if (total <= 0L) return

    val signature = slices.joinToString(",") { it.taskId.toString() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(signature) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        )
    }

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
        val scale = progress.value

        var startAngle = -90f
        for (slice in slices) {
            val sweep = slice.millis.toFloat() / total.toFloat() * 360f * scale
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
