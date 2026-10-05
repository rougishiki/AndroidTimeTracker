package com.timetrack.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timetrack.app.data.DayStat
import com.timetrack.app.data.ExportRange
import com.timetrack.app.data.PeriodComparison
import com.timetrack.app.data.RenameOutcome
import com.timetrack.app.data.SessionTimes
import com.timetrack.app.data.SessionWithTask
import com.timetrack.app.data.StatsMode
import com.timetrack.app.data.StatsPeriod
import com.timetrack.app.data.Task
import com.timetrack.app.data.TaskSlice
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
import java.time.YearMonth

/** A rendered export body plus what it holds, so the toast can be specific. */
data class ExportPayload(
    val content: String,
    val sessionCount: Int,
    val todoCount: Int = 0,
)

/**
 * The stored intervals of one task on one day, i.e. what the editor is looking
 * at. Kept as a snapshot rather than a Flow because it is only ever opened from
 * a row the user tapped, and re-read explicitly after each edit.
 */
data class TaskSessionsState(
    val taskId: Long,
    val taskName: String,
    val colorArgb: Int,
    val day: LocalDate,
    val sessions: List<SessionWithTask>,
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

    /**
     * The running interval once it has been going long enough to look forgotten,
     * else null.
     *
     * Read from observed state rather than driven by an alarm, on purpose. The
     * whole notification design is "zero wakeups", and an exact alarm would need
     * SCHEDULE_EXACT_ALARM on Android 12+ — a permission the user has to grant
     * from system settings. The cost is honest and worth stating: this reminder
     * is only seen when the app is opened. The persistent notification is the
     * reminder that works the rest of the time.
     */
    val longRunning: StateFlow<SessionWithTask?> =
        combine(running, _now) { current, now ->
            current?.takeIf {
                SessionTimes.isProbablyForgotten(it.startTime, now, FORGOTTEN_AFTER_MILLIS)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentTasks: StateFlow<List<Task>> = repo.observeRecentTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _statsDate = MutableStateFlow(repo.today())
    val statsDate: StateFlow<LocalDate> = _statsDate.asStateFlow()

    private val _statsMode = MutableStateFlow(StatsMode.DAY)
    val statsMode: StateFlow<StatsMode> = _statsMode.asStateFlow()

    /**
     * Recomputed whenever the selected day changes *or* the clock ticks, so a task
     * that is still running keeps growing on the statistics screen in real time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dayStat: StateFlow<DayStat?> =
        combine(_statsDate, _now) { date, _ -> date }
            .mapLatest { date -> repo.dayStat(date) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The current period's totals beside the previous period's.
     *
     * Recomputed on the clock tick as well, for the same reason [dayStat] is: a
     * task that is still running has to keep growing on whichever view is open.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val periodComparison: StateFlow<PeriodComparison?> =
        combine(_statsDate, _statsMode, _now) { date, mode, _ -> date to mode }
            .mapLatest { (date, mode) -> repo.periodComparison(mode, date) }
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

    /**
     * Starts a task by id, for a launcher shortcut.
     *
     * The id comes from a menu the app built earlier, so it can be stale. The
     * result is reported honestly rather than assumed.
     */
    fun startTaskById(taskId: Long) {
        viewModelScope.launch {
            runCatching { repo.startExistingTask(taskId) }
                .onSuccess { started ->
                    _message.value = if (started) "已开始计时" else "这个任务已经不存在了"
                }
                .onFailure { _message.value = "无法开始计时：${it.message ?: "未知错误"}" }
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

    fun setStatsMode(mode: StatsMode) {
        _statsMode.value = mode
    }

    /**
     * Steps one period of the current mode.
     *
     * Won't step past the period that contains today: the future holds no
     * records, so a comparator with nothing in it would be noise. The whole
     * current period counts as reachable, which is why this compares period
     * starts rather than the raw date.
     */
    fun shiftPeriod(units: Long) {
        val mode = _statsMode.value
        val next = StatsPeriod.shift(mode, _statsDate.value, units)
        val currentPeriodOfToday = StatsPeriod.bounds(mode, repo.today()).first
        if (!StatsPeriod.bounds(mode, next).first.isAfter(currentPeriodOfToday)) {
            _statsDate.value = next
        }
    }

    fun isAtCurrentPeriod(): Boolean {
        val mode = _statsMode.value
        return StatsPeriod.bounds(mode, _statsDate.value).first ==
            StatsPeriod.bounds(mode, repo.today()).first
    }

    /** `今天 · ...` / `第 41 周 · 10月5日 – 10月11日` / `2026年10月`. */
    fun periodLabel(): String {
        val day = _statsDate.value
        return when (_statsMode.value) {
            StatsMode.DAY -> Fmt.dayLabel(day, repo.today())
            StatsMode.WEEK -> {
                val monday = StatsPeriod.mondayOf(day)
                "第${StatsPeriod.weekNumber(monday)}周 · ${Fmt.weekRangeShort(monday)}"
            }

            StatsMode.MONTH -> Fmt.monthTitle(YearMonth.from(day))
        }
    }

    fun goToToday() {
        _statsDate.value = repo.today()
    }

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

    // ---------- correcting what was recorded ----------

    private val _taskSessions = MutableStateFlow<TaskSessionsState?>(null)
    val taskSessions: StateFlow<TaskSessionsState?> = _taskSessions.asStateFlow()

    /** Every task including archived ones, so an archived task can come back. */
    val allTasks: StateFlow<List<Task>> = repo.observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Opens the editor for a task as it appears on the statistics screen's day. */
    fun openTaskSessions(slice: TaskSlice) {
        openTaskSessions(slice.taskId, slice.name, slice.colorArgb, _statsDate.value)
    }

    /**
     * Opens the editor on whatever is running, anchored on the day that interval
     * itself started.
     *
     * Used by the "did you forget to stop?" card, which is the one place the day
     * being viewed on the statistics screen is irrelevant — a forgotten interval
     * usually started yesterday.
     */
    fun openRunningSessions() {
        val current = running.value ?: return
        openTaskSessions(
            taskId = current.taskId,
            name = current.taskName,
            colorArgb = current.taskColorArgb,
            day = repo.localDayOf(current.startTime),
        )
    }

    private fun openTaskSessions(taskId: Long, name: String, colorArgb: Int, day: LocalDate) {
        viewModelScope.launch {
            _taskSessions.value = TaskSessionsState(
                taskId = taskId,
                taskName = name,
                colorArgb = colorArgb,
                day = day,
                sessions = repo.sessionsOfTaskOnDay(taskId, day),
            )
        }
    }

    fun closeTaskSessions() {
        _taskSessions.value = null
    }

    private suspend fun reloadTaskSessions() {
        val current = _taskSessions.value ?: return
        _taskSessions.value = current.copy(
            sessions = repo.sessionsOfTaskOnDay(current.taskId, current.day),
        )
    }

    /**
     * [day] is the interval's own day, supplied by the editor, not the day being
     * viewed: an interval spanning midnight shows up on two days, and anchoring
     * on the viewed one would silently shift it by a day on save.
     */
    fun retimeSession(sessionId: Long, day: LocalDate, startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch {
            runCatching { repo.retimeSession(sessionId, day, startMinutes, endMinutes) }
                .onSuccess {
                    reloadTaskSessions()
                    _message.value = "已修改这段记录"
                }
                .onFailure { _message.value = "修改失败：${it.message ?: "未知错误"}" }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            runCatching { repo.deleteSession(sessionId) }
                .onSuccess {
                    reloadTaskSessions()
                    _message.value = "已删除这一段"
                }
                .onFailure { _message.value = "删除失败：${it.message ?: "未知错误"}" }
        }
    }

    /** Backfills another interval onto the task the editor is already showing. */
    fun addSessionToCurrentTask(day: LocalDate, startMinutes: Int, endMinutes: Int) {
        val current = _taskSessions.value ?: return
        viewModelScope.launch {
            runCatching {
                repo.addManualSession(current.taskName, day, startMinutes, endMinutes)
            }
                .onSuccess {
                    reloadTaskSessions()
                    _message.value = "已补记一段"
                }
                .onFailure { _message.value = "补记失败：${it.message ?: "未知错误"}" }
        }
    }

    /** Backfills an interval for a task that may not exist yet; entry point is the timer page. */
    fun addManualEntry(name: String, day: LocalDate, startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch {
            runCatching { repo.addManualSession(name, day, startMinutes, endMinutes) }
                .onSuccess { _message.value = "已补记：${name.trim()}" }
                .onFailure { _message.value = "补记失败：${it.message ?: "未知错误"}" }
        }
    }

    // ---------- managing tasks ----------

    fun renameTask(task: Task, newName: String) {
        viewModelScope.launch {
            runCatching { repo.renameTask(task.id, newName) }
                .onSuccess { outcome ->
                    _message.value = when (outcome) {
                        RenameOutcome.MERGED -> "已合并到「${newName.trim()}」"
                        RenameOutcome.RENAMED -> "已重命名为「${newName.trim()}」"
                    }
                }
                .onFailure { _message.value = "重命名失败：${it.message ?: "未知错误"}" }
        }
    }

    fun setTaskArchived(task: Task, archived: Boolean) {
        viewModelScope.launch {
            runCatching { repo.setTaskArchived(task.id, archived) }
                .onSuccess {
                    _message.value = if (archived) "已归档：${task.name}" else "已恢复：${task.name}"
                }
                .onFailure { _message.value = "操作失败：${it.message ?: "未知错误"}" }
        }
    }

    // ---------- helpers the editors need ----------

    fun minutesOf(millis: Long): Int = repo.minutesOf(millis)

    fun dayOf(millis: Long): LocalDate = repo.localDayOf(millis)

    fun lengthOf(day: LocalDate, startMinutes: Int, endMinutes: Int): Long? =
        repo.lengthOf(day, startMinutes, endMinutes)

    fun nowMinutes(): Int = repo.nowMinutes()

    fun today(): LocalDate = repo.today()

    companion object {
        /** Eight hours: longer than any deliberate single sitting. */
        const val FORGOTTEN_AFTER_MILLIS = 8 * 60 * 60 * 1000L

        fun factory(
            repo: TimeTrackRepository,
            todoRepo: TodoRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(repo, todoRepo) }
        }
    }
}
