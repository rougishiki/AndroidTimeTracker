package com.timetrack.app.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * All todo reads and writes.
 *
 * Deliberately separate from [TimeTrackRepository]: the todo lists and the
 * tracked intervals never refer to each other, so folding them into one class
 * would couple two things with no reason to change together. That separation is
 * also why adding this feature needed no edit at all to the tracking code.
 *
 * Period keys are produced and parsed exclusively through [TodoPeriod], so the
 * ISO-week rules live in exactly one place.
 */
class TodoRepository(
    private val db: TimeTrackDatabase,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.todoDao()

    // --- period helpers ------------------------------------------------------

    fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    fun todayKey(): String = TodoPeriod.dayKey(today())

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfDay(dayKey: String): Long = startOfDay(TodoPeriod.parseDayKey(dayKey))

    /** The exclusive end of the day named by [dayKey], i.e. the next midnight. */
    fun endOfDay(dayKey: String): Long = startOfDay(TodoPeriod.parseDayKey(dayKey).plusDays(1))

    fun weekKeyOf(dayKey: String): String = TodoPeriod.weekKey(TodoPeriod.parseDayKey(dayKey))

    fun isToday(dayKey: String): Boolean = dayKey == todayKey()

    // --- reads ---------------------------------------------------------------

    /** Every item of one period, unfinished first and then in creation order. */
    fun observePeriod(scope: TodoScope, periodKey: String): Flow<List<Todo>> =
        dao.observePeriod(scope, periodKey)

    /**
     * The weekly items that were still open when the day named by [dayKey]
     * began, so the daily screen can list them beside that day's own items.
     */
    fun observeWeekOpenOn(dayKey: String): Flow<List<Todo>> = dao.observeOpenSince(
        scope = TodoScope.WEEK,
        periodKey = weekKeyOf(dayKey),
        openSince = startOfDay(dayKey),
    )

    /** Periods that still owe something, so the calendar can mark them. */
    fun observeOpenPeriodKeys(scope: TodoScope): Flow<List<String>> =
        dao.observeOpenPeriodKeys(scope)

    // --- writes --------------------------------------------------------------

    suspend fun add(scope: TodoScope, periodKey: String, rawTitle: String): Long {
        val title = rawTitle.trim()
        require(title.isNotEmpty()) { "待办内容不能为空" }
        return dao.insert(
            Todo(
                title = title,
                scope = scope,
                periodKey = periodKey,
                createdAt = clock(),
            ),
        )
    }

    suspend fun addOn(scope: TodoScope, date: LocalDate, rawTitle: String): Long =
        add(scope, TodoPeriod.key(scope, date), rawTitle)

    /**
     * Ticks or un-ticks an item and reports the state it ended up in, or null if
     * the item was already gone.
     *
     * Un-ticking clears the timestamp rather than keeping a "was done" flag, so
     * a mis-tap leaves no trace in the backup.
     */
    suspend fun toggle(id: Long): Boolean? {
        val todo = dao.get(id) ?: return null
        if (todo.doneAt == null) {
            dao.setDoneAt(id, clock())
            return true
        }
        dao.setDoneAt(id, null)
        return false
    }

    suspend fun rename(id: Long, rawTitle: String) {
        val title = rawTitle.trim()
        require(title.isNotEmpty()) { "待办内容不能为空" }
        dao.rename(id, title)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun getAll(): List<Todo> = dao.getAll()

    suspend fun hasAny(): Boolean = dao.count() > 0
}
