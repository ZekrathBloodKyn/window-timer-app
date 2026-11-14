package dev.wolsey.windowtimer.ui.history

import dev.wolsey.windowtimer.data.db.Run
import dev.wolsey.windowtimer.data.db.Visit

/** One item in the History list: a run (with its visit, if any) or a visit that had no run. */
sealed interface HistoryEntry {
    val jobName: String
    val sortTime: Long

    data class RunEntry(val run: Run, val visit: Visit?) : HistoryEntry {
        override val jobName: String get() = run.jobName
        override val sortTime: Long get() = run.startedAt
    }

    data class VisitEntry(val visit: Visit) : HistoryEntry {
        override val jobName: String get() = visit.jobName
        override val sortTime: Long get() = visit.arrivedAt
    }
}

/** Combines runs and visits into one list, newest first. */
fun buildEntries(runs: List<Run>, visits: List<Visit>): List<HistoryEntry> {
    val visitsById = visits.associateBy { it.id }
    val attachedIds = runs.mapNotNull { it.visitId }.toSet()

    val runEntries = runs.map { HistoryEntry.RunEntry(it, it.visitId?.let { id -> visitsById[id] }) }
    val visitOnly = visits
        .filter { it.id !in attachedIds }
        .map { HistoryEntry.VisitEntry(it) }

    return (runEntries + visitOnly).sortedByDescending { it.sortTime }
}

/** Distinct job names across all entries, sorted, ignoring case and spacing. */
fun entryJobNames(entries: List<HistoryEntry>): List<String> =
    entries.map { it.jobName.trim() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

/** True when the entry belongs to the chosen job; a null filter means all jobs. */
fun HistoryEntry.matches(filter: String?): Boolean =
    filter == null || jobName.trim().equals(filter, ignoreCase = true)