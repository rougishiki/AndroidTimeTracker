package com.timetrack.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The editor only offers two times of day, but a tracked interval can run past
 * midnight — and a day can be 23 or 25 hours long. These tests pin both, because
 * the wall-clock arithmetic that looks obviously right is wrong on those days.
 *
 * The daylight-saving expectations were taken from the JDK rather than reasoned
 * out, and `_probe/DstCheck.java` reproduces them.
 */
class SessionTimesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val day: LocalDate = LocalDate.of(2026, 10, 5)

    private fun span(startHHmm: String, endHHmm: String, on: LocalDate = day, at: ZoneId = zone) =
        SessionTimes.spanOf(on, minutes(startHHmm), minutes(endHHmm), at)

    private fun lengthOf(s: SessionSpan?): Long = s!!.endTime - s.startTime

    private fun minutes(hhmm: String): Int {
        val (h, m) = hhmm.split(":")
        return SessionTimes.toMinutes(h.toInt(), m.toInt())
    }

    // --- labels --------------------------------------------------------------

    @Test
    fun `labels are zero padded to two digits`() {
        assertEquals("09:05", SessionTimes.label(minutes("09:05")))
        assertEquals("00:00", SessionTimes.label(0))
        assertEquals("23:59", SessionTimes.label(minutes("23:59")))
    }

    @Test
    fun `minutes round trip through hour and minute`() {
        for (hhmm in listOf("00:00", "07:30", "12:00", "23:59")) {
            val m = minutes(hhmm)
            val back = SessionTimes.toMinutes(SessionTimes.hourOf(m), SessionTimes.minuteOf(m))
            assertEquals(hhmm, SessionTimes.label(back))
        }
    }

    // --- the same-day case ---------------------------------------------------

    @Test
    fun `an end after the start stays on the same day`() {
        val span = span("09:00", "10:30")!!
        assertEquals(90 * 60_000L, lengthOf(span))
        assertFalse(SessionTimes.crossesMidnight(span.startTime, span.endTime, zone))
        assertEquals(day, SessionTimes.dayOf(span.startTime, zone))
        assertEquals(day, SessionTimes.dayOf(span.endTime, zone))
    }

    // --- the midnight case ---------------------------------------------------

    @Test
    fun `an end before the start is read as the next day`() {
        val span = span("23:30", "00:30")!!
        assertEquals(60 * 60_000L, lengthOf(span))
        assertEquals(day, SessionTimes.dayOf(span.startTime, zone))
        assertEquals(day.plusDays(1), SessionTimes.dayOf(span.endTime, zone))
        assertTrue(SessionTimes.crossesMidnight(span.startTime, span.endTime, zone))
    }

    @Test
    fun `a long crossing lands the end on the next day too`() {
        val span = span("22:00", "03:00")!!
        assertEquals(5 * 60 * 60_000L, lengthOf(span))
        assertEquals(day.plusDays(1), SessionTimes.dayOf(span.endTime, zone))
    }

    // --- the one invalid input ----------------------------------------------

    @Test
    fun `an end equal to the start is refused`() {
        assertNull(span("09:00", "09:00"))
        assertNull(span("00:00", "00:00"))
    }

    // --- reading a stored interval back into the editor ----------------------

    @Test
    fun `stored millis read back as the times that produced them`() {
        val span = span("23:30", "00:30")!!
        assertEquals(minutes("23:30"), SessionTimes.minutesOf(span.startTime, zone))
        assertEquals(minutes("00:30"), SessionTimes.minutesOf(span.endTime, zone))

        // Reading the times back and saving them unchanged must be a no-op, or
        // merely opening the editor would move the interval.
        val again = SessionTimes.spanOf(
            day,
            SessionTimes.minutesOf(span.startTime, zone),
            SessionTimes.minutesOf(span.endTime, zone),
            zone,
        )!!
        assertEquals(span, again)
    }

    // --- daylight saving ----------------------------------------------------

    @Test
    fun `a spring-forward day is 23 hours and the span follows the real clock`() {
        val shortDay = LocalDate.of(2026, 3, 29)
        // 01:30 CET -> 03:30 CEST is one real hour, not the two that a
        // wall-clock subtraction suggests.
        assertEquals(60 * 60_000L, lengthOf(span("01:30", "03:30", on = shortDay, at = berlin)))
        assertEquals(120 * 60_000L, lengthOf(span("01:30", "04:30", on = shortDay, at = berlin)))
    }

    @Test
    fun `a time inside the skipped hour resolves forward instead of failing`() {
        // 02:30 does not exist on 2026-03-29 in Berlin; the zone shifts it to
        // 03:30 CEST. Any answer is better than a crash on a legitimate edit.
        val span = span("02:30", "04:30", on = LocalDate.of(2026, 3, 29), at = berlin)!!
        val startLocal = Instant.ofEpochMilli(span.startTime).atZone(berlin).toLocalTime()
        assertEquals(LocalTime.of(3, 30), startLocal)
    }

    @Test
    fun `an ambiguous time in the repeated hour takes the earlier offset`() {
        val longDay = LocalDate.of(2026, 10, 25)
        // 02:30 happens twice; atZone picks CEST (+02:00), so 01:30 -> 02:30 is
        // one hour while 01:30 -> 03:30 is three.
        assertEquals(60 * 60_000L, lengthOf(span("01:30", "02:30", on = longDay, at = berlin)))
        assertEquals(180 * 60_000L, lengthOf(span("01:30", "03:30", on = longDay, at = berlin)))
    }

    @Test
    fun `a zone without daylight saving is unaffected all year`() {
        // Asia/Shanghai has no transitions, so resolving each time against the
        // zone must equal the plain wall-clock difference everywhere. This is
        // the test that says the fix changes nothing for most users.
        var checked = 0
        var date = LocalDate.of(2026, 1, 1)
        while (date.isBefore(LocalDate.of(2027, 1, 1))) {
            for (start in listOf("00:00", "01:30", "12:00", "23:30")) {
                for (end in listOf("00:01", "03:30", "13:00", "23:59")) {
                    val s = minutes(start)
                    val e = minutes(end)
                    val expected = (if (e > s) e - s else e + SessionTimes.MINUTES_PER_DAY - s) * 60_000L
                    assertEquals(
                        "$start -> $end on $date",
                        expected,
                        lengthOf(span(start, end, on = date)),
                    )
                    checked++
                }
            }
            date = date.plusDays(7)
        }
        assertEquals(53 * 16, checked)
    }

    // --- the "did you forget to stop?" boundary ------------------------------

    @Test
    fun `an interval counts as forgotten only once it passes the threshold`() {
        val start = 1_700_000_000_000L
        val eightHours = 8 * 60 * 60_000L

        assertFalse(SessionTimes.isProbablyForgotten(start, start + eightHours - 1, eightHours))
        assertTrue(SessionTimes.isProbablyForgotten(start, start + eightHours, eightHours))
        assertTrue(SessionTimes.isProbablyForgotten(start, start + 14 * 60 * 60_000L, eightHours))
        // A clock that jumped backwards must not make a fresh interval look
        // forgotten; the difference is negative.
        assertFalse(SessionTimes.isProbablyForgotten(start, start - 60_000L, eightHours))
    }
}
