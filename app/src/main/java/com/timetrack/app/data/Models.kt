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
