package com.timetrack.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.timetrack.app.TimeTrackApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Stops the running interval when the notification's action button is pressed.
 *
 * A receiver rather than a direct call because the button lives in the
 * notification shade, outside any composition: there is no view model to ask.
 * It reaches the same repository the UI uses, so the rule that at most one
 * interval is open still holds and the statistics update by themselves.
 *
 * It also has to take the notification down itself. The UI normally projects the
 * notification from observed state, but that projection only runs while the
 * composition is alive — and pressing this button usually happens precisely when
 * the app is in the background, where no composition exists.
 *
 * `goAsync` is used because [com.timetrack.app.data.TimeTrackRepository.stopRunning]
 * is a suspend function that runs a database transaction, which outlives the
 * few milliseconds a receiver is normally allowed.
 */
class StopTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        val app = context.applicationContext as? TimeTrackApp ?: return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.repository.stopRunning()
                TimerService.hide(context)
            } catch (t: Throwable) {
                // Nothing useful to say here. If stopping failed the interval is
                // still open and the notification still shows it, which is the
                // honest outcome rather than a silent lie.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.timetrack.app.action.STOP"
    }
}
