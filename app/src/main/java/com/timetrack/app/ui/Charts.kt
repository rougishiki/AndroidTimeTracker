package com.timetrack.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timetrack.app.data.TaskSlice
import kotlin.math.atan2
import kotlin.math.hypot
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
 * through periods look like a transition instead of a jump cut. The animation is
 * keyed on *which* tasks are present rather than on their durations, because a
 * running task changes its value every second: keying on the values would restart
 * the sweep on every tick and make the chart twitch rather than breathe.
 *
 * When [onSliceClick] is supplied the ring becomes a control: a tap resolves to
 * the sector under the finger.
 */
@Composable
fun DonutChart(
    slices: List<TaskSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 30.dp,
    onSliceClick: ((TaskSlice) -> Unit)? = null,
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

    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val sweeps = slices.map { it.millis.toFloat() / total.toFloat() * 360f }

    // Read through updated-state so the gesture handler can be installed once.
    // Keying pointerInput on the sweeps would tear the handler down and rebuild it
    // every second, because a running task changes its duration on every tick.
    val latestSlices by rememberUpdatedState(slices)
    val latestSweeps by rememberUpdatedState(sweeps)
    val latestOnClick by rememberUpdatedState(onSliceClick)

    val gestureModifier = if (onSliceClick == null) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val index = sliceIndexAt(
                    pointX = offset.x,
                    pointY = offset.y,
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                    strokePx = strokePx,
                    sweeps = latestSweeps,
                )
                if (index in latestSlices.indices) {
                    latestOnClick?.invoke(latestSlices[index])
                }
            }
        }
    }

    Canvas(modifier.then(gestureModifier)) {
        // Leave half a stroke of margin on each side so the ring is not clipped.
        val diameter = min(size.width, size.height) - strokePx
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
                style = Stroke(width = strokePx, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}

/**
 * Which slice contains the point, or -1 for none.
 *
 * Pure geometry over plain floats, deliberately: hit testing is the kind of code
 * that goes wrong on exactly one edge case — a tap at twelve o'clock, or in the
 * hole — and is miserable to debug through a touchscreen. This way the edge cases
 * are unit tests. See `DonutHitTestTest`.
 *
 * Angles follow the convention `DrawScope.drawArc` uses: degrees clockwise from
 * 3 o'clock in screen coordinates, with the ring starting at -90 (12 o'clock).
 *
 * [sweeps] are the *full* sector angles, not the animating ones, so a tap in a
 * sector that is still sweeping in still lands.
 */
internal fun sliceIndexAt(
    pointX: Float,
    pointY: Float,
    width: Float,
    height: Float,
    strokePx: Float,
    sweeps: List<Float>,
): Int {
    val centreX = width / 2f
    val centreY = height / 2f
    val centreRadius = (min(width, height) - strokePx) / 2f
    val inner = centreRadius - strokePx / 2f
    val outer = centreRadius + strokePx / 2f

    val dx = pointX - centreX
    val dy = pointY - centreY
    val distance = hypot(dx, dy)
    // The hole and everything beyond the ring belong to no sector.
    if (distance < inner || distance > outer) return -1

    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
    val fromStart = ((degrees + 90.0) % 360.0 + 360.0) % 360.0

    var accumulated = 0.0
    sweeps.forEachIndexed { index, sweep ->
        accumulated += sweep
        if (fromStart < accumulated) return index
    }
    return -1
}
