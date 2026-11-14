package dev.wolsey.windowtimer.data

import dev.wolsey.windowtimer.data.db.Run

/** Averages over a set of runs. */
data class RunSummary(
    val count: Int,
    val fullRunCount: Int,     // runs where every planned window was done
    val avgPlannedMs: Long,    // the three averages below use full runs only
    val avgActualMs: Long,
    val avgDiffMs: Long,       // positive = ran over the estimate
    val overallPace: Double?,  // by windows actually done, over the run's clock time
    val perPersonPace: Double? = null  // everyone view only: windows per person-hour
)

/** How many windows one person did in one run. */
data class Share(val personId: Long, val windows: Int)

/**
 * Who worked this run. Runs recorded before crews existed count as [ownerId]
 * (the "Me" person) having done everything.
 */
fun Run.shares(ownerId: Long): List<Share> {
    val members = Crew.decode(crew)
    return if (members.isEmpty()) listOf(Share(ownerId, windows))
    else members.map { Share(it.personId, it.windows) }
}

/**
 * Summarizes the runs, or returns null when there are none.
 *
 * With [personId] null this is the whole crew: pace is everyone's windows over the run's
 * clock time. With a person, only runs they worked count, and pace is their own windows
 * over the run's clock time (everyone shares one clock).
 */
fun summarize(runs: List<Run>, personId: Long? = null, ownerId: Long = 0L): RunSummary? {
    val chosen = if (personId == null) runs
    else runs.filter { run -> run.shares(ownerId).any { it.personId == personId } }
    if (chosen.isEmpty()) return null

    fun windowsIn(run: Run): Int =
        if (personId == null) run.windows
        else run.shares(ownerId).firstOrNull { it.personId == personId }?.windows ?: 0

    val full = chosen.filterNot { it.isPartial }
    val nFull = full.size
    val fullPlanned = full.sumOf { it.plannedMs }
    val fullActual = full.sumOf { it.actualMs }

    val totalActual = chosen.sumOf { it.actualMs }
    val totalWindows = chosen.sumOf { windowsIn(it) }
    val personMs = chosen.sumOf { it.actualMs * it.shares(ownerId).size }

    return RunSummary(
        count = chosen.size,
        fullRunCount = nFull,
        avgPlannedMs = if (nFull > 0) fullPlanned / nFull else 0L,
        avgActualMs = if (nFull > 0) fullActual / nFull else 0L,
        avgDiffMs = if (nFull > 0) (fullActual - fullPlanned) / nFull else 0L,
        overallPace = if (totalActual > 0 && totalWindows > 0)
            totalWindows * 3_600_000.0 / totalActual
        else null,
        perPersonPace = if (personId == null && personMs > 0 && totalWindows > 0)
            totalWindows * 3_600_000.0 / personMs
        else null
    )
}