package com.timetrack.app.data

import java.time.LocalDate

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
    /** How many intervals contributed; the divisor for an average session. */
    val intervalCount: Int,
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

/** How wide a window the statistics screen is looking at. */
enum class StatsMode(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
}

/** Aggregated totals over an arbitrary half-open window `[start, untilExclusive)`. */
data class RangeStat(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val totalMillis: Long,
    /** How many intervals contributed; the divisor for an average session. */
    val intervalCount: Int,
    val slices: List<TaskSlice>,
)

/**
 * One task's time in a period next to the period immediately before it.
 *
 * A task present in only one of the two still gets a row, with zero on the other
 * side: "I did not touch this at all last week" is exactly the kind of change the
 * comparison exists to show, and dropping it would hide the most interesting
 * rows.
 */
data class ComparisonRow(
    val taskId: Long,
    val name: String,
    val colorArgb: Int,
    val currentMillis: Long,
    val previousMillis: Long,
) {
    val deltaMillis: Long get() = currentMillis - previousMillis
}

/** A period's totals beside the same figures for the period before it. */
data class PeriodComparison(
    val currentStart: LocalDate,
    val currentEndInclusive: LocalDate,
    val previousStart: LocalDate,
    val previousEndInclusive: LocalDate,
    val currentTotalMillis: Long,
    val previousTotalMillis: Long,
    val rows: List<ComparisonRow>,
) {
    val deltaMillis: Long get() = currentTotalMillis - previousTotalMillis
}
