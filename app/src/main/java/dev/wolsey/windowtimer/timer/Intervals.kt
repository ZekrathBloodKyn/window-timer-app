package dev.wolsey.windowtimer.timer

import android.content.Context
import androidx.core.content.edit
import kotlin.math.roundToInt

/** How the timer's alerts are spaced. The defaults are four alerts over the whole estimate. */
data class IntervalPlan(
    val count: Int = 4,                 // intervals over the whole estimate
    val separate: Boolean = false,      // give interior and exterior their own intervals
    val interiorCount: Int = 4,
    val exteriorCount: Int = 4,
    val interiorFirst: Boolean = true,
    val stopwatchAlerts: Boolean = false,   // repeating alert in stopwatch mode
    val stopwatchMinutes: Int = 15
) {
    companion object {
        const val MIN = 1
        const val MAX = 20
        const val MINUTES_MIN = 1
        const val MINUTES_MAX = 120
    }
}

object IntervalSettings {
    private const val PREFS = "intervals"

    fun load(context: Context): IntervalPlan {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = IntervalPlan()
        fun count(key: String, default: Int) =
            p.getInt(key, default).coerceIn(IntervalPlan.MIN, IntervalPlan.MAX)
        return IntervalPlan(
            count = count("count", d.count),
            separate = p.getBoolean("separate", d.separate),
            interiorCount = count("interior", d.interiorCount),
            exteriorCount = count("exterior", d.exteriorCount),
            interiorFirst = p.getBoolean("interior_first", d.interiorFirst),
            stopwatchAlerts = p.getBoolean("stopwatch_alerts", d.stopwatchAlerts),
            stopwatchMinutes = p.getInt("stopwatch_minutes", d.stopwatchMinutes)
                .coerceIn(IntervalPlan.MINUTES_MIN, IntervalPlan.MINUTES_MAX)
        )
    }

    fun save(context: Context, plan: IntervalPlan) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt("count", plan.count)
            putBoolean("separate", plan.separate)
            putInt("interior", plan.interiorCount)
            putInt("exterior", plan.exteriorCount)
            putBoolean("interior_first", plan.interiorFirst)
            putBoolean("stopwatch_alerts", plan.stopwatchAlerts)
            putInt("stopwatch_minutes", plan.stopwatchMinutes)
        }
    }
}

enum class IntervalGroup { ALL, INTERIOR, EXTERIOR }

/**
 * One alert on the timeline.
 * @property timeMs when it fires, counted from the start
 * @property label what the screen shows, e.g. "25%" or "Interior 50%"
 * @property sound which of the four sound settings plays: 0 early, 1 middle, 2 late, 3 final
 */
data class Mark(val timeMs: Long, val label: String, val sound: Int, val group: IntervalGroup)

/** Sound stage for the end of the first part when interior and exterior run separately. */
const val PART_COMPLETE_SOUND = 4

/**
 * The alerts for a run, in order. [interiorMs] is the part of [totalMs] that's interior work.
 * With separate groups on and both kinds of window present, the first group's time runs out
 * before the second begins; with only one kind present that kind's own count is used.
 */
fun buildMarks(totalMs: Long, interiorMs: Long, plan: IntervalPlan): List<Mark> {
    if (totalMs <= 0) return emptyList()
    val interior = interiorMs.coerceIn(0L, totalMs)
    val exterior = totalMs - interior

    if (!plan.separate || (interior == 0L && exterior == 0L)) {
        return segment(0L, totalMs, plan.count, IntervalGroup.ALL, "")
    }

    class Part(val group: IntervalGroup, val ms: Long, val count: Int, val prefix: String)

    val parts = buildList {
        if (interior > 0) add(Part(IntervalGroup.INTERIOR, interior, plan.interiorCount, "Interior "))
        if (exterior > 0) add(Part(IntervalGroup.EXTERIOR, exterior, plan.exteriorCount, "Exterior "))
    }.let { if (plan.interiorFirst) it else it.reversed() }

    val bothKinds = parts.size > 1
    var start = 0L
    return parts.withIndex().flatMap { (index, part) ->
        val marks = segment(start, part.ms, part.count, part.group, if (bothKinds) part.prefix else "")
        start += part.ms
        // The end of the first part gets its own sound; only the end of the job is "final".
        if (bothKinds && index < parts.lastIndex) {
            marks.dropLast(1) + marks.last().copy(sound = PART_COMPLETE_SOUND)
        } else {
            marks
        }
    }
}

/** [n] evenly spaced alerts across [length], beginning at [start]. */
private fun segment(start: Long, length: Long, n: Int, group: IntervalGroup, prefix: String): List<Mark> =
    (1..n).map { k ->
        Mark(
            timeMs = start + markMillis(length, k.toDouble() / n),
            label = prefix + "${(k * 100.0 / n).roundToInt()}%",
            sound = soundStage(k, n),
            group = group
        )
    }

/** The last alert of a group is the final sound; the rest spread over early, middle and late. */
fun soundStage(k: Int, n: Int): Int =
    if (k >= n) 3 else (((k - 1) * 3) / (n - 1)).coerceIn(0, 2)

/** The next repeating alert after [elapsedMs]: the next multiple of [everyMs]. */
fun nextStopwatchAlert(elapsedMs: Long, everyMs: Long): Long =
    (elapsedMs / everyMs + 1) * everyMs