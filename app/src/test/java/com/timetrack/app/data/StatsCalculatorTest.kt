package com.timetrack.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the aggregation rules that decide what the statistics screen shows.
 * These are pure functions, so no device or emulator is involved.
 */
class StatsCalculatorTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private fun session(
        taskId: Long = 1L,
        name: String = "任务",
        start: Long,
        end: Long?,
    ) = SessionWithTask(
        id = taskId,
        taskId = taskId,
        startTime = start,
        endTime = end,
        taskName = name,
        taskColorArgb = 0xFF112233.toInt(),
    )

    @Test
    fun `counts a session fully inside the window`() {
        val base = 1_700_000_000_000L
        val slices = StatsCalculator.aggregate(
            sessions = listOf(session(start = base, end = base + hour)),
            windowStart = base - day,
            windowEnd = base + day,
            now = base + 10 * day,
        )
        assertEquals(1, slices.size)
        assertEquals(hour, slices[0].millis)
    }

    @Test
    fun `splits a session crossing midnight evenly between the two days`() {
        val midnight = 1_700_000_000_000L
        val crossing = session(start = midnight - 30 * minute, end = midnight + 30 * minute)

        val previousDay = StatsCalculator.aggregate(
            sessions = listOf(crossing),
            windowStart = midnight - day,
            windowEnd = midnight,
            now = midnight + day,
        )
        val nextDay = StatsCalculator.aggregate(
            sessions = listOf(crossing),
            windowStart = midnight,
            windowEnd = midnight + day,
            now = midnight + day,
        )

        assertEquals(30 * minute, previousDay.single().millis)
        assertEquals(30 * minute, nextDay.single().millis)
    }

    @Test
    fun `measures a running session up to now`() {
        val base = 1_700_000_000_000L
        val slices = StatsCalculator.aggregate(
            sessions = listOf(session(start = base, end = null)),
            windowStart = base - day,
            windowEnd = base + day,
            now = base + 42 * minute,
        )
        assertEquals(42 * minute, slices.single().millis)
    }

    @Test
    fun `clips a session that starts before the window`() {
        val base = 1_700_000_000_000L
        val slices = StatsCalculator.aggregate(
            // 3 hours long, but only the last hour falls inside the window.
            sessions = listOf(session(start = base - 2 * hour, end = base + hour)),
            windowStart = base,
            windowEnd = base + day,
            now = base + day,
        )
        assertEquals(hour, slices.single().millis)
    }

    @Test
    fun `ignores sessions entirely outside the window`() {
        val base = 1_700_000_000_000L
        val slices = StatsCalculator.aggregate(
            sessions = listOf(session(start = base + 5 * day, end = base + 5 * day + hour)),
            windowStart = base,
            windowEnd = base + day,
            now = base + 10 * day,
        )
        assertTrue(slices.isEmpty())
    }

    @Test
    fun `merges several sessions of the same task`() {
        val base = 1_700_000_000_000L
        val slices = StatsCalculator.aggregate(
            sessions = listOf(
                session(taskId = 7, name = "写周报", start = base, end = base + 20 * minute),
                session(taskId = 7, name = "写周报", start = base + hour, end = base + hour + 25 * minute),
            ),
            windowStart = base - day,
            windowEnd = base + day,
            now = base + day,
        )
        assertEquals(1, slices.size)
        assertEquals(45 * minute, slices[0].millis)
    }

    @Test
    fun `keeps distinct tasks apart and sorts them by time descending`() {
        val base = 1_700_000_000_000L
        val slices = StatsCalculator.aggregate(
            sessions = listOf(
                session(taskId = 1, name = "短的", start = base, end = base + 5 * minute),
                session(taskId = 2, name = "长的", start = base + hour, end = base + hour + 50 * minute),
            ),
            windowStart = base - day,
            windowEnd = base + day,
            now = base + day,
        )
        assertEquals(listOf("长的", "短的"), slices.map { it.name })
        assertEquals(2, slices.size)
    }

    @Test
    fun `never reports a negative duration when the clock jumps backwards`() {
        val base = 1_700_000_000_000L
        // An endTime before startTime is what an NTP correction or manual clock
        // change can produce; it must be dropped rather than counted negatively.
        val slices = StatsCalculator.aggregate(
            sessions = listOf(session(start = base, end = base - hour)),
            windowStart = base - day,
            windowEnd = base + day,
            now = base + day,
        )
        assertTrue(slices.isEmpty())
    }

    @Test
    fun `totals match the sum of the slices`() {
        val base = 1_700_000_000_000L
        val slices = StatsCalculator.aggregate(
            sessions = listOf(
                session(taskId = 1, name = "A", start = base, end = base + 10 * minute),
                session(taskId = 2, name = "B", start = base + hour, end = base + hour + 30 * minute),
            ),
            windowStart = base - day,
            windowEnd = base + day,
            now = base + day,
        )
        assertEquals(40 * minute, slices.sumOf { it.millis })
    }

    // ---------- period comparison ----------

    private fun slice(taskId: Long, name: String, millis: Long) = TaskSlice(
        taskId = taskId,
        name = name,
        colorArgb = 0xFF112233.toInt(),
        millis = millis,
    )

    @Test
    fun `compare keeps a task that only appears in the current period`() {
        val rows = StatsCalculator.compare(
            current = listOf(slice(1, "新出现的", hour)),
            previous = emptyList(),
        )
        assertEquals(1, rows.size)
        assertEquals(hour, rows[0].currentMillis)
        assertEquals(0L, rows[0].previousMillis)
        assertEquals(hour, rows[0].deltaMillis)
    }

    @Test
    fun `compare keeps a task that dropped out entirely`() {
        val rows = StatsCalculator.compare(
            current = emptyList(),
            previous = listOf(slice(1, "上一期做过", hour)),
        )
        assertEquals(1, rows.size)
        assertEquals(0L, rows[0].currentMillis)
        assertEquals(hour, rows[0].previousMillis)
        assertEquals(-hour, rows[0].deltaMillis)
    }

    @Test
    fun `compare matches tasks across the two periods by id, not by name`() {
        val rows = StatsCalculator.compare(
            current = listOf(slice(7, "写周报", 2 * hour)),
            previous = listOf(slice(7, "写周报", hour)),
        )
        assertEquals(1, rows.size)
        assertEquals(2 * hour, rows[0].currentMillis)
        assertEquals(hour, rows[0].previousMillis)
        assertEquals(hour, rows[0].deltaMillis)
    }

    @Test
    fun `compare sorts by the current period first`() {
        val rows = StatsCalculator.compare(
            current = listOf(slice(1, "A", 30 * minute), slice(2, "B", hour)),
            previous = listOf(slice(1, "A", 5 * hour), slice(2, "B", minute)),
        )
        // B leads because it is bigger this period, even though A was bigger before.
        assertEquals(listOf("B", "A"), rows.map { it.name })
    }

    @Test
    fun `compare sorts a task that fell to zero by what it used to be worth`() {
        val rows = StatsCalculator.compare(
            current = listOf(slice(1, "还在", 10 * minute)),
            previous = listOf(slice(2, "大的旧项", 5 * hour), slice(3, "小的旧项", minute)),
        )
        // Both of the vanished rows have a zero current value, so the previous
        // period decides their order rather than leaving them at the bottom.
        assertEquals(listOf("还在", "大的旧项", "小的旧项"), rows.map { it.name })
    }

    @Test
    fun `compare of two empty periods is empty`() {
        assertTrue(StatsCalculator.compare(emptyList(), emptyList()).isEmpty())
    }

    // ---------- counting intervals ----------

    @Test
    fun `contributing intervals counts rows, not tasks`() {
        val base = 1_700_000_000_000L
        // Three rows, two tasks, all inside the window.
        val count = StatsCalculator.contributingIntervals(
            sessions = listOf(
                session(taskId = 1, name = "A", start = base, end = base + 10 * minute),
                session(taskId = 1, name = "A", start = base + hour, end = base + hour + 10 * minute),
                session(taskId = 2, name = "B", start = base + 2 * hour, end = base + 2 * hour + 10 * minute),
            ),
            windowStart = base - day,
            windowEnd = base + day,
            now = base + day,
        )
        assertEquals(3, count)
    }

    @Test
    fun `contributing intervals skips rows the window clips to nothing`() {
        val base = 1_700_000_000_000L
        val count = StatsCalculator.contributingIntervals(
            sessions = listOf(
                session(taskId = 1, name = "里面", start = base, end = base + 10 * minute),
                session(taskId = 2, name = "完全在外面", start = base + 5 * day, end = base + 5 * day + hour),
                // An interval that ends before it starts is what a clock change
                // can produce; it must not be counted either.
                session(taskId = 3, name = "负的", start = base, end = base - hour),
            ),
            windowStart = base - day,
            windowEnd = base + day,
            now = base + day,
        )
        assertEquals(1, count)
    }
}
