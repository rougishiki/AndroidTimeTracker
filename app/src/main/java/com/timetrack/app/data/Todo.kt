package com.timetrack.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One todo item, fixed to the period it was created in.
 *
 * Both lists share this table: [scope] says whether [periodKey] names a day
 * (`2026-10-05`) or an ISO week (`2026-W53`). The period is captured once and
 * never moves afterwards, which is what stops "yesterday" from changing when
 * today is edited.
 *
 * [doneAt] is the entire completion state. There is no `done` flag and no
 * second record table, because an item is completed exactly once, ever:
 * ticking a weekly item finishes it for that week. Everything the UI shows,
 * including how a past day looked at the time, is derived from this one
 * timestamp by [TodoPeriod]. Clearing it un-completes the item, so nothing is
 * lost that the user cannot put back.
 *
 * Unlike [Task] there is no `archived` flag. Statistics never reference a todo,
 * so deleting one leaves no dangling history and soft deletion would only be
 * complexity without a payoff.
 */
@Entity(
    tableName = "todos",
    indices = [Index(value = ["scope", "periodKey"])],
)
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val scope: TodoScope,
    val periodKey: String,
    val createdAt: Long,
    val doneAt: Long? = null,
)
