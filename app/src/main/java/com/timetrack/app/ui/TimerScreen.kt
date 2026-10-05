package com.timetrack.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timetrack.app.data.SessionWithTask
import com.timetrack.app.data.Task
import com.timetrack.app.ui.theme.Radius
import com.timetrack.app.ui.theme.Space
import com.timetrack.app.util.Fmt

/**
 * The timer page: one status object, one action, then a list.
 *
 * The page used to be four stacked cards of roughly equal weight, which left no
 * way to tell what mattered. Now exactly one container is filled — the running
 * state — because filling means "this is live" and nothing else on this page is
 * live. Everything else is grouped by typography and spacing.
 */
@Composable
fun TimerScreen(vm: AppViewModel) {
    val running by vm.running.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val recents by vm.recentTasks.collectAsStateWithLifecycle()
    val allTasks by vm.allTasks.collectAsStateWithLifecycle()
    val longRunning by vm.longRunning.collectAsStateWithLifecycle()
    val todayStat by vm.todayStat.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var showTaskAdmin by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Today's per-task totals, so a row can say what it has already cost today
    // instead of only when it was last touched.
    val todayByTask = remember(todayStat) {
        todayStat?.slices?.associate { it.taskId to it.millis }.orEmpty()
    }

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
            .padding(horizontal = Space.pageH, vertical = Space.pageV),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        RunningCard(running = running, now = now, onStop = { vm.stop() })

        LongRunningCard(
            session = longRunning,
            now = now,
            onStop = { vm.stop() },
            onFixEnd = { vm.openRunningSessions() },
        )

        NewTaskBlock(
            value = input,
            onValueChange = { input = it },
            onSubmit = { submit() },
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "最近",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Three secondary actions used to sit here as three buttons,
                // competing with the list for attention. They are one menu now.
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("补录时间") },
                            onClick = {
                                menuOpen = false
                                showManualEntry = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("管理任务") },
                            onClick = {
                                menuOpen = false
                                showTaskAdmin = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("使用说明") },
                            onClick = {
                                menuOpen = false
                                showHelp = true
                            },
                        )
                    }
                }
            }

            if (recents.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.PlayArrow,
                    title = "还没有任何任务",
                    hint = "在上面输入第一件事，回车就开始计时。之后它会出现在这里，点一下即可切换。",
                )
            } else {
                recents.forEachIndexed { index, task ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    TaskRow(
                        task = task,
                        isRunning = running?.taskId == task.id,
                        todayMillis = todayByTask[task.id] ?: 0L,
                        liveMillis = running
                            ?.takeIf { it.taskId == task.id }
                            ?.let { (now - it.startTime).coerceAtLeast(0L) },
                        onClick = { vm.startExisting(task) },
                    )
                }
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
            note = "补录不会影响正在进行的计时，随时可以改日期。",
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
    val elapsed = running?.let { (now - it.startTime).coerceAtLeast(0L) } ?: 0L
    val live = running != null

    // The only filled container in the app, because filling now means exactly one
    // thing. An idle timer is not a state worth painting, so it becomes an
    // outlined card instead — the same object, visibly switched off.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (live) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (live) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (running != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(running.taskColorArgb)),
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = running.taskName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(Space.xs))
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Sized from the space actually available: `1:02:03` has to
                    // fit on a narrow phone, and a big screen should get a clock
                    // worth looking at. Only the size is overridden — weight and
                    // tabular figures come from the display token.
                    val clockSize = (maxWidth.value / 6f).coerceIn(34f, 64f)
                    Text(
                        text = Fmt.clock(elapsed),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = clockSize.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "开始于 ${Fmt.timeOfDay(running.startTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.lg))
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
                Text(text = "当前空闲", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = "在下方输入任务名即可开始计时",
                    style = MaterialTheme.typography.bodySmall,
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
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
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
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
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

/**
 * The one action on the page.
 *
 * Not a card: a card around an input and a button made the action look like one
 * more block of content. Removing the container is what promotes it.
 */
@Composable
private fun NewTaskBlock(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val tap = rememberFirmTap()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("要做什么？") },
            placeholder = { Text("例如：写周报") },
            singleLine = true,
            shape = RoundedCornerShape(Radius.control),
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
                .height(50.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(Space.sm))
            Text("创建并开始计时")
        }
        // Earns its line twice: it explains why the button is disabled, and it
        // teaches that the keyboard's enter key does the same thing.
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

@Composable
private fun TaskRow(
    task: Task,
    isRunning: Boolean,
    todayMillis: Long,
    liveMillis: Long?,
    onClick: () -> Unit,
) {
    val tap = rememberFirmTap()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.control))
            .clickable {
                tap()
                onClick()
            }
            .padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A leading stripe instead of a filled row. Same information, said
        // rather than shouted — and it keeps filling reserved for the status card.
        Box(
            modifier = Modifier
                .width(Radius.stripe)
                .height(30.dp)
                .clip(CircleShape)
                .background(
                    if (isRunning) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color.Transparent
                    },
                ),
        )
        Spacer(Modifier.width(Space.md))
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(task.colorArgb)),
        )
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isRunning) {
                    "进行中"
                } else {
                    "最近一次：${Fmt.relativeDay(task.lastUsedAt)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = when {
                liveMillis != null -> Fmt.clock(liveMillis)
                todayMillis > 0L -> Fmt.duration(todayMillis)
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isRunning) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
