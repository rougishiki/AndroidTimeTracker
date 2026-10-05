package com.timetrack.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.IsoFields
import java.util.Locale

/** Whether a todo belongs to a single day or to a whole ISO week. */
enum class TodoScope { DAY, WEEK }

/**
 * Period keys and the date arithmetic shared by the day and week todo lists.
 *
 * A key is a stable, sortable string stored verbatim in SQLite:
 *
 * ```
 * DAY  -> "2026-10-05"
 * WEEK -> "2026-W53"
 * ```
 *
 * The week key is built from the **ISO week-based year**, never from
 * `LocalDate.year`. The two disagree whenever a week straddles New Year, and
 * the failure is silent: 2026-12-28 and 2027-01-01 sit in the same ISO week,
 * but the calendar-year form yields `2026-W53` for the first and `2027-W53` for
 * the second. One week becomes two periods, so a weekly todo appears in both
 * and ticking one leaves the other behind. Between 2020 and 2030 there are 19
 * such days.
 *
 * [parseWeekKey] therefore goes through `TemporalField` arithmetic rather than
 * rebuilding a date from year and week number by hand; the round trip
 * `weekKey -> weekStart` is verified for every day from 2020 to 2030 in the
 * tests.
 *
 * Everything here is pure date maths with no [java.time.ZoneId]. A period is a
 * calendar concept; the repository is what turns one into millisecond bounds,
 * using the zone it was constructed with.
 */
object TodoPeriod {

    private const val ROWS_IN_MONTH_GRID = 6
    private const val DAYS_IN_WEEK = 7
    private const val MONDAY = 1

    /** Pinned so the stored key never picks up a locale's own digits. */
    private val DAY_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.US)

    /** `2026-10-05`. */
    fun dayKey(date: LocalDate): String = DAY_FORMAT.format(date)

    /**
     * `2026-W53`. Week numbers are zero padded so that plain string ordering in
     * SQL matches chronological ordering.
     */
    fun weekKey(date: LocalDate): String = String.format(
        Locale.US,
        "%04d-W%02d",
        date.get(IsoFields.WEEK_BASED_YEAR),
        date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
    )

    fun key(scope: TodoScope, date: LocalDate): String = when (scope) {
        TodoScope.DAY -> dayKey(date)
        TodoScope.WEEK -> weekKey(date)
    }

    /** The Monday that opens [date]'s ISO week. */
    fun weekStart(date: LocalDate): LocalDate =
        date.with(ChronoField.DAY_OF_WEEK, MONDAY.toLong())

    /** The Sunday that closes [date]'s ISO week (inclusive). */
    fun weekEnd(date: LocalDate): LocalDate = weekStart(date).plusDays(6)

    /** Monday through Sunday of [date]'s ISO week. */
    fun weekDays(date: LocalDate): List<LocalDate> {
        val monday = weekStart(date)
        return (0L until DAYS_IN_WEEK.toLong()).map { monday.plusDays(it) }
    }

    /** ISO week number, for display as `第41周`. */
    fun weekNumber(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    fun parseDayKey(key: String): LocalDate = LocalDate.parse(key, DAY_FORMAT)

    /**
     * The Monday of the ISO week named by `2026-W53`.
     *
     * Jan 4 is always inside ISO week 1, so anchoring there and moving to the
     * requested week number avoids every year-boundary special case.
     */
    fun parseWeekKey(key: String): LocalDate {
        val year = key.substring(0, 4).toInt()
        val week = key.substring(5).removePrefix("W").toInt()
        return LocalDate.of(year, 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
            .with(ChronoField.DAY_OF_WEEK, MONDAY.toLong())
    }

    fun parse(scope: TodoScope, key: String): LocalDate = when (scope) {
        TodoScope.DAY -> parseDayKey(key)
        TodoScope.WEEK -> parseWeekKey(key)
    }

    /** Moves [key] by [periods] periods; negative goes backwards. */
    fun shift(scope: TodoScope, key: String, periods: Long): String = when (scope) {
        TodoScope.DAY -> dayKey(parseDayKey(key).plusDays(periods))
        TodoScope.WEEK -> weekKey(parseWeekKey(key).plusWeeks(periods))
    }

    /**
     * Six Monday-first rows covering [month].
     *
     * Always six rows, so the grid keeps a fixed height as the user pages
     * through months, and the first column is always Monday. Material's own
     * `DatePicker` takes the first day of the week from the device locale and
     * offers no way to override it, which is why this grid is hand rolled.
     */
    fun monthRows(month: YearMonth): List<List<LocalDate>> {
        val firstCell = weekStart(month.atDay(1))
        return (0 until ROWS_IN_MONTH_GRID).map { row ->
            (0 until DAYS_IN_WEEK).map { column ->
                firstCell.plusDays((row * DAYS_IN_WEEK + column).toLong())
            }
        }
    }

    // --- projecting a weekly todo onto a day of the daily list ---------------

    /**
     * Whether a todo was still open when the day starting at [dayStart] began,
     * and therefore belongs in that day's list of weekly items.
     *
     * Deriving this from [doneAt] alone is what lets a past day be redrawn
     * exactly as it looked at the time, with no snapshot table and no scheduled
     * job: anything finished later, or never finished, was plainly still open.
     */
    fun wasOpenOn(doneAt: Long?, dayStart: Long): Boolean = doneAt == null || doneAt >= dayStart

    /** Whether [doneAt] falls inside the half-open day `[dayStart, dayEnd)`. */
    fun completedOn(doneAt: Long?, dayStart: Long, dayEnd: Long): Boolean =
        doneAt != null && doneAt >= dayStart && doneAt < dayEnd
}
