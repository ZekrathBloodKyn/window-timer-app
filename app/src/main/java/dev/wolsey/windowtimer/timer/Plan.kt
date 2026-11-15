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

/** A crew's work on a job, worked out once for the total and the interior/exterior split. */
private class CrewWork(
    val interiorHours: Double,   // hours of interior work if the whole crew works it
    val exteriorHours: Double,
    val share: Map<Long, Double> // each person's share of the crew's output; adds up to 1
)

private fun crewWork(
    counts: List<TypeCount>,
    types: List<WindowType>,
    crew: List<Person>,
    standard: WindowType
): CrewWork {
    var interiorHours = 0.0
    var exteriorHours = 0.0
    val weighted = HashMap<Long, Double>()

    for (c in counts) {
        val type = types.firstOrNull { it.id == c.typeId } ?: standard
        for (interior in listOf(true, false)) {
            val n = if (interior) c.interior else c.exterior
            if (n <= 0) continue
            val crewRate = crew.sumOf { it.paceFor(type, interior, standard) }
            val hours = n / crewRate
            if (interior) interiorHours += hours else exteriorHours += hours
            crew.forEach { p ->
                weighted[p.id] = (weighted[p.id] ?: 0.0) +
                        hours * p.paceFor(type, interior, standard) / crewRate
            }
        }
    }
    val all = interiorHours + exteriorHours
    val share = if (all > 0) weighted.mapValues { it.value / all }
    else crew.associate { it.id to 1.0 / crew.size }
    return CrewWork(interiorHours, exteriorHours, share)
}

private fun isFullTime(availability: Map<Long, Availability>) =
    availability.values.all { it.startMs <= 0L && it.endMs == null }

/**
 * Milliseconds until [hours] of full-crew work is done, when each person is only working
 * between their start and end. Someone who isn't there adds nothing, so their share of the
 * work waits for them or is picked up more slowly by whoever is left.
 */
private fun timeToFinish(
    hours: Double,
    share: Map<Long, Double>,
    availability: Map<Long, Availability>
): Double {
    var remaining = hours * 3_600_000.0
    if (remaining <= 0) return 0.0

    val points = (listOf(0L) + availability.values.flatMap {
        listOfNotNull(it.startMs.takeIf { start -> start > 0 }, it.endMs)
    }).distinct().sorted()

    for (i in points.indices) {
        val from = points[i]
        val capacity = share.entries.sumOf { (id, s) ->
            val a = availability[id] ?: Availability()
            if (from >= a.startMs && (a.endMs == null || from < a.endMs)) s else 0.0
        }
        val next = points.getOrNull(i + 1)
        if (next == null) {
            // Nobody left to work would never finish, so fall back to the full-crew time.
            return if (capacity > 0) from + remaining / capacity else hours * 3_600_000.0
        }
        val length = (next - from).toDouble()
        if (capacity * length >= remaining) return from + remaining / capacity
        remaining -= capacity * length
    }
    return hours * 3_600_000.0
}

/**
 * Estimated time for a crew. A person's pace is their pace on Standard windows; another
 * type is scaled by its pace relative to Standard. The crew's pace for a type is the sum
 * of its people's scaled paces. With no crew, falls back to the plain type paces.
 *
 * [availability] says when each person is working, counted from the timer's start. Anyone
 * not listed works the whole job.
 */
fun totalMillis(
    counts: List<TypeCount>,
    types: List<WindowType>,
    crew: List<Person>,
    availability: Map<Long, Availability> = emptyMap()
): Long {
    val standard = types.firstOrNull { it.isStandard }
    if (crew.isEmpty() || standard == null) return totalMillis(counts, types)

    val work = crewWork(counts, types, crew, standard)
    val hours = work.interiorHours + work.exteriorHours
    if (isFullTime(availability)) return (hours * 3600.0).roundToLong() * 1000L
    return (timeToFinish(hours, work.share, availability) / 1000.0).roundToLong() * 1000L
}

/**
 * The part of the estimate that's interior work, with interior done first. The rest,
 * `totalMillis(...) - this`, is exterior.
 */
fun interiorMillis(
    counts: List<TypeCount>,
    types: List<WindowType>,
    crew: List<Person>,
    availability: Map<Long, Availability> = emptyMap()
): Long {
    val standard = types.firstOrNull { it.isStandard }
    val total = totalMillis(counts, types, crew, availability)

    if (crew.isEmpty() || standard == null) {
        var hours = 0.0
        for (c in counts) {
            val type = types.firstOrNull { it.id == c.typeId } ?: standard ?: continue
            if (c.interior > 0) hours += c.interior.toDouble() / type.interiorPace
        }
        return ((hours * 3600.0).roundToLong() * 1000L).coerceIn(0L, total)
    }

    val work = crewWork(counts, types, crew, standard)
    val ms = if (isFullTime(availability)) work.interiorHours * 3_600_000.0
    else timeToFinish(work.interiorHours, work.share, availability)
    return ((ms / 1000.0).roundToLong() * 1000L).coerceIn(0L, total)
}