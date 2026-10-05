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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.timetrack.app.data.DayStat
import com.timetrack.app.data.TaskSlice
import com.timetrack.app.util.Fmt
import java.time.LocalDate

@Composable
fun StatsScreen(vm: AppViewModel) {
    val date by vm.statsDate.collectAsStateWithLifecycle()
    val stat by vm.dayStat.collectAsStateWithLifecycle()
    val taskSessions by vm.taskSessions.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DateNavigator(
            label = Fmt.dayLabel(date, today),
            canGoNext = date.isBefore(today),
            onPrev = { vm.shiftDate(-1) },
            onNext = { vm.shiftDate(1) },
            onToday = { vm.goToToday() },
        )

        val current = stat
        when {
            current == null -> Text(
                text = "正在统计…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            current.slices.isEmpty() -> EmptyDayCard()

            else -> {
                TotalCard(current)
                PieCard(current)
                BarCard(current, onSliceClick = { vm.openTaskSessions(it) })
                Text(
                    text = "点某一项可以查看并修改它这一天的时间段。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // The aggregate above is what the day looks like; this is where the user
    // reaches the rows behind it, which is the only way to repair a mistake.
    taskSessions?.let { state ->
        TaskSessionsDialog(
            state = state,
            nowMillis = now,
            minutesOf = { vm.minutesOf(it) },
            dayOf = { vm.dayOf(it) },
            lengthOf = { d, s, e -> vm.lengthOf(d, s, e) },
            onRetime = { id, day, start, end -> vm.retimeSession(id, day, start, end) },
            onDelete = { id -> vm.deleteSession(id) },
            onAdd = { day, start, end -> vm.addSessionToCurrentTask(day, start, end) },
            onDismiss = { vm.closeTaskSessions() },
        )
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

@Composable
private fun TotalCard(stat: DayStat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当日总计",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Fmt.duration(stat.totalMillis),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${stat.slices.size} 项任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${Fmt.hours(stat.totalMillis)} 小时",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PieCard(stat: DayStat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text("时长占比", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                DonutChart(
                    slices = stat.slices,
                    modifier = Modifier.size(190.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = Fmt.duration(stat.totalMillis),
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
    }
}

@Composable
private fun BarCard(stat: DayStat, onSliceClick: (TaskSlice) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("各项时长", style = MaterialTheme.typography.titleMedium)
            stat.slices.forEach { slice ->
                BarRow(
                    slice = slice,
                    total = stat.totalMillis,
                    onClick = { onSliceClick(slice) },
                )
            }
        }
    }
}

@Composable
private fun BarRow(slice: TaskSlice, total: Long, onClick: () -> Unit) {
    val fraction = if (total <= 0L) 0f else (slice.millis.toFloat() / total.toFloat())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
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

@Composable
private fun EmptyDayCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("这一天没有记录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "去「计时」页开始一段，统计会自动出现在这里",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun percent(part: Long, total: Long): String =
    if (total <= 0L) "0%" else String.format(java.util.Locale.US, "%.0f%%", part * 100.0 / total)
