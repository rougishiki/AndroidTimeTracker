package com.timetrack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timetrack.app.data.ComparisonRow
import com.timetrack.app.data.DayStat
import com.timetrack.app.data.PeriodComparison
import com.timetrack.app.data.StatsMode
import com.timetrack.app.data.TaskSlice
import com.timetrack.app.ui.theme.Space
import com.timetrack.app.util.Fmt
import java.time.LocalDate

@Composable
fun StatsScreen(vm: AppViewModel) {
    val mode by vm.statsMode.collectAsStateWithLifecycle()
    val stat by vm.dayStat.collectAsStateWithLifecycle()
    val comparison by vm.periodComparison.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.pageH, vertical = Space.pageV),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            ModeSwitch(mode = mode, onSelect = { vm.setStatsMode(it) })

            DateNavigator(
                label = vm.periodLabel(),
                canGoNext = !vm.isAtCurrentPeriod(),
                onPrev = { vm.shiftPeriod(-1) },
                onNext = { vm.shiftPeriod(1) },
                onToday = { vm.goToToday() },
            )
        }

        if (mode == StatsMode.DAY) {
            val current = stat
            when {
                current == null -> LoadingText()

                current.slices.isEmpty() -> EmptyState(
                    icon = Icons.Filled.DateRange,
                    title = "这一天没有记录",
                    hint = "去「计时」页开始一段，统计会自动出现在这里。",
                )

                else -> {
                    PeriodTotalBlock(
                        label = "当日总计",
                        totalMillis = current.totalMillis,
                        secondary = "${current.slices.size} 项任务" +
                            averageSuffix(current.totalMillis, current.intervalCount),
                    )
                    DonutSection(
                        totalMillis = current.totalMillis,
                        slices = current.slices,
                    )
                    BarSection(
                        slices = current.slices,
                        total = current.totalMillis,
                        onSliceClick = { vm.openTaskSessions(it) },
                    )
                }
            }
        } else {
            val current = comparison
            when {
                current == null -> LoadingText()

                current.currentTotalMillis == 0L && current.previousTotalMillis == 0L ->
                    EmptyState(
                        icon = Icons.Filled.DateRange,
                        title = if (mode == StatsMode.WEEK) "这一周没有记录" else "这个月没有记录",
                        hint = "左右翻到别的期看看，或者去「计时」页开始一段。",
                    )

                else -> {
                    ComparisonTotalBlock(current, mode)
                    ComparisonSection(current)
                }
            }
        }
    }

    // The interval editor dialog is hosted in AppRoot rather than here, so the
    // reminder card on the timer screen can open the very same one.
}

@Composable
private fun LoadingText() {
    Text(
        text = "正在统计…",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModeSwitch(mode: StatsMode, onSelect: (StatsMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        StatsMode.entries.forEachIndexed { index, item ->
            SegmentedButton(
                selected = mode == item,
                onClick = { onSelect(item) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = StatsMode.entries.size,
                ),
            ) {
                Text(item.label)
            }
        }
    }
}

@Composable
private fun DateNavigator(
    label: String,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一天")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canGoNext) {
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "后一天")
            }
        } else {
            TextButton(onClick = onToday) { Text("今天") }
        }
    }
}

/**
 * The period's headline figure, with no container around it.
 *
 * It used to sit in a card, which gave the one number that matters the same
 * visual weight as everything else on the page. Removing the container is what
 * promotes it: the 34sp total against two 12.5sp lines underneath is the whole
 * hierarchy, and it costs no background at all.
 */
@Composable
private fun PeriodTotalBlock(label: String, totalMillis: Long, secondary: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = Fmt.duration(totalMillis),
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** ` · 平均每次 40分`, or nothing when there is no interval to average. */
private fun averageSuffix(totalMillis: Long, intervalCount: Int): String {
    if (intervalCount <= 0) return ""
    return " · 平均每次 ${Fmt.duration(totalMillis / intervalCount)}"
}

/**
 * Share of the day, at a glance.
 *
 * The ring is paired with the bar list below rather than replaced by it, because
 * the two answer different questions: the ring says *how the day split up*, the
 * bars say *how much exactly, and which task*. Removing the ring as "redundant"
 * was wrong — the bars were doing double duty as its legend, and a legend is not
 * a substitute for the thing it labels.
 */
@Composable
private fun DonutSection(totalMillis: Long, slices: List<TaskSlice>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        DonutChart(
            slices = slices,
            modifier = Modifier.size(190.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Fmt.duration(totalMillis),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "总计",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BarSection(
    slices: List<TaskSlice>,
    total: Long,
    onSliceClick: (TaskSlice) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            text = "各项时长",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        slices.forEach { slice ->
            BarRow(slice = slice, total = total, onClick = { onSliceClick(slice) })
        }
    }
}

@Composable
private fun BarRow(slice: TaskSlice, total: Long, onClick: () -> Unit) {
    val tap = rememberFirmTap()
    val fraction = if (total <= 0L) 0f else (slice.millis.toFloat() / total.toFloat())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                tap()
                onClick()
            }
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(slice.colorArgb)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = slice.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = Fmt.duration(slice.millis),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = percent(slice.millis, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    // Keep very small slices visible instead of collapsing to nothing.
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(slice.colorArgb)),
            )
        }
    }
}

private fun percent(part: Long, total: Long): String =
    if (total <= 0L) "0%" else String.format(java.util.Locale.US, "%.0f%%", part * 100.0 / total)

// --- the week / month comparison view ----------------------------------------

@Composable
private fun ComparisonTotalBlock(comparison: PeriodComparison, mode: StatsMode) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (mode == StatsMode.WEEK) "本周总计" else "本月总计",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = Fmt.duration(comparison.currentTotalMillis),
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "上期 ${Fmt.duration(comparison.previousTotalMillis)} · " +
                deltaText(comparison.deltaMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${Fmt.monthDay(comparison.currentStart)} – ${Fmt.monthDay(comparison.currentEndInclusive)}" +
                " · 对比 ${Fmt.monthDay(comparison.previousStart)} – ${Fmt.monthDay(comparison.previousEndInclusive)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComparisonSection(comparison: PeriodComparison) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            text = "与上一期对比",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (comparison.rows.isEmpty()) {
            Text(
                text = "两期都没有记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        comparison.rows.forEach { row -> ComparisonRowItem(row) }
    }
}

@Composable
private fun ComparisonRowItem(row: ComparisonRow) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(row.colorArgb)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = Fmt.duration(row.currentMillis),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "上期 ${Fmt.duration(row.previousMillis)} · ${deltaText(row.deltaMillis)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * `↑ 12分` / `↓ 12分` / `持平`.
 *
 * Deliberately neither colour-coded nor emphasised. Whether more time on a task
 * is good or bad depends entirely on the task, and a comparison is context, not a
 * verdict. The previous version set these in full-contrast ink and right-aligned
 * them, which turned a footnote into the loudest thing on the page.
 */
private fun deltaText(deltaMillis: Long): String = when {
    deltaMillis > 0L -> "↑ ${Fmt.duration(deltaMillis)}"
    deltaMillis < 0L -> "↓ ${Fmt.duration(-deltaMillis)}"
    else -> "持平"
}
