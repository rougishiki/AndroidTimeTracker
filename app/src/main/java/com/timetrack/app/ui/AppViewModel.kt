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

/** Rendered export body plus the number of intervals it contains. */
data class ExportPayload(val content: String, val count: Int)

/**
 * Holds no Android Context: the notification is driven from [running] by the UI
 * layer, and exporting returns a String that the caller writes to a SAF Uri.
 */
class AppViewModel(private val repo: TimeTrackRepository) : ViewModel() {

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

    suspend fun renderExport(range: ExportRange, asCsv: Boolean): ExportPayload {
        val rows = repo.exportRows(range)
        val content = if (asCsv) Exporter.csv(rows) else Exporter.json(rows)
        return ExportPayload(content = content, count = rows.size)
    }

    fun exportFinished(count: Int) {
        _exportBusy.value = false
        _message.value = if (count > 0) "已导出 $count 条记录" else "该范围内暂无记录，已导出空表"
    }

    fun exportFailed(reason: String) {
        _exportBusy.value = false
        _message.value = "导出失败：$reason"
    }

    companion object {
        fun factory(repo: TimeTrackRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(repo) }
        }
    }
}
