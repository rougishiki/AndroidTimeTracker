package com.timetrack.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The millisecond bounds a wall-clock edit produced. */
data class SessionSpan(val startTime: Long, val endTime: Long)

/**
 * Turns the wall-clock times a user typed back into an interval.
 *
 * Editing a tracked interval needs one rule the rest of the app can be built on,
 * and it is the awkward one: a session may run past midnight, but the editor
 * only offers two times of day. The rule here is that
 *
 * - an end **after** the start is the same day,
 * - an end **before** the start is the next day,
 * - an end **equal** to the start is rejected as a mistake.
 *
 * That covers `23:30 -> 00:30` without asking the user for a date, and refuses
 * the one input that has no sensible reading.
 *
 * Each time is resolved against the zone rather than added to the day's start,
 * because a daylight-saving day is not 24 hours long. On 2026-03-29 Berlin
 * skips 02:00-03:00, so adding three and a half hours to midnight lands at 04:30
 * local instead of 03:30, and `01:30 -> 03:30` would be reported as two hours
 * instead of the one that actually elapsed. `atZone` applies the offset in force
 * at each local time, which keeps the elapsed time honest on the two days a
 * year that are 23 or 25 hours long. A local time inside the skipped hour is
 * shifted forward, and an ambiguous time in a repeated hour takes the earlier
 * offset — both are `atZone`'s documented resolutions.
 *
 * Pure date arithmetic with an injected [ZoneId], so all of this is testable
 * without a device. [TimeTrackRepository] is what supplies the zone.
 */
object SessionTimes {

    const val MINUTES_PER_DAY = 24 * 60

    fun toMinutes(hour: Int, minute: Int): Int = hour * 60 + minute

    fun hourOf(minutes: Int): Int = Math.floorMod(minutes, MINUTES_PER_DAY) / 60

    fun minuteOf(minutes: Int): Int = Math.floorMod(minutes, MINUTES_PER_DAY) % 60

    /** `09:05`, for the editor's fields. */
    fun label(minutes: Int): String =
        "%02d:%02d".format(java.util.Locale.US, hourOf(minutes), minuteOf(minutes))

    /** Minutes since the start of the day for [millis]. */
    fun minutesOf(millis: Long, zone: ZoneId): Int {
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return toMinutes(time.hour, time.minute)
    }

    /** The local date [millis] falls on, which is the anchor the editor edits within. */
    fun dayOf(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /**
     * The interval described by [startMinutes] and [endMinutes] on [day].
     *
     * Returns null for the one input with no sensible reading, so the caller can
     * tell the user rather than inventing a duration.
     */
    fun spanOf(day: LocalDate, startMinutes: Int, endMinutes: Int, zone: ZoneId): SessionSpan? {
        if (startMinutes == endMinutes) return null
        val endDay = if (endMinutes < startMinutes) day.plusDays(1) else day
        return SessionSpan(
            startTime = instantOf(day, startMinutes, zone),
            endTime = instantOf(endDay, endMinutes, zone),
        )
    }

    /** Whether an interval ends on a later calendar day than it started. */
    fun crossesMidnight(startTime: Long, endTime: Long, zone: ZoneId): Boolean =
        dayOf(startTime, zone) != dayOf(endTime, zone)

    /**
     * Whether an interval has been open long enough that the user probably
     * walked away without pressing stop.
     *
     * The threshold comes from the caller; the boundary lives here so it is unit
     * tested rather than buried in a view model that needs a device to run. A
     * clock that jumped backwards cannot make an interval look forgotten,
     * because the difference would be negative.
     */
    fun isProbablyForgotten(startTime: Long, now: Long, thresholdMillis: Long): Boolean =
        now - startTime >= thresholdMillis

    private fun instantOf(day: LocalDate, minutes: Int, zone: ZoneId): Long =
        day.atTime(hourOf(minutes), minuteOf(minutes))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
}
