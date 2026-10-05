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
}
