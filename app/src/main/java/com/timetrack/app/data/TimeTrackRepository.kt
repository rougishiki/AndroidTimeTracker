package com.timetrack.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single entry point for all tracking operations.
 *
 * Concurrency rule enforced here: at most one session has `endTime == null`.
 * Starting anything always closes whatever was running, inside the same
 * transaction, so a crash mid-switch cannot leave two intervals open.
 */
class TimeTrackRepository(
    private val db: TimeTrackDatabase,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    fun observeRunningSession(): Flow<SessionWithTask?> = dao.observeRunningSession()

    fun observeRecentTasks(limit: Int = 12): Flow<List<Task>> = dao.observeRecentTasks(limit)

    /** Creates the task if the name is new, then starts tracking it immediately. */
    suspend fun startNewTask(rawName: String): Long {
        val name = rawName.trim()
        require(name.isNotEmpty()) { "任务名不能为空" }
        return db.withTransaction {
            val stamp = clock()
            dao.closeRunningSessions(stamp)
            val existing = dao.findTaskByName(name)
            val taskId = if (existing != null) {
                dao.updateTask(existing.copy(archived = false, lastUsedAt = stamp))
                existing.id
            } else {
                dao.insertTask(
                    Task(
                        name = name,
                        colorArgb = colorFor(name),
                        createdAt = stamp,
                        lastUsedAt = stamp,
                    )
                )
            }
            dao.insertSession(Session(taskId = taskId, startTime = stamp))
        }
    }

    /**
     * Switches to an existing task, closing the running one first.
     *
     * Reports whether anything started, because the launcher shortcuts call this
     * with an id from a menu that may be out of date. Claiming "started" when
     * nothing did would be a lie the user cannot see through.
     */
    suspend fun startExistingTask(taskId: Long): Boolean = db.withTransaction {
        val task = dao.getTask(taskId) ?: return@withTransaction false
        val stamp = clock()
        dao.closeRunningSessions(stamp)
        // An archived task can be started again from a shortcut; doing so brings
        // it back into the picker rather than tracking something invisible.
        dao.updateTask(task.copy(lastUsedAt = stamp, archived = false))
        dao.insertSession(Session(taskId = taskId, startTime = stamp))
        true
    }

    /** Stops tracking and returns the finished duration in millis, or null if nothing ran. */
    suspend fun stopRunning(): Long? = db.withTransaction {
        val running = dao.getRunningSession() ?: return@withTransaction null
        // Guard against a clock that moved backwards (NTP correction / timezone edit).
        val stamp = clock().coerceAtLeast(running.startTime)
        dao.closeRunningSessions(stamp)
        stamp - running.startTime
    }

    /**
     * Totals for the half-open window `[since, untilExclusive)`.
     *
     * The same aggregation the day view uses, only widened: the overlap query
     * already takes arbitrary bounds, so a week or a month needs no new SQL and
     * inherits the midnight-splitting rule for free. An interval spanning New
     * Year is therefore split between the two weeks, not attributed to one.
     */
    suspend fun rangeStat(since: LocalDate, untilExclusive: LocalDate): RangeStat {
        val windowStart = startOfDay(since)
        val windowEnd = startOfDay(untilExclusive)
        val slices = StatsCalculator.aggregate(
            sessions = dao.getSessionsOverlapping(windowStart, windowEnd),
            windowStart = windowStart,
            windowEnd = windowEnd,
            now = clock(),
        )
        return RangeStat(
            start = since,
            endInclusive = untilExclusive.minusDays(1),
            totalMillis = slices.sumOf { it.millis },
            slices = slices,
        )
    }

    /** A period's totals beside the figures for the period immediately before it. */
    suspend fun periodComparison(mode: StatsMode, day: LocalDate): PeriodComparison {
        val (currentStart, currentUntil) = StatsPeriod.bounds(mode, day)
        val (previousStart, previousUntil) = StatsPeriod.previousBounds(mode, day)
        val current = rangeStat(currentStart, currentUntil)
        val previous = rangeStat(previousStart, previousUntil)
        return PeriodComparison(
            currentStart = currentStart,
            currentEndInclusive = StatsPeriod.lastDayInclusive(currentUntil),
            previousStart = previousStart,
            previousEndInclusive = StatsPeriod.lastDayInclusive(previousUntil),
            currentTotalMillis = current.totalMillis,
            previousTotalMillis = previous.totalMillis,
            rows = StatsCalculator.compare(current.slices, previous.slices),
        )
    }

    /**
     * Totals for one local day.
     *
     * An interval that crosses midnight contributes only its overlapping part,
     * so `23:30 -> 00:30` adds 30 minutes to each of the two days.
     */
    suspend fun dayStat(date: LocalDate): DayStat {
        val stat = rangeStat(date, date.plusDays(1))
        return DayStat(
            dayStart = startOfDay(date),
            totalMillis = stat.totalMillis,
            slices = stat.slices,
        )
    }

    suspend fun exportRows(range: ExportRange): List<ExportRow> {
        val nowMs = clock()
        val sessions = if (range == ExportRange.ALL) {
            dao.getAllSessions()
        } else {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val backDays = when (range) {
                ExportRange.TODAY -> 0L
                ExportRange.LAST_7_DAYS -> 6L
                ExportRange.LAST_30_DAYS -> 29L
                ExportRange.ALL -> 0L
            }
            dao.getSessionsOverlapping(
                since = startOfDay(today.minusDays(backDays)),
                until = startOfDay(today.plusDays(1)),
            )
        }
        return sessions.map { s ->
            ExportRow(
                taskName = s.taskName,
                startTime = s.startTime,
                endTime = s.endTime,
                durationMillis = (s.endTime ?: nowMs).coerceAtLeast(s.startTime) - s.startTime,
                running = s.endTime == null,
            )
        }
    }

    suspend fun hasAnyData(): Boolean = dao.sessionCount() > 0

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    // ---------- correcting what was recorded ----------

    /**
     * The stored intervals of one task on one local day, for the editor.
     *
     * Uses overlap rather than containment, so an interval spanning midnight can
     * be reached from either of the days it touches — the same rule the day
     * statistics use.
     */
    suspend fun sessionsOfTaskOnDay(taskId: Long, date: LocalDate): List<SessionWithTask> =
        dao.getTaskSessionsOverlapping(
            taskId = taskId,
            since = startOfDay(date),
            until = startOfDay(date.plusDays(1)),
        )

    /**
     * Rewrites one interval from the wall-clock times the user typed on [day].
     *
     * Giving a still-running interval an end time is the common case: forgetting
     * to press stop used to be unfixable. Retiming never clears an end time, so
     * the "at most one running interval" rule that [startNewTask] maintains
     * cannot be broken from this path.
     *
     * Throws when the two times are identical; there is no honest duration to
     * invent for that input.
     */
    suspend fun retimeSession(sessionId: Long, day: LocalDate, startMinutes: Int, endMinutes: Int) {
        val span = spanFor(day, startMinutes, endMinutes)
        val session = dao.getSession(sessionId) ?: return
        dao.updateSession(session.copy(startTime = span.startTime, endTime = span.endTime))
    }

    suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)

    /**
     * Wall-clock times to millisecond bounds, in this repository's zone.
     *
     * The zone is applied here and nowhere else, so the UI never has to know
     * which one is in force, and [SessionTimes] stays a pure date function.
     */
    fun spanFor(day: LocalDate, startMinutes: Int, endMinutes: Int): SessionSpan =
        SessionTimes.spanOf(day, startMinutes, endMinutes, zone)
            ?: throw IllegalArgumentException("结束时间不能和开始时间相同")

    /**
     * Millis between two times of day, resolved in this repository's zone, or
     * null when the two are identical.
     *
     * The editor previews this instead of subtracting clock times, so what it
     * shows is exactly what saving will store — including on the 23- and 25-hour
     * days, where a wall-clock subtraction is simply wrong.
     */
    fun lengthOf(day: LocalDate, startMinutes: Int, endMinutes: Int): Long? =
        SessionTimes.spanOf(day, startMinutes, endMinutes, zone)?.let { it.endTime - it.startTime }

    /** Minutes since midnight, for pre-filling the editor's fields. */
    fun minutesOf(millis: Long): Int = SessionTimes.minutesOf(millis, zone)

    /**
     * The local day an instant falls on.
     *
     * The editor anchors a session on *its own* day rather than the day being
     * viewed: an interval spanning midnight appears on two days, and anchoring
     * on the viewed one would shift the interval by a day on the next save.
     */
    fun localDayOf(millis: Long): LocalDate = SessionTimes.dayOf(millis, zone)

    fun nowMinutes(): Int = minutesOf(clock())

    /**
     * Backfills an interval that was never tracked.
     *
     * Deliberately does not touch the running session: recording something from
     * earlier must not stop whatever is being timed right now. The interval is
     * inserted closed, so it cannot become a second running row either.
     */
    suspend fun addManualSession(
        rawName: String,
        day: LocalDate,
        startMinutes: Int,
        endMinutes: Int,
    ): Long {
        val span = spanFor(day, startMinutes, endMinutes)
        return db.withTransaction {
            val name = rawName.trim()
            require(name.isNotEmpty()) { "任务名不能为空" }
            val stamp = clock()
            val existing = dao.findTaskByName(name)
            val taskId = if (existing != null) {
                dao.updateTask(existing.copy(archived = false, lastUsedAt = stamp))
                existing.id
            } else {
                dao.insertTask(
                    Task(name = name, colorArgb = colorFor(name), createdAt = stamp, lastUsedAt = stamp),
                )
            }
            dao.insertSession(
                Session(taskId = taskId, startTime = span.startTime, endTime = span.endTime),
            )
        }
    }

    // ---------- managing tasks ----------

    /** Every task including archived ones, so an archived task can come back. */
    fun observeAllTasks(): Flow<List<Task>> = dao.observeAllTasks()

    /**
     * Renames a task, merging into an existing one when the new name is taken.
     *
     * `tasks.name` is unique, so a rename onto an occupied name cannot just
     * update the row. Merging is the honest answer, and it is what repairs a
     * statistics list sprayed with `写周报` / `写周报 ` / `写周报2`: the intervals
     * move to the surviving task and the duplicate is archived rather than
     * deleted, so no report loses history to a missing row.
     */
    suspend fun renameTask(taskId: Long, rawName: String): RenameOutcome = db.withTransaction {
        val name = rawName.trim()
        require(name.isNotEmpty()) { "任务名不能为空" }
        val task = dao.getTask(taskId) ?: error("任务不存在")
        if (task.name == name) return@withTransaction RenameOutcome.RENAMED

        val clash = dao.findTaskByName(name)
        if (clash == null || clash.id == taskId) {
            dao.updateTask(task.copy(name = name))
            RenameOutcome.RENAMED
        } else {
            val stamp = clock()
            dao.reassignSessions(fromTaskId = taskId, toTaskId = clash.id)
            dao.updateTask(clash.copy(archived = false, lastUsedAt = stamp))
            dao.updateTask(task.copy(archived = true))
            RenameOutcome.MERGED
        }
    }

    /**
     * Tasks are archived, never deleted: statistics join on `taskId`, so a
     * deleted task would silently drop its history out of every report.
     */
    suspend fun setTaskArchived(taskId: Long, archived: Boolean) {
        val task = dao.getTask(taskId) ?: return
        dao.updateTask(task.copy(archived = archived))
    }

    companion object {
        /** Stable, readable palette. A task keeps its colour forever via its name hash. */
        private val PALETTE = intArrayOf(
            0xFF4C8DFF.toInt(),
            0xFF23C16B.toInt(),
            0xFFFFA53D.toInt(),
            0xFFE5484D.toInt(),
            0xFF8E6BF0.toInt(),
            0xFF12B5CB.toInt(),
            0xFFFFC53D.toInt(),
            0xFFFF6B9D.toInt(),
            0xFF6E7B8B.toInt(),
            0xFF8FA31E.toInt(),
        )

        fun colorFor(seed: String): Int {
            val h = seed.hashCode() % PALETTE.size
            return PALETTE[if (h < 0) h + PALETTE.size else h]
        }
    }
}
