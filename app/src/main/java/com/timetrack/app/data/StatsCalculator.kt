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
            val millis = overlapMillis(s, windowStart, windowEnd, now)
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

    /**
     * How many intervals contributed anything to the window.
     *
     * The average session length needs this, and counting *tasks* instead would
     * be wrong: one task can be tracked a dozen times in a day, and dividing its
     * total by one would report a twelve-hour "average session".
     */
    fun contributingIntervals(
        sessions: List<SessionWithTask>,
        windowStart: Long,
        windowEnd: Long,
        now: Long,
    ): Int = sessions.count { overlapMillis(it, windowStart, windowEnd, now) > 0L }

    /**
     * Lays two periods' per-task totals side by side.
     *
     * A task appearing in only one of the periods still gets a row, with zero on
     * the other side. Dropping it would hide precisely what the comparison is
     * for: something that appeared, or something that stopped happening.
     *
     * Sorted by the current period descending, with the previous period as the
     * tie-break. The tie-break is what keeps a task that fell to zero near the
     * top instead of sinking to the bottom next to the trivial rows.
     */
    fun compare(
        current: List<TaskSlice>,
        previous: List<TaskSlice>,
    ): List<ComparisonRow> {
        val currentById = current.associateBy { it.taskId }
        val previousById = previous.associateBy { it.taskId }
        val ids = LinkedHashSet<Long>().apply {
            addAll(currentById.keys)
            addAll(previousById.keys)
        }

        return ids.mapNotNull { id ->
            val now = currentById[id]
            val before = previousById[id]
            val source = now ?: before ?: return@mapNotNull null
            ComparisonRow(
                taskId = id,
                name = source.name,
                colorArgb = source.colorArgb,
                currentMillis = now?.millis ?: 0L,
                previousMillis = before?.millis ?: 0L,
            )
        }.sortedWith(
            compareByDescending<ComparisonRow> { it.currentMillis }
                .thenByDescending { it.previousMillis },
        )
    }

    /**
     * The part of [session] that falls inside the window, or zero if it does not
     * reach it.
     *
     * Guards against a clock that moved backwards, so the answer is never
     * negative and a corrupt row cannot subtract from the total.
     */
    private fun overlapMillis(
        session: SessionWithTask,
        windowStart: Long,
        windowEnd: Long,
        now: Long,
    ): Long {
        val end = (session.endTime ?: now).coerceAtLeast(session.startTime)
        val from = maxOf(session.startTime, windowStart)
        val to = minOf(end, windowEnd)
        return (to - from).coerceAtLeast(0L)
    }
}
