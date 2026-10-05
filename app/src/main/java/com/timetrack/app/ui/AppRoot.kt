package com.timetrack.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import com.timetrack.app.TimeTrackApp
import com.timetrack.app.service.TimerService

private enum class Tab(val label: String, val icon: ImageVector) {
    TIMER("计时", Icons.Filled.PlayArrow),
    STATS("统计", Icons.Filled.DateRange),
    EXPORT("导出", Icons.Filled.Share),
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as TimeTrackApp
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(app.repository))

    var tab by rememberSaveable { mutableStateOf(Tab.TIMER) }
    val snackbarHostState = remember { SnackbarHostState() }

    val running by vm.running.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

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
                Tab.STATS -> StatsScreen(vm)
                Tab.EXPORT -> ExportScreen(vm)
            }
        }
    }
}
