package com.timetrack.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeTrackDao {

    // ---------- running session ----------

    @Query(
        """
        SELECT s.id AS id, s.taskId AS taskId, s.startTime AS startTime, s.endTime AS endTime,
               t.name AS taskName, t.colorArgb AS taskColorArgb
        FROM sessions s JOIN tasks t ON t.id = s.taskId
        WHERE s.endTime IS NULL
        ORDER BY s.startTime DESC LIMIT 1
        """
    )
    fun observeRunningSession(): Flow<SessionWithTask?>

    @Query("SELECT * FROM sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getRunningSession(): Session?

    /** Closes every dangling interval at once, guarding against corrupted state. */
    @Query("UPDATE sessions SET endTime = :endTime WHERE endTime IS NULL")
    suspend fun closeRunningSessions(endTime: Long): Int

    @Insert
    suspend fun insertSession(session: Session): Long

    // ---------- tasks ----------

    @Query("SELECT * FROM tasks WHERE archived = 0 ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecentTasks(limit: Int): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTaskByName(name: String): Task?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTask(id: Long): Task?

    @Insert
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    // ---------- reporting ----------

    /**
     * Every interval that overlaps `[since, until)`. Overlap (rather than
     * containment) is required so an interval spanning midnight is attributed to
     * both days instead of only the one it started on.
     */
    @Query(
        """
        SELECT s.id AS id, s.taskId AS taskId, s.startTime AS startTime, s.endTime AS endTime,
               t.name AS taskName, t.colorArgb AS taskColorArgb
        FROM sessions s JOIN tasks t ON t.id = s.taskId
        WHERE s.startTime < :until AND (s.endTime IS NULL OR s.endTime > :since)
        ORDER BY s.startTime ASC
        """
    )
    suspend fun getSessionsOverlapping(since: Long, until: Long): List<SessionWithTask>

    /** All intervals that ended, oldest first. Used for the "export everything" path. */
    @Query(
        """
        SELECT s.id AS id, s.taskId AS taskId, s.startTime AS startTime, s.endTime AS endTime,
               t.name AS taskName, t.colorArgb AS taskColorArgb
        FROM sessions s JOIN tasks t ON t.id = s.taskId
        ORDER BY s.startTime ASC
        """
    )
    suspend fun getAllSessions(): List<SessionWithTask>

    @Query("SELECT MIN(startTime) FROM sessions")
    suspend fun earliestStart(): Long?

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun sessionCount(): Int
}
