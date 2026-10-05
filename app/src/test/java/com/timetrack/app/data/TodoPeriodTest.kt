package com.timetrack.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Pins the rules that decide which period a todo belongs to.
 *
 * The interesting cases are all at year boundaries, where the ISO week-based
 * year and the calendar year disagree. Those are impossible to check by eye and
 * painful to reproduce on a phone, which is exactly why they live here.
 */
class TodoPeriodTest {

    // --- day keys ------------------------------------------------------------

    @Test
    fun `day key is a zero padded ISO date`() {
        assertEquals("2026-10-05", TodoPeriod.dayKey(LocalDate.of(2026, 10, 5)))
        assertEquals("2026-01-09", TodoPeriod.dayKey(LocalDate.of(2026, 1, 9)))
    }

    // --- the ISO week-based-year trap ---------------------------------------

    @Test
    fun `week key uses the ISO week-based year rather than the calendar year`() {
        // 2027-01-01 is a Friday. Its ISO week opened on 2026-12-28 and is week
        // 53 of *2026*; the calendar year alone would have said 2027.
        assertEquals("2026-W53", TodoPeriod.weekKey(LocalDate.of(2027, 1, 1)))
        // 2025-12-29 is the Monday of ISO week 1 of 2026.
        assertEquals("2026-W01", TodoPeriod.weekKey(LocalDate.of(2025, 12, 29)))
        assertEquals("2025-W01", TodoPeriod.weekKey(LocalDate.of(2024, 12, 30)))
        assertEquals("2026-W41", TodoPeriod.weekKey(LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun `a single ISO week spanning New Year never splits into two keys`() {
        val keys = (0L until 7L)
            .map { TodoPeriod.weekKey(LocalDate.of(2026, 12, 28).plusDays(it)) }
            .toSet()
        assertEquals(setOf("2026-W53"), keys)
    }

    @Test
    fun `2026 really does have 53 ISO weeks`() {
        assertEquals(53, TodoPeriod.weekNumber(LocalDate.of(2026, 12, 28)))
        assertEquals("2026-W53", TodoPeriod.weekKey(LocalDate.of(2026, 12, 28)))
    }

    @Test
    fun `week keys sort chronologically as plain strings`() {
        val keys = listOf(
            LocalDate.of(2025, 12, 29),
            LocalDate.of(2026, 1, 5),
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 12, 28),
            LocalDate.of(2027, 1, 4),
        ).map { TodoPeriod.weekKey(it) }
        assertEquals(keys.sorted(), keys)
    }

    // --- week edges ----------------------------------------------------------

    @Test
    fun `a week runs from Monday to Sunday`() {
        val wednesday = LocalDate.of(2026, 10, 7)
        assertEquals(LocalDate.of(2026, 10, 5), TodoPeriod.weekStart(wednesday))
        assertEquals(LocalDate.of(2026, 10, 11), TodoPeriod.weekEnd(wednesday))
        assertEquals(DayOfWeek.MONDAY, TodoPeriod.weekStart(wednesday).dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, TodoPeriod.weekEnd(wednesday).dayOfWeek)
    }

    @Test
    fun `week days are listed Monday first`() {
        val days = TodoPeriod.weekDays(LocalDate.of(2026, 10, 7))
        assertEquals(7, days.size)
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, days.last().dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 11), days.last())
    }

    // --- round trip ----------------------------------------------------------

    @Test
    fun `every day from 2020 to 2030 round trips back to its own Monday`() {
        var checked = 0
        var date = LocalDate.of(2020, 1, 1)
        while (date.isBefore(LocalDate.of(2031, 1, 1))) {
            assertEquals(
                "round trip failed for $date",
                TodoPeriod.weekStart(date),
                TodoPeriod.parseWeekKey(TodoPeriod.weekKey(date)),
            )
            checked++
            date = date.plusDays(1)
        }
        assertEquals(4018, checked)
    }

    // --- shifting ------------------------------------------------------------

    @Test
    fun `shifting a week by one equals shifting a date by seven days`() {
        val monday = LocalDate.of(2026, 10, 5)
        assertEquals(
            TodoPeriod.weekKey(monday.plusWeeks(1)),
            TodoPeriod.shift(TodoScope.WEEK, TodoPeriod.weekKey(monday), 1),
        )
        assertEquals("2026-W40", TodoPeriod.shift(TodoScope.WEEK, "2026-W41", -1))
    }

    @Test
    fun `shifting a week across New Year follows the ISO week, not the year`() {
        // The obvious answer would be 2026-W52, but 2026 has 53 ISO weeks.
        assertEquals("2026-W52", TodoPeriod.shift(TodoScope.WEEK, "2026-W53", -1))
        assertEquals("2026-W53", TodoPeriod.shift(TodoScope.WEEK, "2027-W01", -1))
        assertEquals("2027-W01", TodoPeriod.shift(TodoScope.WEEK, "2026-W53", 1))
    }

    @Test
    fun `shifting a day crosses months and years`() {
        assertEquals("2026-11-01", TodoPeriod.shift(TodoScope.DAY, "2026-10-31", 1))
        assertEquals("2025-12-31", TodoPeriod.shift(TodoScope.DAY, "2026-01-01", -1))
    }

    // --- calendar grid -------------------------------------------------------

    @Test
    fun `month grid is six Monday-first rows covering the whole month`() {
        val month = YearMonth.of(2026, 10)
        val rows = TodoPeriod.monthRows(month)

        assertEquals(6, rows.size)
        assertTrue(rows.all { it.size == 7 })
        assertTrue(rows.all { it.first().dayOfWeek == DayOfWeek.MONDAY })

        val flat = rows.flatten()
        assertEquals(42, flat.size)
        assertEquals(TodoPeriod.weekStart(month.atDay(1)), flat.first())
        for (day in 1..month.lengthOfMonth()) {
            assertTrue("grid is missing ${month.atDay(day)}", flat.contains(month.atDay(day)))
        }
    }

    @Test
    fun `month grid needs no leading blank row when the month starts on a Monday`() {
        // 2026-06-01 is a Monday, so the first cell is the 1st itself.
        val rows = TodoPeriod.monthRows(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), rows.first().first())
    }

    // --- locale safety -------------------------------------------------------

    @Test
    fun `keys keep ASCII digits even under an Arabic-digit locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
            assertEquals("2026-10-05", TodoPeriod.dayKey(LocalDate.of(2026, 10, 5)))
            assertEquals("2026-W41", TodoPeriod.weekKey(LocalDate.of(2026, 10, 5)))
        } finally {
            Locale.setDefault(original)
        }
    }

    // --- projecting a weekly todo onto a day ---------------------------------

    @Test
    fun `a weekly todo belongs to every day it was still open`() {
        val dayStart = 1_700_000_000_000L
        val dayEnd = dayStart + 86_400_000L

        assertTrue("never finished, so still open", TodoPeriod.wasOpenOn(null, dayStart))
        assertTrue("finished later, so it was open that day", TodoPeriod.wasOpenOn(dayEnd + 1, dayStart))
        assertTrue("finished at the very start of the day", TodoPeriod.wasOpenOn(dayStart, dayStart))
        assertFalse("finished the day before", TodoPeriod.wasOpenOn(dayStart - 1, dayStart))
    }

    @Test
    fun `completed on is the half-open window of one day`() {
        val dayStart = 1_700_000_000_000L
        val dayEnd = dayStart + 86_400_000L

        assertTrue(TodoPeriod.completedOn(dayStart, dayStart, dayEnd))
        assertTrue(TodoPeriod.completedOn(dayEnd - 1, dayStart, dayEnd))
        assertFalse(TodoPeriod.completedOn(dayEnd, dayStart, dayEnd))
        assertFalse(TodoPeriod.completedOn(dayStart - 1, dayStart, dayEnd))
        assertFalse(TodoPeriod.completedOn(null, dayStart, dayEnd))
    }
}
