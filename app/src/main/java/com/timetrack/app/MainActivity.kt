package com.timetrack.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.timetrack.app.ui.AppRoot
import com.timetrack.app.ui.theme.TimeTrackTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForNotificationPermissionIfNeeded()
        handleShortcutIntent(intent)
        keepShortcutsFresh()
        setContent {
            TimeTrackTheme {
                AppRoot()
            }
        }
    }

    /**
     * Reached when the app is already running, which is the common case for a
     * shortcut: the launcher reuses the existing task instead of creating one.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent?.action != Shortcuts.ACTION_START_TASK) return
        val taskId = intent.getLongExtra(Shortcuts.EXTRA_TASK_ID, -1L)
        if (taskId > 0L) StartRequest.publish(taskId)
    }

    /**
     * Tracks the recent tasks while the app is in the foreground, which is when
     * they change. Scoped to the activity's lifecycle on purpose: there is no
     * reason to keep a collector alive in the background just to tidy a menu.
     */
    private fun keepShortcutsFresh() {
        val repository = (application as TimeTrackApp).repository
        lifecycleScope.launch {
            repository.observeRecentTasks(limit = 4).collect { tasks ->
                Shortcuts.sync(this@MainActivity, tasks)
            }
        }
    }

    /**
     * Only needed so the running-task notification is visible on Android 13+.
     * Tracking itself works whether or not this is granted.
     */
    private fun askForNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
