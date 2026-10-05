package com.timetrack.app.util

import com.timetrack.app.data.ExportRow
import com.timetrack.app.data.Todo
import java.time.ZoneId

/**
 * Builds the export payloads by hand: no serialization dependency, and the exact
 * byte layout is under our control.
 */
object Exporter {

    /** Excel only detects UTF-8 when the file starts with a BOM; without it Chinese text garbles. */
    private const val UTF8_BOM = "\uFEFF"

    fun csv(rows: List<ExportRow>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append(UTF8_BOM)
        append("任务,开始时间,结束时间,时长(秒),时长(小时),时长,状态\r\n")
        for (r in rows) {
            append(cell(r.taskName)).append(',')
            append(cell(Fmt.dateTime(r.startTime, zone))).append(',')
            append(cell(r.endTime?.let { Fmt.dateTime(it, zone) } ?: "")).append(',')
            append(r.durationMillis / 1000).append(',')
            append(Fmt.hours(r.durationMillis)).append(',')
            append(cell(Fmt.duration(r.durationMillis))).append(',')
            append(if (r.running) "进行中" else "已完成").append("\r\n")
        }
    }

    /**
     * The structured backup, and the only format that carries todos: the CSV is
     * one row per tracked interval, and mixing a second kind of record into it
     * would break the column layout.
     *
     * [todos] is last and defaulted so existing callers, including the export
     * tests, keep working unchanged.
     */
    fun json(
        rows: List<ExportRow>,
        zone: ZoneId = ZoneId.systemDefault(),
        todos: List<Todo> = emptyList(),
    ): String = buildString {
        append("{\n")
        append("  \"app\": \"TimeTrack\",\n")
        append("  \"schemaVersion\": 2,\n")
        append("  \"timezone\": ").append(quote(zone.id)).append(",\n")
        append("  \"exportedAt\": ").append(quote(Fmt.dateTime(System.currentTimeMillis(), zone))).append(",\n")
        append("  \"sessionCount\": ").append(rows.size).append(",\n")
        append("  \"totalMillis\": ").append(rows.sumOf { it.durationMillis }).append(",\n")
        append("  \"todoCount\": ").append(todos.size).append(",\n")
        append("  \"sessions\": [\n")
        rows.forEachIndexed { index, r ->
            append("    {\n")
            append("      \"task\": ").append(quote(r.taskName)).append(",\n")
            append("      \"startTime\": ").append(r.startTime).append(",\n")
            append("      \"startAt\": ").append(quote(Fmt.dateTime(r.startTime, zone))).append(",\n")
            append("      \"endTime\": ").append(r.endTime?.toString() ?: "null").append(",\n")
            append("      \"endAt\": ").append(r.endTime?.let { quote(Fmt.dateTime(it, zone)) } ?: "null").append(",\n")
            append("      \"durationMillis\": ").append(r.durationMillis).append(",\n")
            append("      \"running\": ").append(r.running).append('\n')
            append("    }").append(if (index == rows.lastIndex) "\n" else ",\n")
        }
        append("  ],\n")
        append("  \"todos\": [\n")
        todos.forEachIndexed { index, t ->
            append("    {\n")
            append("      \"title\": ").append(quote(t.title)).append(",\n")
            append("      \"scope\": ").append(quote(t.scope.name)).append(",\n")
            append("      \"period\": ").append(quote(t.periodKey)).append(",\n")
            append("      \"createdTime\": ").append(t.createdAt).append(",\n")
            append("      \"createdAt\": ").append(quote(Fmt.dateTime(t.createdAt, zone))).append(",\n")
            append("      \"done\": ").append(t.doneAt != null).append(",\n")
            append("      \"doneTime\": ").append(t.doneAt?.toString() ?: "null").append(",\n")
            append("      \"doneAt\": ").append(t.doneAt?.let { quote(Fmt.dateTime(it, zone)) } ?: "null").append('\n')
            append("    }").append(if (index == todos.lastIndex) "\n" else ",\n")
        }
        append("  ]\n")
        append("}\n")
    }

    fun suggestedFileName(extension: String, stamp: Long): String {
        val clean = Fmt.dateTime(stamp).replace(':', '-').replace(' ', '_')
        return "TimeTrack_$clean.$extension"
    }

    // --- helpers -------------------------------------------------------------

    private fun cell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
