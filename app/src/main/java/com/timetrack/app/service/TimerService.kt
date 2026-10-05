package com.timetrack.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.timetrack.app.MainActivity
import com.timetrack.app.R

/**
 * Keeps the running task visible in the notification shade.
 *
 * This service owns **no** tracking state on purpose: the authoritative interval
 * lives in the database, and the notification renders elapsed time with the
 * system chronometer ([NotificationCompat.Builder.setUsesChronometer] plus
 * `setWhen`). That means the notification never has to be refreshed while a task
 * runs, so there is no per-second wakeup and no wakelock.
 *
 * Every failure path degrades to "no notification" rather than "no tracking".
 */
class TimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW) {
            val name = intent.getStringExtra(EXTRA_TASK_NAME).orEmpty()
            val since = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
            try {
                showForeground(name, since)
            } catch (t: Throwable) {
                // Missing POST_NOTIFICATIONS, a foreground-service restriction on
                // Android 12+, or a missing specialUse declaration. None of these
                // should stop the user's timer, which is database-backed.
                stopSelf()
            }
        } else {
            stopTracking()
        }
        return START_NOT_STICKY
    }

    private fun showForeground(taskName: String, since: Long) {
        ensureChannel()
        val notification = buildNotification(taskName, since)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopTracking() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(taskName: String, since: Long) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(taskName.ifBlank { "正在计时" })
            .setContentText("点击回到应用")
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(since)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "计时状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "在通知栏显示当前正在计时的任务"
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "time_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_SHOW = "com.timetrack.app.action.SHOW"
        private const val EXTRA_TASK_NAME = "extra_task_name"
        private const val EXTRA_START_TIME = "extra_start_time"

        /** Shows (or updates) the running-task notification. */
        fun show(context: Context, taskName: String, since: Long) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_START_TIME, since)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                // Ignore: the timer itself does not depend on the notification.
            }
        }

        /** Tears the notification down. Uses stopService to avoid background-start limits. */
        fun hide(context: Context) {
            try {
                context.stopService(Intent(context, TimerService::class.java))
            } catch (t: Throwable) {
                // Ignore.
            }
        }
    }
}
