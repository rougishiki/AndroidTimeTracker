package com.timetrack.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timetrack.app.R
import com.timetrack.app.StartRequest
import com.timetrack.app.TimeTrackApp
import com.timetrack.app.service.TimerService

/**
 * Icons are local vectors rather than `material-icons-core`: that set has no
 * download glyph, and dropping one filled icon into an otherwise outline row is
 * exactly the inconsistency that makes an app look assembled rather than
 * designed. Four vectors cost a few KB and let the whole row share one spec —
 * 24dp grid, 1.75dp stroke, round caps.
 *
 * "导出" is called "备份" because that is the user's mental model: the point is
 * not to produce a file, it is to not lose the data.
 */
private enum class Tab(val label: String, @DrawableRes val iconRes: Int) {
    TIMER("计时", R.drawable.ic_nav_timer),
    TODO("待办", R.drawable.ic_nav_todo),
    STATS("统计", R.drawable.ic_nav_stats),
    BACKUP("备份", R.drawable.ic_nav_backup),
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as TimeTrackApp
    val vm: AppViewModel = viewModel(
        factory = AppViewModel.factory(app.repository, app.todoRepository),
    )
    val todoVm: TodoViewModel = viewModel(
        factory = TodoViewModel.factory(app.todoRepository),
    )

    var tab by rememberSaveable { mutableStateOf(Tab.TIMER) }
    val snackbarHostState = remember { SnackbarHostState() }

    val running by vm.running.collectAsStateWithLifecycle()
    val longRunning by vm.longRunning.collectAsStateWithLifecycle()
    val taskSessions by vm.taskSessions.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val todoMessage by todoVm.message.collectAsStateWithLifecycle()

    // Notification is a pure projection of observed state, which keeps the
    // ViewModel free of any Context dependency.
    LaunchedEffect(running?.id) {
        val current = running
        if (current != null) {
            TimerService.show(context, current.taskName, current.startTime)
        } else {
            TimerService.hide(context)
        }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        vm.consumeMessage()
    }

    // Both view models share one host so a todo action and a timer action can
    // never stack two snackbars on top of each other.
    LaunchedEffect(todoMessage) {
        val text = todoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        todoVm.consumeMessage()
    }

    // The "did you forget to stop?" reminder, read from observed state rather
    // than driven by an alarm — see AppViewModel.longRunning for why. Keyed on
    // the interval so it fires once per session rather than once per
    // recomposition.
    LaunchedEffect(longRunning?.id) {
        val stuck = longRunning ?: return@LaunchedEffect
        snackbarHostState.showSnackbar("「${stuck.taskName}」已经计时很久了，确认还在进行吗？")
    }

    // A launcher shortcut asks for a task to start. Consumed once so a
    // recomposition cannot start it a second time.
    val pendingStart by StartRequest.taskId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingStart) {
        val taskId = pendingStart ?: return@LaunchedEffect
        StartRequest.consume()
        vm.startTaskById(taskId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomBar(current = tab, onSelect = { tab = it })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when (tab) {
                Tab.TIMER -> TimerScreen(vm)
                Tab.TODO -> TodoScreen(todoVm)
                Tab.STATS -> StatsScreen(vm)
                Tab.BACKUP -> ExportScreen(vm)
            }
        }
    }

    // Hosted at the app level rather than on the statistics screen, because the
    // reminder card on the timer screen opens this same editor — and because a
    // dialog should not vanish when the user switches tabs.
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

/**
 * The bottom bar, hand-built for one reason: Material's `NavigationBar` draws a
 * filled pill behind the selected item, and with four tabs that pill is one of
 * the largest patches of colour on the screen. A 3dp line above the icon says
 * the same thing at a fraction of the visual weight — and in an interface whose
 * only real colours are the user's task colours, that matters.
 *
 * `selectable` rather than `clickable` so the items still introduce themselves
 * to a screen reader as tabs with a selected state.
 */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Tab.entries.forEach { tab ->
                    val selected = tab == current
                    val tint = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = selected,
                                onClick = { onSelect(tab) },
                                role = Role.Tab,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(if (selected) tint else Color.Transparent),
                        )
                        Spacer(Modifier.height(6.dp))
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = tint,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                        )
                    }
                }
            }
        }
    }
}
