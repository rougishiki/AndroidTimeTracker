package com.timetrack.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timetrack.app.data.SessionTimes
import com.timetrack.app.data.SessionWithTask
import com.timetrack.app.data.Task
import com.timetrack.app.util.Fmt
import java.time.LocalDate

/**
 * Dialogs shared by the timer, statistics and todo screens.
 *
 * They mutate through explicit callbacks rather than taking the view model, so
 * one dialog can serve "edit an existing interval", "backfill another one" and
 * "record the first one" without three near-copies.
 */

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String = "确定",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * Edits one interval as a date plus two times of day.
 *
 * The user is never asked to type a date: the end being earlier than the start
 * is read as the next day, so a session that ran past midnight is editable with
 * the same two fields as any other. An identical pair is refused rather than
 * turned into an invented 24 hours.
 *
 * [initialDay] anchors the times and is editable, because "I recorded this on
 * the wrong day" is the same class of mistake as a wrong time. Callers pass the
 * day the interval itself belongs to, never the day currently on screen: an
 * interval spanning midnight appears on two days, and anchoring on the viewed
 * one would silently shift it by a day the moment the user pressed save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionTimeDialog(
    title: String,
    initialDay: LocalDate,
    initialStartMinutes: Int,
    initialEndMinutes: Int,
    lengthOf: (LocalDate, Int, Int) -> Long?,
    nameLabel: String? = null,
    initialName: String = "",
    note: String? = null,
    onConfirm: (name: String, day: LocalDate, startMinutes: Int, endMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val startState = rememberTimePickerState(
        initialHour = SessionTimes.hourOf(initialStartMinutes),
        initialMinute = SessionTimes.minuteOf(initialStartMinutes),
        is24Hour = true,
    )
    val endState = rememberTimePickerState(
        initialHour = SessionTimes.hourOf(initialEndMinutes),
        initialMinute = SessionTimes.minuteOf(initialEndMinutes),
        is24Hour = true,
    )
    var name by rememberSaveable { mutableStateOf(initialName) }
    var dayEpoch by rememberSaveable { mutableStateOf(initialDay.toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayEpoch)

    val startMinutes = SessionTimes.toMinutes(startState.hour, startState.minute)
    val endMinutes = SessionTimes.toMinutes(endState.hour, endState.minute)
    val identical = startMinutes == endMinutes
    val crosses = endMinutes < startMinutes
    // Resolved through the caller's zone, so the preview equals what saving will
    // store even on a daylight-saving day.
    val length = lengthOf(day, startMinutes, endMinutes)
    val lengthSuffix = length?.let { " · 共 ${Fmt.duration(it)}" }.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (nameLabel != null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(nameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { dayEpoch -= 1 }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "前一天",
                        )
                    }
                    Text(
                        text = "${Fmt.monthDay(day)} ${Fmt.weekdayShort(day)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { dayEpoch += 1 }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "后一天",
                        )
                    }
                }

                Text("开始", style = MaterialTheme.typography.labelSmall)
                TimeInput(state = startState)

                Text("结束", style = MaterialTheme.typography.labelSmall)
                TimeInput(state = endState)

                Text(
                    text = when {
                        identical -> "结束时间和开始时间相同，请改一个"
                        crosses -> "结束早于开始，记为次日 ${SessionTimes.label(endMinutes)} 结束$lengthSuffix"
                        else -> "同一天内$lengthSuffix"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (identical) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), day, startMinutes, endMinutes) },
                enabled = !identical && (nameLabel == null || name.isNotBlank()),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * The intervals behind one task's total on one day.
 *
 * The statistics screen only ever shows an aggregate, so a mistake in it is both
 * invisible and unfixable. This exposes the actual rows.
 */
@Composable
fun TaskSessionsDialog(
    state: TaskSessionsState,
    nowMillis: Long,
    minutesOf: (Long) -> Int,
    dayOf: (Long) -> LocalDate,
    lengthOf: (LocalDate, Int, Int) -> Long?,
    onRetime: (Long, LocalDate, Int, Int) -> Unit,
    onDelete: (Long) -> Unit,
    onAdd: (LocalDate, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<SessionWithTask?>(null) }
    var adding by remember { mutableStateOf(false) }
    // Deleting an interval is the one genuinely irreversible action here, so it
    // goes through a confirmation rather than firing on the first tap.
    var deleting by remember { mutableStateOf<SessionWithTask?>(null) }

    val total = state.sessions.sumOf { s ->
        ((s.endTime ?: nowMillis).coerceAtLeast(s.startTime)) - s.startTime
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.taskName) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${Fmt.monthDay(state.day)} · ${state.sessions.size} 段 · 共 ${Fmt.duration(total)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                if (state.sessions.isEmpty()) {
                    Text(
                        text = "这一天已经没有记录了。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        nowMillis = nowMillis,
                        minutesOf = minutesOf,
                        onEdit = { editing = session },
                        onDelete = { deleting = session },
                    )
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("补录时间段") }
            }
        },
    )

    // The static footer that used to sit at the bottom of this dialog said two
    // things. One — "an earlier end means the next day" — is already said inline
    // by the editor the moment the two times cross. The other belongs where the
    // user actually needs it: in front of the delete.
    deleting?.let { session ->
        val start = minutesOf(session.startTime)
        val end = session.endTime?.let(minutesOf)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这一段？") },
            text = {
                Text(
                    text = buildString {
                        append(Fmt.monthDay(state.day))
                        append("  ")
                        append(SessionTimes.label(start))
                        if (end != null) append(" – ${SessionTimes.label(end)}")
                        append("\n删除后无法恢复，这一天的统计会同步减少。")
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(session.id)
                        deleting = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }

    editing?.let { session ->
        SessionTimeDialog(
            title = "修改时间段",
            // The interval's own day, not the day being viewed. See the note on
            // SessionTimeDialog for why that distinction matters.
            initialDay = dayOf(session.startTime),
            initialStartMinutes = minutesOf(session.startTime),
            initialEndMinutes = session.endTime?.let(minutesOf) ?: minutesOf(nowMillis),
            lengthOf = lengthOf,
            note = if (session.endTime == null) "这一段还在计时，填上结束时间就会停止。" else null,
            onConfirm = { _, day, start, end ->
                onRetime(session.id, day, start, end)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (adding) {
        SessionTimeDialog(
            title = "补记一段",
            initialDay = state.day,
            // A plausible hour ending just now, which is what a forgotten
            // interval usually looks like.
            initialStartMinutes = (minutesOf(nowMillis) - 60).coerceAtLeast(0),
            initialEndMinutes = minutesOf(nowMillis),
            lengthOf = lengthOf,
            onConfirm = { _, day, start, end ->
                onAdd(day, start, end)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun SessionRow(
    session: SessionWithTask,
    nowMillis: Long,
    minutesOf: (Long) -> Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val start = minutesOf(session.startTime)
    val end = session.endTime?.let(minutesOf)
    val length = ((session.endTime ?: nowMillis).coerceAtLeast(session.startTime)) - session.startTime

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (end != null) {
                    "${SessionTimes.label(start)} – ${SessionTimes.label(end)}"
                } else {
                    "${SessionTimes.label(start)} – 进行中"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = buildString {
                    append(Fmt.duration(length))
                    if (end != null && end < start) append(" · 次日结束")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "修改")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除")
        }
    }
}

/**
 * Rename, archive and restore.
 *
 * Renaming onto a name that is already taken merges the two tasks, which is the
 * only way to repair a statistics list split by near-identical names — and the
 * reason there is no delete here at all: statistics reference tasks by id, so
 * deleting one would silently drop its history out of every report.
 */
@Composable
fun TaskAdminDialog(
    tasks: List<Task>,
    onRename: (Task, String) -> Unit,
    onArchive: (Task, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf<Task?>(null) }
    val active = tasks.filterNot { it.archived }
    val archived = tasks.filter { it.archived }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理任务") },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // The paragraph explaining merging used to sit here. Renaming
                // onto a taken name already answers with "已合并到「X」", so the
                // paragraph was pre-empting a message the user will actually see.
                if (active.isEmpty()) {
                    Text(
                        text = "还没有任何任务。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                active.forEach { task ->
                    TaskAdminRow(
                        task = task,
                        onRename = { renaming = task },
                        onArchive = { onArchive(task, true) },
                    )
                }

                if (archived.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "已归档 ${archived.size} 个（不出现在计时页）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    archived.forEach { task ->
                        TaskAdminRow(
                            task = task,
                            onRename = { renaming = task },
                            onArchive = { onArchive(task, false) },
                        )
                    }
                }
            }
        },
    )

    renaming?.let { task ->
        TextInputDialog(
            title = "重命名任务",
            label = "任务名",
            initial = task.name,
            onConfirm = {
                onRename(task, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun TaskAdminRow(
    task: Task,
    onRename: () -> Unit,
    onArchive: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = task.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onRename) { Text("重命名") }
        TextButton(onClick = onArchive) {
            Text(if (task.archived) "恢复" else "归档")
        }
    }
}
