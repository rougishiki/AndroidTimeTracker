package com.timetrack.app.util

import com.timetrack.app.data.ExportRow
import com.timetrack.app.data.Todo
import com.timetrack.app.data.TodoScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Locks down the export byte format. The CSV rules matter because a stray comma or
 * a missing BOM silently corrupts the file for the user rather than failing loudly.
 */
class ExporterTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val base = 1_700_000_000_000L

    private fun row(
        name: String = "写周报",
        start: Long = base,
        end: Long? = base + 5_400_000L,
        running: Boolean = end == null,
    ) = ExportRow(
        taskName = name,
        startTime = start,
        endTime = end,
        durationMillis = (end ?: start) - start,
        running = running,
    )

    // ---------- CSV ----------

    @Test
    fun `csv starts with a UTF-8 BOM so Excel detects the encoding`() {
        val csv = Exporter.csv(listOf(row()), zone)
        assertTrue("CSV must begin with the BOM", csv.startsWith("\uFEFF"))
    }

    @Test
    fun `csv header lists the expected columns`() {
        val header = Exporter.csv(emptyList(), zone)
            .removePrefix("\uFEFF")
            .lineSequence()
            .first()
        assertEquals("任务,开始时间,结束时间,时长(秒),时长(小时),时长,状态", header)
    }

    @Test
    fun `csv quotes a task name containing a comma`() {
        val csv = Exporter.csv(listOf(row(name = "读书,做笔记")), zone)
        assertTrue(csv.contains("\"读书,做笔记\""))
    }

    @Test
    fun `csv doubles embedded quotes`() {
        val csv = Exporter.csv(listOf(row(name = "他说\"你好\"")), zone)
        assertTrue(csv.contains("\"他说\"\"你好\"\"\""))
    }

    @Test
    fun `csv writes 5400 seconds and 1_50 hours for a 90 minute session`() {
        val csv = Exporter.csv(listOf(row()), zone)
        assertTrue(csv.contains(",5400,"))
        assertTrue(csv.contains(",1.50,"))
    }

    @Test
    fun `csv uses a dot for the decimal separator regardless of locale`() {
        val csv = Exporter.csv(listOf(row()), zone)
        assertFalse("a comma here would break the column layout", csv.contains(",1,50,"))
    }

    @Test
    fun `csv marks a running session and leaves its end time empty`() {
        val csv = Exporter.csv(listOf(row(end = null)), zone)
        val dataLine = csv.removePrefix("\uFEFF").lineSequence().elementAt(1)
        assertTrue("running rows end with 进行中", dataLine.endsWith("进行中"))
        assertTrue("the end-time column must be empty", dataLine.contains(",,"))
    }

    @Test
    fun `csv contains one data line per row`() {
        val csv = Exporter.csv(listOf(row(), row(name = "读书")), zone)
        // header + 2 rows, ignoring the trailing empty element
        val lines = csv.removePrefix("\uFEFF").trimEnd().lines()
        assertEquals(3, lines.size)
    }

    // ---------- JSON ----------

    @Test
    fun `json reports a null end for a running session`() {
        val json = Exporter.json(listOf(row(end = null)), zone)
        assertTrue(json.contains("\"endTime\": null"))
        assertTrue(json.contains("\"running\": true"))
    }

    @Test
    fun `json escapes quotes in the task name`() {
        val json = Exporter.json(listOf(row(name = "他说\"你好\"")), zone)
        assertTrue(json.contains("\"task\": \"他说\\\"你好\\\"\""))
    }

    @Test
    fun `json carries the raw timestamps for later analysis`() {
        val json = Exporter.json(listOf(row()), zone)
        assertTrue(json.contains("\"startTime\": $base"))
        assertTrue(json.contains("\"endTime\": ${base + 5_400_000L}"))
        assertTrue(json.contains("\"durationMillis\": 5400000"))
    }

    @Test
    fun `json of an empty range is still well formed`() {
        val json = Exporter.json(emptyList(), zone)
        assertTrue(json.contains("\"sessionCount\": 0"))
        assertTrue(json.contains("\"totalMillis\": 0"))
        assertTrue(json.contains("\"sessions\": ["))
    }

    @Test
    fun `json separates multiple sessions with a comma but not the last one`() {
        val json = Exporter.json(listOf(row(name = "A"), row(name = "B")), zone)
        val firstClose = json.indexOf("    },\n")
        val lastClose = json.lastIndexOf("    }\n")
        assertTrue("first entry must be comma separated", firstClose > 0)
        assertTrue("last entry must not be comma separated", lastClose > firstClose)
    }

    // ---------- JSON todos ----------

    private fun todo(
        title: String = "写周报",
        scope: TodoScope = TodoScope.WEEK,
        periodKey: String = "2026-W41",
        createdAt: Long = base,
        doneAt: Long? = null,
    ) = Todo(
        id = 1L,
        title = title,
        scope = scope,
        periodKey = periodKey,
        createdAt = createdAt,
        doneAt = doneAt,
    )

    @Test
    fun `json carries todos and reports the new schema version`() {
        val json = Exporter.json(
            rows = listOf(row()),
            zone = zone,
            todos = listOf(todo(), todo(title = "跑步", scope = TodoScope.DAY, periodKey = "2026-10-05")),
        )
        assertTrue(json.contains("\"schemaVersion\": 2"))
        assertTrue(json.contains("\"todoCount\": 2"))
        assertTrue(json.contains("\"todos\": ["))
        assertTrue(json.contains("\"period\": \"2026-W41\""))
        assertTrue(json.contains("\"scope\": \"DAY\""))
    }

    @Test
    fun `json marks an unticked todo as not done`() {
        val json = Exporter.json(emptyList(), zone, listOf(todo(doneAt = null)))
        assertTrue(json.contains("\"done\": false"))
        assertTrue(json.contains("\"doneTime\": null"))
        assertTrue(json.contains("\"doneAt\": null"))
    }

    @Test
    fun `json records the moment a todo was ticked`() {
        val done = base + 3_600_000L
        val json = Exporter.json(emptyList(), zone, listOf(todo(doneAt = done)))
        assertTrue(json.contains("\"done\": true"))
        assertTrue(json.contains("\"doneTime\": $done"))
    }

    @Test
    fun `json escapes quotes in a todo title`() {
        val json = Exporter.json(emptyList(), zone, listOf(todo(title = "他说\"你好\"")))
        assertTrue(json.contains("\"title\": \"他说\\\"你好\\\"\""))
    }

    @Test
    fun `json without todos is still well formed`() {
        val json = Exporter.json(emptyList(), zone)
        assertTrue(json.contains("\"todoCount\": 0"))
        assertTrue(json.contains("\"todos\": ["))
    }

    // ---------- file name ----------

    @Test
    fun `suggested file name has no characters that are illegal on Windows`() {
        val name = Exporter.suggestedFileName("csv", base)
        assertTrue(name.startsWith("TimeTrack_"))
        assertTrue(name.endsWith(".csv"))
        assertFalse(name.contains(':'))
        assertFalse(name.contains(' '))
    }
}
