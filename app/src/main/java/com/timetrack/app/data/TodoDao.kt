package com.timetrack.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Todo reads are always period-scoped: the screen knows which day or week it is
 * showing, so there is deliberately no "give me everything" query for the lists
 * to misuse.
 */
@Dao
interface TodoDao {

    /**
     * Every item of one period, unfinished first and then in the order it was
     * added. In SQLite `(doneAt IS NOT NULL)` is 0 or 1, so ascending order
     * floats the finished items to the bottom.
     */
    @Query(
        """
        SELECT * FROM todos
        WHERE scope = :scope AND periodKey = :periodKey
        ORDER BY (doneAt IS NOT NULL) ASC, id ASC
        """
    )
    fun observePeriod(scope: TodoScope, periodKey: String): Flow<List<Todo>>

    /**
     * The items of [periodKey] that were still open when [openSince] arrived.
     *
     * The daily screen uses this with a week's key to show the weekly list
     * underneath today's, and the same rule redraws a past day: anything
     * finished later, or never finished, was plainly still open at the time.
     * Filtering in SQL rather than in Kotlin keeps that list short.
     */
    @Query(
        """
        SELECT * FROM todos
        WHERE scope = :scope AND periodKey = :periodKey
          AND (doneAt IS NULL OR doneAt >= :openSince)
        ORDER BY (doneAt IS NOT NULL) ASC, id ASC
        """
    )
    fun observeOpenSince(
        scope: TodoScope,
        periodKey: String,
        openSince: Long,
    ): Flow<List<Todo>>

    /** Periods that still owe something, so the calendar can mark them. */
    @Query("SELECT DISTINCT periodKey FROM todos WHERE scope = :scope AND doneAt IS NULL")
    fun observeOpenPeriodKeys(scope: TodoScope): Flow<List<String>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun get(id: Long): Todo?

    @Insert
    suspend fun insert(todo: Todo): Long

    /** Passing null un-completes the item; the previous timestamp is not kept. */
    @Query("UPDATE todos SET doneAt = :doneAt WHERE id = :id")
    suspend fun setDoneAt(id: Long, doneAt: Long?)

    @Query("UPDATE todos SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun delete(id: Long)

    /** Full dump for the JSON backup, oldest first. */
    @Query("SELECT * FROM todos ORDER BY id ASC")
    suspend fun getAll(): List<Todo>

    @Query("SELECT COUNT(*) FROM todos")
    suspend fun count(): Int
}
