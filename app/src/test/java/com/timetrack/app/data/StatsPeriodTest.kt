package com.timetrack.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The statistics screen compares a period with the one before it, so the two
 * windows have to line up exactly. A gap loses time; an overlap counts it twice.
 */
class StatsPeriodTest {

    private val wednesday = LocalDate.of(2026, 10, 7)

    @Test
    fun `a day window is half-open`() {
        val (start, until) = StatsPeriod.bounds(StatsMode.DAY, wednesday)
        assertEquals(wednesday, start)
        assertEquals(wednesday.plusDays(1), until)
    }

    @Test
    fun `a week window runs from Monday to the next Monday`() {
        val (start, until) = StatsPeriod.bounds(StatsMode.WEEK, wednesday)
        assertEquals(LocalDate.of(2026, 10, 5), start)
        assertEquals(LocalDate.of(2026, 10, 12), until)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, StatsPeriod.lastDayInclusive(until).dayOfWeek)
    }

    @Test
    fun `a month window runs to the first of the next month`() {
        val (start, until) = StatsPeriod.bounds(StatsMode.MONTH, wednesday)
        assertEquals(LocalDate.of(2026, 10, 1), start)
        assertEquals(LocalDate.of(2026, 11, 1), until)
    }

    @Test
    fun `the previous period ends exactly where the current one begins`() {
        val days = listOf(
            LocalDate.of(2026, 10, 7),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2027, 1, 1),
        )
        for (mode in StatsMode.entries) {
            for (day in days) {
                val current = StatsPeriod.bounds(mode, day)
                val previous = StatsPeriod.previousBounds(mode, day)
                assertEquals(
                    "$mode around $day must be adjacent, not overlapping or gapped",
                    current.first,
                    previous.second,
                )
                assertTrue(
                    "$mode around $day: the previous period must start earlier",
                    previous.first.isBefore(current.first),
                )
            }
        }
    }

    @Test
    fun `stepping months is stable when starting from the 31st`() {
        // Naive month arithmetic clamps 2026-01-31 to 2026-02-28 and then keeps
        // drifting further back; normalising to the first of the month avoids it.
        var day = LocalDate.of(2026, 1, 31)
        val visited = mutableListOf<LocalDate>()
        repeat(12) {
            visited += StatsPeriod.bounds(StatsMode.MONTH, day).first
            day = StatsPeriod.shift(StatsMode.MONTH, day, 1)
        }
        assertEquals((1..12).map { LocalDate.of(2026, it, 1) }, visited)
    }

    @Test
    fun `stepping forward and back returns to the same period`() {
        val days = listOf(
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2026, 1, 1),
        )
        for (mode in StatsMode.entries) {
            for (day in days) {
                val forward = StatsPeriod.shift(mode, day, 1)
                val back = StatsPeriod.shift(mode, forward, -1)
                assertEquals(
                    "$mode starting from $day",
                    StatsPeriod.bounds(mode, day).first,
                    StatsPeriod.bounds(mode, back).first,
                )
            }
        }
    }

    @Test
    fun `week rules agree with the todo lists`() {
        // The Monday rule and the ISO week number are written twice on purpose.
        // This is the test that stops the two copies drifting apart.
        var date = LocalDate.of(2020, 1, 1)
        var checked = 0
        while (date.isBefore(LocalDate.of(2030, 1, 1))) {
            assertEquals("mondayOf disagreed on $date", TodoPeriod.weekStart(date), StatsPeriod.mondayOf(date))
            assertEquals("weekNumber disagreed on $date", TodoPeriod.weekNumber(date), StatsPeriod.weekNumber(date))
            checked++
            date = date.plusDays(7)
        }
        assertEquals(522, checked)
    }
}
