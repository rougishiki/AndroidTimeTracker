package com.timetrack.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timetrack.app.StartRequest
import com.timetrack.app.TimeTrackApp
import com.timetrack.app.service.TimerService

private enum class Tab(val label: String, val icon: ImageVector) {
    TIMER("计时", Icons.Filled.PlayArrow),
    TODO("待办", Icons.Filled.CheckCircle),
    STATS("统计", Icons.Filled.DateRange),
    EXPORT("导出", Icons.Filled.Share),
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
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
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
                Tab.EXPORT -> ExportScreen(vm)
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
