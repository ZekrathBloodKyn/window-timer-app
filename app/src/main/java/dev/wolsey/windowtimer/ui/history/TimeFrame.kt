package dev.wolsey.windowtimer.ui.history

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class TimeFramePreset(val label: String) {
    ALL("All time"),
    TODAY("Today"),
    WEEK("This week"),
    MONTH("This month"),
    LAST_30("Last 30 days"),
    YEAR("This year"),
    CUSTOM("Custom range")
}

/** From [startMillis] up to, but not including, [endMillis]. */
data class TimeRange(val startMillis: Long, val endMillis: Long) {
    operator fun contains(time: Long): Boolean = time >= startMillis && time < endMillis
}

/**
 * The range for a preset, or null for all time. A custom range covers both chosen days
 * in full and may be given in either order.
 */
fun timeRangeFor(
    preset: TimeFramePreset,
    customFromMillis: Long,
    customToMillis: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
): TimeRange? {
    fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today = day(now)

    val (start, end) = when (preset) {
        TimeFramePreset.ALL -> return null
        TimeFramePreset.TODAY -> today to today.plusDays(1)
        TimeFramePreset.WEEK -> {
            val first = today.with(TemporalAdjusters.previousOrSame(firstDay))
            first to first.plusDays(7)
        }
        TimeFramePreset.MONTH -> {
            val first = today.withDayOfMonth(1)
            first to first.plusMonths(1)
        }
        TimeFramePreset.LAST_30 -> today.minusDays(29) to today.plusDays(1)
        TimeFramePreset.YEAR -> {
            val first = today.withDayOfYear(1)
            first to first.plusYears(1)
        }
        TimeFramePreset.CUSTOM -> {
            val a = day(customFromMillis)
            val b = day(customToMillis)
            val first = if (a.isAfter(b)) b else a
            val last = if (a.isAfter(b)) a else b
            first to last.plusDays(1)
        }
    }
    return TimeRange(
        start.atStartOfDay(zone).toInstant().toEpochMilli(),
        end.atStartOfDay(zone).toInstant().toEpochMilli()
    )
}