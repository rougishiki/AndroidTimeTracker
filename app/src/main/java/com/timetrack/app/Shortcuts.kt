package com.timetrack.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.timetrack.app.data.Task

/**
 * Keeps the launcher's long-press shortcuts in step with the most recent tasks.
 *
 * Starting a timer used to take five steps: unlock, find the icon, open the app,
 * type the name, press start. A shortcut makes the same thing two, which matters
 * because it is the single most repeated action in the app.
 *
 * Dynamic rather than static shortcuts, because the point is to offer the tasks
 * this user actually uses; a static XML list can only hold fixed entries.
 *
 * The list is refreshed while the app is open — which is exactly when the recent
 * tasks change — so it never needs a background job.
 */
object Shortcuts {

    const val ACTION_START_TASK = "com.timetrack.app.action.START_TASK"
    const val EXTRA_TASK_ID = "extra_task_id"

    /** Four is the useful number in a long-press menu; launchers cap it anyway. */
    private const val WANTED = 4

    fun sync(context: Context, tasks: List<Task>) {
        val limit = minOf(WANTED, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
        if (limit <= 0) return

        val shortcuts = tasks.take(limit).map { task ->
            ShortcutInfoCompat.Builder(context, "task-${task.id}")
                .setShortLabel(task.name)
                .setLongLabel("开始计时：${task.name}")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = ACTION_START_TASK
                        putExtra(EXTRA_TASK_ID, task.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                )
                .build()
        }

        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (t: Throwable) {
            // Some launchers reject the request, and the platform rate-limits it.
            // A missing shortcut must never take the app down with it.
        }
    }
}
