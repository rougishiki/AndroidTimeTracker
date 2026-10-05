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

    /** Switches to an existing task, closing the running one first. */
    suspend fun startExistingTask(taskId: Long) {
        db.withTransaction {
            val task = dao.getTask(taskId) ?: return@withTransaction
            val stamp = clock()
            dao.closeRunningSessions(stamp)
            dao.updateTask(task.copy(lastUsedAt = stamp, archived = false))
            dao.insertSession(Session(taskId = taskId, startTime = stamp))
        }
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
     * Totals for one local day.
     *
     * An interval that crosses midnight contributes only its overlapping part,
     * so `23:30 -> 00:30` adds 30 minutes to each of the two days.
     */
    suspend fun dayStat(date: LocalDate): DayStat {
        val dayStart = startOfDay(date)
        val dayEnd = startOfDay(date.plusDays(1))
        val slices = StatsCalculator.aggregate(
            sessions = dao.getSessionsOverlapping(dayStart, dayEnd),
            windowStart = dayStart,
            windowEnd = dayEnd,
            now = clock(),
        )
        return DayStat(dayStart = dayStart, totalMillis = slices.sumOf { it.millis }, slices = slices)
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
