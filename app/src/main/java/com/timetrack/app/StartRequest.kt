package com.timetrack.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A task the user asked to start from outside the app, currently only from a
 * launcher shortcut.
 *
 * A process-level hand-off rather than a value threaded through `setContent`,
 * because the same intent also arrives while the app is already running (via
 * `onNewIntent`), when no new composition is created. The Activity publishes,
 * the composition consumes, and neither needs to know about the other.
 */
object StartRequest {

    private val _taskId = MutableStateFlow<Long?>(null)
    val taskId: StateFlow<Long?> = _taskId.asStateFlow()

    fun publish(taskId: Long) {
        _taskId.value = taskId
    }

    fun consume() {
        _taskId.value = null
    }
}
