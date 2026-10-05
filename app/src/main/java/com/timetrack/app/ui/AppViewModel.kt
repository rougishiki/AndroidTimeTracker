package com.timetrack.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timetrack.app.data.DayStat
import com.timetrack.app.data.ExportRange
import com.timetrack.app.data.SessionWithTask
import com.timetrack.app.data.Task
import com.timetrack.app.data.TimeTrackRepository
import com.timetrack.app.data.TodoRepository
import com.timetrack.app.util.Exporter
import com.timetrack.app.util.Fmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A rendered export body plus what it holds, so the toast can be specific. */
data class ExportPayload(
    val content: String,
    val sessionCount: Int,
    val todoCount: Int = 0,
)

/**
 * Holds no Android Context: the notification is driven from [running] by the UI
 * layer, and exporting returns a String that the caller writes to a SAF Uri.
 *
 * It takes [todoRepo] as well as [repo] only because the export screen is one
 * screen that backs up both domains. Every other interaction with todos lives in
 * [TodoViewModel], which knows nothing about tracking.
 */
class AppViewModel(
    private val repo: TimeTrackRepository,
    private val todoRepo: TodoRepository,
) : ViewModel() {

    /** Re-emits once per second, aligned to the wall-clock second boundary so the
     *  running display never looks like it skipped or repeated a second. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    val running: StateFlow<SessionWithTask?> = repo.observeRunningSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentTasks: StateFlow<List<Task>> = repo.observeRecentTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _statsDate = MutableStateFlow(repo.today())
    val statsDate: StateFlow<LocalDate> = _statsDate.asStateFlow()

    /**
     * Recomputed whenever the selected day changes *or* the clock ticks, so a task
     * that is still running keeps growing on the statistics screen in real time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dayStat: StateFlow<DayStat?> =
        combine(_statsDate, _now) { date, _ -> date }
            .mapLatest { date -> repo.dayStat(date) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _exportBusy = MutableStateFlow(false)
    val exportBusy: StateFlow<Boolean> = _exportBusy.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val tick = System.currentTimeMillis()
                _now.value = tick
                delay((1_000L - (tick % 1_000L)).coerceAtLeast(1L))
            }
        }
    }

    fun startNew(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.startNewTask(name) }
                .onSuccess { _message.value = "开始计时：$name" }
                .onFailure { _message.value = "无法开始计时：${it.message ?: "未知错误"}" }
        }
    }

    fun startExisting(task: Task) {
        viewModelScope.launch {
            runCatching { repo.startExistingTask(task.id) }
                .onSuccess { _message.value = "切换为：${task.name}" }
                .onFailure { _message.value = "无法切换任务" }
        }
    }

    fun stop() {
        viewModelScope.launch {
            runCatching { repo.stopRunning() }
                .onSuccess { duration ->
                    _message.value = if (duration != null) {
                        "已结束，本次 ${Fmt.duration(duration)}"
                    } else {
                        "当前没有正在计时的任务"
                    }
                }
                .onFailure { _message.value = "停止失败：${it.message ?: "未知错误"}" }
        }
    }

    fun shiftDate(days: Long) {
        val next = _statsDate.value.plusDays(days)
        if (next <= repo.today()) _statsDate.value = next
    }

    fun goToToday() {
        _statsDate.value = repo.today()
    }

    fun isToday(): Boolean = _statsDate.value == repo.today()

    fun consumeMessage() {
        _message.value = null
    }

    fun markExporting() {
        _exportBusy.value = true
        _message.value = "正在导出…"
    }

    /**
     * The CSV holds one row per tracked interval, so it never carries todos.
     * The JSON is the backup format, and a backup that silently omitted the todo
     * lists would not be one — so todos are always exported in full, and the
     * range affects only the tracking records. The export screen says so.
     */
    suspend fun renderExport(range: ExportRange, asCsv: Boolean): ExportPayload {
        val rows = repo.exportRows(range)
        if (asCsv) {
            return ExportPayload(content = Exporter.csv(rows), sessionCount = rows.size)
        }
        val todos = todoRepo.getAll()
        return ExportPayload(
            content = Exporter.json(rows, todos = todos),
            sessionCount = rows.size,
            todoCount = todos.size,
        )
    }

    fun exportFinished(payload: ExportPayload) {
        _exportBusy.value = false
        _message.value = when {
            payload.sessionCount == 0 && payload.todoCount == 0 -> "该范围内暂无记录，已导出空表"
            payload.todoCount > 0 -> "已导出 ${payload.sessionCount} 条计时记录、${payload.todoCount} 条待办"
            else -> "已导出 ${payload.sessionCount} 条记录"
        }
    }

    fun exportFailed(reason: String) {
        _exportBusy.value = false
        _message.value = "导出失败：$reason"
    }

    companion object {
        fun factory(
            repo: TimeTrackRepository,
            todoRepo: TodoRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(repo, todoRepo) }
        }
    }
}
