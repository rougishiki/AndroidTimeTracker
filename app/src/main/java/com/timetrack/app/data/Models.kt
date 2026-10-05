package com.timetrack.app.data

/** One task's share of a reporting window. */
data class TaskSlice(
    val taskId: Long,
    val name: String,
    val colorArgb: Int,
    val millis: Long,
)

/** Aggregated view of a single local day. */
data class DayStat(
    val dayStart: Long,
    val totalMillis: Long,
    val slices: List<TaskSlice>,
)

/** Flat row used by the exporters. */
data class ExportRow(
    val taskName: String,
    val startTime: Long,
    val endTime: Long?,
    val durationMillis: Long,
    val running: Boolean,
)

/** Range presets offered on the export screen. */
enum class ExportRange(val label: String) {
    TODAY("今天"),
    LAST_7_DAYS("最近 7 天"),
    LAST_30_DAYS("最近 30 天"),
    ALL("全部记录"),
}

/**
 * What a rename actually did, so the UI can say "已合并" instead of claiming a
 * plain rename. `tasks.name` is unique, so renaming onto a taken name has to
 * merge the two tasks rather than fail.
 */
enum class RenameOutcome { RENAMED, MERGED }

/**
 * One day of the todo screen: the items created for that day, plus the week's
 * items that were still open when it began.
 *
 * The split is derived, never stored. On a past day [weekDoneThatDay] is simply
 * the weekly items whose `doneAt` falls inside that day, which is what lets an
 * old day be redrawn exactly as it looked at the time — no snapshot table, no
 * scheduled job.
 */
data class DayTodos(
    val dayKey: String,
    val dayStart: Long,
    val dayEnd: Long,
    /** Items created for this day. */
    val own: List<Todo>,
    /** Weekly items that were still open that day and are still open. */
    val weekOpen: List<Todo>,
    /** Weekly items finished during that day. */
    val weekDoneThatDay: List<Todo>,
)
