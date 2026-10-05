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
}
