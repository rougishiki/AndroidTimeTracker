package com.timetrack.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timetrack.app.data.DayTodos
import com.timetrack.app.data.Todo
import com.timetrack.app.data.TodoPeriod
import com.timetrack.app.data.TodoRepository
import com.timetrack.app.data.TodoScope
import com.timetrack.app.util.Fmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** The week view: the week's own items, plus the bounds its label is built from. */
data class WeekTodos(
    val weekKey: String,
    val monday: LocalDate,
    val sunday: LocalDate,
    val items: List<Todo>,
)

/**
 * State for the todo tab.
 *
 * Both views hang off a single [dayKey]: the week view is simply the ISO week
 * containing that day. One anchor means switching between "today" and "this
 * week" can never land the two views on unrelated periods, and stepping a week
 * is stepping seven days.
 */
class TodoViewModel(private val repo: TodoRepository) : ViewModel() {

    private val _dayKey = MutableStateFlow(repo.todayKey())
    val dayKey: StateFlow<String> = _dayKey.asStateFlow()

    private val _mode = MutableStateFlow(TodoScope.DAY)
    val mode: StateFlow<TodoScope> = _mode.asStateFlow()

    /** The month the calendar and week-list dialogs are showing, which may differ
     *  from the selected period while the user browses. */
    private val _pickerMonth = MutableStateFlow(YearMonth.from(repo.today()))
    val pickerMonth: StateFlow<YearMonth> = _pickerMonth.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayTodos: StateFlow<DayTodos?> = _dayKey
        .flatMapLatest { key ->
            val dayStart = repo.startOfDay(key)
            val dayEnd = repo.endOfDay(key)
            combine(
                repo.observePeriod(TodoScope.DAY, key),
                repo.observeWeekOpenOn(key),
            ) { own, week ->
                DayTodos(
                    dayKey = key,
                    dayStart = dayStart,
                    dayEnd = dayEnd,
                    own = own,
                    // The split is the same rule as TodoPeriod.wasOpenOn, applied
                    // to the week's items so the day can show what it finished.
                    weekOpen = week.filterNot { TodoPeriod.completedOn(it.doneAt, dayStart, dayEnd) },
                    weekDoneThatDay = week.filter { TodoPeriod.completedOn(it.doneAt, dayStart, dayEnd) },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val weekTodos: StateFlow<WeekTodos?> = _dayKey
        .flatMapLatest { key ->
            val weekKey = repo.weekKeyOf(key)
            val monday = TodoPeriod.parseWeekKey(weekKey)
            repo.observePeriod(TodoScope.WEEK, weekKey).map { items ->
                WeekTodos(weekKey, monday, monday.plusDays(6), items)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Periods that still owe something, so the pickers can mark them. */
    val openDays: StateFlow<Set<String>> = repo.observeOpenPeriodKeys(TodoScope.DAY)
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val openWeeks: StateFlow<Set<String>> = repo.observeOpenPeriodKeys(TodoScope.WEEK)
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // --- navigation ----------------------------------------------------------

    fun setMode(scope: TodoScope) {
        _mode.value = scope
    }

    /** Steps one day, or one week when the week view is showing. */
    fun shift(units: Long) {
        val days = if (_mode.value == TodoScope.WEEK) units * 7 else units
        _dayKey.value = TodoPeriod.shift(TodoScope.DAY, _dayKey.value, days)
        syncPickerToSelection()
    }

    fun goToToday() {
        _dayKey.value = repo.todayKey()
        syncPickerToSelection()
    }

    fun selectDay(dayKey: String) {
        _dayKey.value = dayKey
        syncPickerToSelection()
    }

    fun showMonth(month: YearMonth) {
        _pickerMonth.value = month
    }

    fun isOnToday(): Boolean = _dayKey.value == repo.todayKey()

    /** The key of the real today, so the pickers can mark it. */
    fun todayKey(): String = repo.todayKey()

    fun dayLabel(dayKey: String): String =
        Fmt.dayLabel(TodoPeriod.parseDayKey(dayKey), repo.today())

    fun weekLabel(weekKey: String): String {
        val monday = TodoPeriod.parseWeekKey(weekKey)
        return "第${TodoPeriod.weekNumber(monday)}周 · ${Fmt.weekRangeShort(monday)}"
    }

    // --- edits ---------------------------------------------------------------

    /** Adds to whichever list is on screen. */
    fun add(rawTitle: String) = addFor(_mode.value, rawTitle)

    /** Used by the "+" on the weekly section of the daily view. */
    fun addToWeek(rawTitle: String) = addFor(TodoScope.WEEK, rawTitle)

    private fun addFor(scope: TodoScope, rawTitle: String) {
        val title = rawTitle.trim()
        if (title.isEmpty()) return
        val key = periodKeyOf(scope, _dayKey.value)
        viewModelScope.launch {
            runCatching { repo.add(scope, key, title) }
                .onSuccess { _message.value = "已添加：$title" }
                .onFailure { _message.value = "添加失败：${it.message ?: "未知错误"}" }
        }
    }

    fun toggle(todo: Todo) {
        viewModelScope.launch {
            runCatching { repo.toggle(todo.id) }
                .onSuccess { done ->
                    _message.value = when (done) {
                        true -> "完成：${todo.title}"
                        false -> "已取消完成：${todo.title}"
                        null -> "这条待办已经不存在了"
                    }
                }
                .onFailure { _message.value = "操作失败：${it.message ?: "未知错误"}" }
        }
    }

    fun rename(todo: Todo, rawTitle: String) {
        val title = rawTitle.trim()
        if (title.isEmpty() || title == todo.title) return
        viewModelScope.launch {
            runCatching { repo.rename(todo.id, title) }
                .onSuccess { _message.value = "已重命名" }
                .onFailure { _message.value = "重命名失败：${it.message ?: "未知错误"}" }
        }
    }

    fun delete(todo: Todo) {
        viewModelScope.launch {
            runCatching { repo.delete(todo.id) }
                .onSuccess { _message.value = "已删除：${todo.title}" }
                .onFailure { _message.value = "删除失败：${it.message ?: "未知错误"}" }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun periodKeyOf(scope: TodoScope, dayKey: String): String = when (scope) {
        TodoScope.DAY -> dayKey
        TodoScope.WEEK -> repo.weekKeyOf(dayKey)
    }

    private fun syncPickerToSelection() {
        _pickerMonth.value = YearMonth.from(TodoPeriod.parseDayKey(_dayKey.value))
    }

    companion object {
        fun factory(repo: TodoRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TodoViewModel(repo) }
        }
    }
}
