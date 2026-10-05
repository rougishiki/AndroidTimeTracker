package com.timetrack.app.data

/**
 * Pure interval aggregation, deliberately free of Room and Android types so it can
 * be exercised by plain JVM unit tests.
 */
object StatsCalculator {

    /**
     * Totals each task's overlap with the window `[windowStart, windowEnd)`.
     *
     * Overlap, not containment, is the whole point: a session running
     * `23:30 -> 00:30` must contribute 30 minutes to each of the two days rather
     * than 60 minutes to whichever day it started on.
     *
     * An open interval (`endTime == null`) is measured up to [now].
     *
     * @return per-task totals, largest first.
     */
    fun aggregate(
        sessions: List<SessionWithTask>,
        windowStart: Long,
        windowEnd: Long,
        now: Long,
    ): List<TaskSlice> {
        val buckets = LinkedHashMap<Long, TaskSlice>()
        for (s in sessions) {
            // Guard against a clock that moved backwards: never produce a negative span.
            val end = (s.endTime ?: now).coerceAtLeast(s.startTime)
            val from = maxOf(s.startTime, windowStart)
            val to = minOf(end, windowEnd)
            val millis = to - from
            if (millis <= 0L) continue

            val previous = buckets[s.taskId]
            buckets[s.taskId] = TaskSlice(
                taskId = s.taskId,
                name = s.taskName,
                colorArgb = s.taskColorArgb,
                millis = (previous?.millis ?: 0L) + millis,
            )
        }
        return buckets.values.sortedByDescending { it.millis }
    }
}
