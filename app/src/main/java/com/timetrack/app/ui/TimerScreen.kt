package com.timetrack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timetrack.app.data.SessionWithTask
import com.timetrack.app.data.Task
import com.timetrack.app.util.Fmt

@Composable
fun TimerScreen(vm: AppViewModel) {
    val running by vm.running.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val recents by vm.recentTasks.collectAsStateWithLifecycle()
    val allTasks by vm.allTasks.collectAsStateWithLifecycle()
    val longRunning by vm.longRunning.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var showTaskAdmin by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (input.isBlank()) return
        vm.startNew(input)
        input = ""
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RunningCard(running = running, now = now, onStop = { vm.stop() })

        LongRunningCard(
            session = longRunning,
            now = now,
            onStop = { vm.stop() },
            onFixEnd = { vm.openRunningSessions() },
        )

        NewTaskCard(
            value = input,
            onValueChange = { input = it },
            onSubmit = { submit() },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (recents.isEmpty()) "还没有任务" else "点击即可切换任务",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            // Backfilling and tidying up both belong next to the task list, not
            // buried in a settings screen.
            TextButton(onClick = { showManualEntry = true }) { Text("补录时间") }
            TextButton(onClick = { showTaskAdmin = true }) { Text("管理任务") }
            IconButton(onClick = { showHelp = true }) {
                Icon(Icons.Filled.Info, contentDescription = "使用说明")
            }
        }

        if (recents.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Filled.PlayArrow,
                    title = "还没有任何任务",
                    hint = "在上面输入第一件事，回车就开始计时。之后它会出现在这里，点一下即可切换。",
                )
            }
        } else {
            recents.forEach { task ->
                TaskRow(
                    task = task,
                    isRunning = running?.taskId == task.id,
                    onClick = { vm.startExisting(task) },
                )
            }
        }
    }

    if (showManualEntry) {
        SessionTimeDialog(
            title = "补录时间",
            initialDay = vm.today(),
            initialStartMinutes = (vm.nowMinutes() - 60).coerceAtLeast(0),
            initialEndMinutes = vm.nowMinutes(),
            lengthOf = { d, s, e -> vm.lengthOf(d, s, e) },
            nameLabel = "任务名",
            note = "补记不会影响正在进行的计时，随时可以改日期。",
            onConfirm = { name, day, start, end ->
                vm.addManualEntry(name, day, start, end)
                showManualEntry = false
            },
            onDismiss = { showManualEntry = false },
        )
    }

    if (showTaskAdmin) {
        TaskAdminDialog(
            tasks = allTasks,
            onRename = { task, name -> vm.renameTask(task, name) },
            onArchive = { task, archived -> vm.setTaskArchived(task, archived) },
            onDismiss = { showTaskAdmin = false },
        )
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }
}

@Composable
private fun RunningCard(running: SessionWithTask?, now: Long, onStop: () -> Unit) {
    val tap = rememberFirmTap()
    val isRunning = running != null
    val elapsed = running?.let { (now - it.startTime).coerceAtLeast(0L) } ?: 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (running != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(running.taskColorArgb)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = running.taskName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Sized from the space actually available: `1:02:03` has to
                    // fit on a narrow phone instead of being clipped, and a big
                    // screen should get a clock worth looking at.
                    val clockSize = (maxWidth.value / 6f).coerceIn(34f, 64f).sp
                    Text(
                        text = Fmt.clock(elapsed),
                        fontSize = clockSize,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "开始于 ${Fmt.timeOfDay(running.startTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        tap()
                        onStop()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("停止计时", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text("当前没有在计时的任务", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "在下方输入要做什么，点开始或按回车",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Shown once an interval has been open long enough to look forgotten.
 *
 * Deliberately a card rather than a popup: it is the answer to "did you walk
 * away?", so it should sit there until the question is answered, not demand a
 * tap on the way past. It offers both honest fixes — end it now, or end it
 * earlier — because "I stopped an hour ago" is the usual truth.
 */
@Composable
private fun LongRunningCard(
    session: SessionWithTask?,
    now: Long,
    onStop: () -> Unit,
    onFixEnd: () -> Unit,
) {
    val tap = rememberFirmTap()
    if (session == null) return
    val elapsed = (now - session.startTime).coerceAtLeast(0L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "还在做这件事吗？",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "「${session.taskName}」已经连续计时 ${Fmt.duration(elapsed)}，" +
                    "从 ${Fmt.dateTime(session.startTime)} 开始。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "如果早就结束了，可以结束到现在，或者改成更早的时间。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        tap()
                        onStop()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("结束到现在")
                }
                OutlinedButton(
                    onClick = {
                        tap()
                        onFixEnd()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("改成更早")
                }
            }
        }
    }
}

@Composable
private fun NewTaskCard(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val tap = rememberFirmTap()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("要做什么？") },
                placeholder = { Text("例如：写周报") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清空")
                        }
                    }
                },
            )
            Button(
                onClick = {
                    tap()
                    onSubmit()
                },
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("创建并开始计时")
            }
            // Earns its line twice: it explains why the button is disabled, and
            // it teaches that the keyboard's enter key does the same thing.
            Text(
                text = if (value.isBlank()) {
                    "输入任务名后即可创建并开始"
                } else {
                    "回车也可以直接开始"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskRow(task: Task, isRunning: Boolean, onClick: () -> Unit) {
    val tap = rememberFirmTap()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                tap()
                onClick()
            },
        shape = RoundedCornerShape(14.dp),
        color = if (isRunning) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(task.colorArgb)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "上次 ${Fmt.relativeDay(task.lastUsedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isRunning) {
                Text(
                    text = "计时中",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
