package com.timetrack.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoField
import java.time.temporal.IsoFields

/**
 * Period boundaries for the statistics screen.
 *
 * Weeks are ISO weeks starting on Monday, matching the todo lists and the local
 * convention. [mondayOf] and [weekNumber] deliberately duplicate two one-line
 * rules that also live in [TodoPeriod]; `StatsPeriodTest` asserts the two copies
 * agree day by day across a decade, which is what stops them drifting apart
 * without forcing a rename of code that is already tested and shipped.
 *
 * Everything here is pure date arithmetic with no [java.time.ZoneId]: a period is
 * a calendar concept, and [TimeTrackRepository] is what turns one into
 * millisecond bounds using the zone it was constructed with.
 */
object StatsPeriod {

    /**
     * `[start, untilExclusive)` covering the period that contains [day].
     *
     * Half-open, so a period never overlaps the one before it. That is what lets
     * the "previous period" comparison line up without double counting the days
     * on a boundary.
     */
    fun bounds(mode: StatsMode, day: LocalDate): Pair<LocalDate, LocalDate> = when (mode) {
        StatsMode.DAY -> day to day.plusDays(1)
        StatsMode.WEEK -> mondayOf(day).let { it to it.plusWeeks(1) }
        StatsMode.MONTH -> day.withDayOfMonth(1).let { it to it.plusMonths(1) }
    }

    /** The period immediately before the one containing [day]. */
    fun previousBounds(mode: StatsMode, day: LocalDate): Pair<LocalDate, LocalDate> {
        val start = bounds(mode, day).first
        val anchor = when (mode) {
            StatsMode.DAY -> start.minusDays(1)
            StatsMode.WEEK -> start.minusWeeks(1)
            StatsMode.MONTH -> start.minusMonths(1)
        }
        return bounds(mode, anchor)
    }

    /**
     * Steps [units] whole periods from [day]; negative goes back.
     *
     * The anchor is normalised to the start of its period first. Without that,
     * stepping months from the 31st would clamp to the 28th and keep drifting
     * further from the month the user meant.
     */
    fun shift(mode: StatsMode, day: LocalDate, units: Long): LocalDate {
        val anchor = bounds(mode, day).first
        return when (mode) {
            StatsMode.DAY -> anchor.plusDays(units)
            StatsMode.WEEK -> anchor.plusWeeks(units)
            StatsMode.MONTH -> anchor.plusMonths(units)
        }
    }

    /** The Monday that opens [date]'s ISO week. */
    fun mondayOf(date: LocalDate): LocalDate =
        date.with(ChronoField.DAY_OF_WEEK, DayOfWeek.MONDAY.value.toLong())

    /** ISO week number, for display as `第41周`. */
    fun weekNumber(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** The last day inside a window, given its exclusive end. */
    fun lastDayInclusive(untilExclusive: LocalDate): LocalDate = untilExclusive.minusDays(1)
}
