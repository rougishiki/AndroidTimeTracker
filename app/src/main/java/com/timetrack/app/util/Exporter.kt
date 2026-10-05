package com.timetrack.app.util

import com.timetrack.app.data.ExportRow
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

    fun json(rows: List<ExportRow>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append("{\n")
        append("  \"app\": \"TimeTrack\",\n")
        append("  \"schemaVersion\": 1,\n")
        append("  \"timezone\": ").append(quote(zone.id)).append(",\n")
        append("  \"exportedAt\": ").append(quote(Fmt.dateTime(System.currentTimeMillis(), zone))).append(",\n")
        append("  \"sessionCount\": ").append(rows.size).append(",\n")
        append("  \"totalMillis\": ").append(rows.sumOf { it.durationMillis }).append(",\n")
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
