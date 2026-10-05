package com.timetrack.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One tracked interval.
 *
 * [endTime] == null means the interval is still running. Only ever one such row
 * exists at a time, which is enforced by the repository inside a transaction.
 * Elapsed time is always derived from timestamps rather than accumulated in
 * memory, so a process kill or device reboot cannot corrupt the duration.
 */
@Entity(
    tableName = "sessions",
    indices = [Index("taskId"), Index("startTime"), Index("endTime")],
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val startTime: Long,
    val endTime: Long? = null,
)

/** A [Session] joined with the display fields of its owning [Task]. */
data class SessionWithTask(
    val id: Long,
    val taskId: Long,
    val startTime: Long,
    val endTime: Long?,
    val taskName: String,
    val taskColorArgb: Int,
)
