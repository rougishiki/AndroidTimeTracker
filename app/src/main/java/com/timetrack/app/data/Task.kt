package com.timetrack.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A task the user has tracked at least once.
 *
 * Tasks are never hard-deleted: statistics reference them by id, so removing a
 * task from the picker only sets [archived]. That keeps historical reports intact.
 */
@Entity(
    tableName = "tasks",
    indices = [Index(value = ["name"], unique = true)],
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorArgb: Int,
    val createdAt: Long,
    val lastUsedAt: Long,
    val archived: Boolean = false,
)
