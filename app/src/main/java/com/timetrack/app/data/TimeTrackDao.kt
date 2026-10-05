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

    // ---------- correcting intervals ----------

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: Long): Session?

    @Update
    suspend fun updateSession(session: Session)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    /**
     * The individual intervals of one task inside a window, for the editor.
     *
     * The statistics screen only ever shows an aggregate; to fix a mistake the
     * user has to reach the actual rows, which is what this returns. The overlap
     * predicate matches [getSessionsOverlapping] so the editor and the report
     * agree on which intervals a day contains.
     */
    @Query(
        """
        SELECT s.id AS id, s.taskId AS taskId, s.startTime AS startTime, s.endTime AS endTime,
               t.name AS taskName, t.colorArgb AS taskColorArgb
        FROM sessions s JOIN tasks t ON t.id = s.taskId
        WHERE s.taskId = :taskId AND s.startTime < :until AND (s.endTime IS NULL OR s.endTime > :since)
        ORDER BY s.startTime ASC
        """
    )
    suspend fun getTaskSessionsOverlapping(taskId: Long, since: Long, until: Long): List<SessionWithTask>

    // ---------- managing tasks ----------

    /** Includes archived tasks, so an archived one can be restored. */
    @Query("SELECT * FROM tasks ORDER BY archived ASC, lastUsedAt DESC")
    fun observeAllTasks(): Flow<List<Task>>

    @Query("UPDATE sessions SET taskId = :toTaskId WHERE taskId = :fromTaskId")
    suspend fun reassignSessions(fromTaskId: Long, toTaskId: Long): Int
}
