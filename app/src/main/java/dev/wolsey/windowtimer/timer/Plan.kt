package dev.wolsey.windowtimer.timer

import dev.wolsey.windowtimer.data.TypeCount
import dev.wolsey.windowtimer.data.db.Person
import dev.wolsey.windowtimer.data.db.WindowType
import dev.wolsey.windowtimer.data.paceFor
import kotlin.math.roundToLong

/** A quarter mark's time in milliseconds, rounded to a whole second. */
fun markMillis(total: Long, fraction: Double): Long =
    (total * fraction / 1000.0).roundToLong() * 1000L

/** Formats milliseconds as m:ss, or h:mm:ss once past an hour. */
fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Like formatClock, but rounds up so a countdown reads 0:00 exactly when time is up. */
fun formatCountdown(ms: Long): String =
    formatClock(((ms.coerceAtLeast(0) + 999) / 1000) * 1000)

fun totalMillis(counts: List<TypeCount>, types: List<WindowType>): Long {
    val standard = types.firstOrNull { it.isStandard }
    val hours = counts.sumOf { c ->
        val type = types.firstOrNull { it.id == c.typeId } ?: standard
        if (type == null) 0.0
        else c.interior.toDouble() / type.interiorPace + c.exterior.toDouble() / type.exteriorPace
    }
    return (hours * 3600.0).roundToLong() * 1000L
}

/**
 * Estimated time for a crew. A person's pace is their pace on Standard windows; another
 * type is scaled by its pace relative to Standard. The crew's pace for a type is the sum
 * of its people's scaled paces. With no crew, falls back to the plain type paces.
 */
fun totalMillis(counts: List<TypeCount>, types: List<WindowType>, crew: List<Person>): Long {
    val standard = types.firstOrNull { it.isStandard }
    if (crew.isEmpty() || standard == null) return totalMillis(counts, types)

    fun crewPace(type: WindowType, interior: Boolean): Double =
        crew.sumOf { it.paceFor(type, interior, standard) }

    val hours = counts.sumOf { c ->
        val type = types.firstOrNull { it.id == c.typeId } ?: standard
        val interiorHours = if (c.interior > 0) c.interior / crewPace(type, true) else 0.0
        val exteriorHours = if (c.exterior > 0) c.exterior / crewPace(type, false) else 0.0
        interiorHours + exteriorHours
    }
    return (hours * 3600.0).roundToLong() * 1000L
}

/**
 * The part of the estimate that's interior work. The rest, `totalMillis(...) - this`, is
 * exterior. Uses the same paces as the crew estimate.
 */
fun interiorMillis(counts: List<TypeCount>, types: List<WindowType>, crew: List<Person>): Long {
    val standard = types.firstOrNull { it.isStandard }

    fun pace(type: WindowType, interior: Boolean): Double =
        if (crew.isEmpty() || standard == null) {
            (if (interior) type.interiorPace else type.exteriorPace).toDouble()
        } else {
            crew.sumOf { it.paceFor(type, interior, standard) }
        }

    var hours = 0.0
    for (c in counts) {
        val type = types.firstOrNull { it.id == c.typeId } ?: standard ?: continue
        if (c.interior > 0) hours += c.interior / pace(type, true)
    }
    val total = totalMillis(counts, types, crew)
    return ((hours * 3600.0).roundToLong() * 1000L).coerceIn(0L, total)
}