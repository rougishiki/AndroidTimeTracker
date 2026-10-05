package com.timetrack.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** All user-facing time formatting lives here so screens stay declarative. */
object Fmt {

    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val DATE_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Elapsed time for the big running display: `12:34`, or `1:02:03` past an hour. */
    fun clock(millis: Long): String {
        val safe = if (millis < 0) 0 else millis
        val total = safe / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** Human duration for lists and totals, e.g. `1小时23分` / `5分20秒` / `42秒`. */
    fun duration(millis: Long): String {
        val safe = if (millis < 0) 0 else millis
        val total = safe / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "${h}小时${m}分"
            m > 0 -> "${m}分${s}秒"
            else -> "${s}秒"
        }
    }

    /**
     * Pinned to Locale.US on purpose: with a comma decimal separator (de/fr locales)
     * this value would break the column layout of the exported CSV.
     */
    fun hours(millis: Long): String =
        String.format(java.util.Locale.US, "%.2f", millis / 3_600_000.0)

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_ONLY.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun timeOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        TIME_ONLY.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Compact "last used" label: `今天 09:30` / `昨天 21:05` / `5月4日`. */
    fun relativeDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val moment = Instant.ofEpochMilli(millis).atZone(zone)
        val today = LocalDate.now(zone)
        return when (moment.toLocalDate()) {
            today -> "今天 ${TIME_ONLY.format(moment)}"
            today.minusDays(1) -> "昨天 ${TIME_ONLY.format(moment)}"
            else -> "${moment.monthValue}月${moment.dayOfMonth}日"
        }
    }

    private val WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** `2026年5月4日 周一`, with 今天/昨天 shortcuts. */
    fun dayLabel(date: LocalDate, today: LocalDate): String {
        val base = "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${WEEKDAYS[date.dayOfWeek.value - 1]}"
        return when (date) {
            today -> "今天 · $base"
            today.minusDays(1) -> "昨天 · $base"
            else -> base
        }
    }

    private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

    /** `周一`..`周日`, indexed by ISO day number so Monday is always first. */
    fun weekdayShort(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    /** The single characters used as the calendar's column headers, Monday first. */
    val WEEKDAY_HEADS: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    /** `2026年10月`, for a picker header. */
    fun monthTitle(month: YearMonth): String = "${month.year}年${month.monthValue}月"

    /** `10月5日`, for cells and week rows. */
    fun monthDay(date: LocalDate): String = MONTH_DAY.format(date)

    /** `10月5日 – 10月11日`, the label of one ISO week. */
    fun weekRange(monday: LocalDate): String =
        "${MONTH_DAY.format(monday)} – ${MONTH_DAY.format(monday.plusDays(6))}"
}
