package dev.wolsey.windowtimer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.Breakdown
import dev.wolsey.windowtimer.data.Crew
import dev.wolsey.windowtimer.data.CrewMember
import dev.wolsey.windowtimer.data.TypeCount
import dev.wolsey.windowtimer.data.db.Person
import dev.wolsey.windowtimer.data.db.Run
import dev.wolsey.windowtimer.data.db.WindowType
import dev.wolsey.windowtimer.data.store.PersonStore
import dev.wolsey.windowtimer.ui.timer.CountRow
import dev.wolsey.windowtimer.ui.timer.CrewChips
import dev.wolsey.windowtimer.ui.timer.SavedCrewChips
import dev.wolsey.windowtimer.ui.timer.TypeCountRows
import dev.wolsey.windowtimer.ui.timer.toCounts
import dev.wolsey.windowtimer.ui.timer.toRows
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the Add run and Edit run dialogs are editing: who worked, how many windows of each
 * type each person did, and when anyone arrived late or left early.
 */
@Stable
class CrewEditState {
    var loaded by mutableStateOf(false)
    var choices by mutableStateOf<List<Person>>(emptyList())   // who can be picked
    var selected by mutableStateOf<List<Long>>(emptyList())    // who worked
    val rows = mutableStateMapOf<Long, List<CountRow>>()       // each person's counts
    val starts = mutableStateMapOf<Long, Long>()               // arrival, ms after the run's start
    val ends = mutableStateMapOf<Long, Long>()                 // departure, ms after the run's start

    fun crewPeople(): List<Person> = selected.mapNotNull { id -> choices.firstOrNull { it.id == id } }

    /** Each selected person with the windows they did and when they worked. */
    fun members(): List<CrewMember> = crewPeople().map { p ->
        CrewMember(
            p.id, p.name, p.interiorPace, p.exteriorPace,
            rows[p.id].orEmpty().toCounts().filter { it.windows > 0 },
            startMs = starts[p.id] ?: 0L,
            endMs = ends[p.id]
        )
    }

    fun setSelected(next: List<Long>, standard: WindowType?) {
        val default = listOfNotNull(standard?.let { CountRow(it.id, it.name) })
        next.filter { it !in selected }.forEach { rows[it] = default }
        selected.filter { it !in next }.forEach {
            rows.remove(it)
            starts.remove(it)
            ends.remove(it)
        }
        selected = next
    }

    /** Hands out a job's planned counts between the selected people, by pace. */
    fun applyPlanned(planned: List<TypeCount>, standard: WindowType?) {
        val default = listOfNotNull(standard?.let { CountRow(it.id, it.name) })
        val crew = crewPeople()
        val split = Crew.split(planned, crew)
        crew.forEachIndexed { i, p -> rows[p.id] = split[i].toRows().ifEmpty { default } }
    }

    /**
     * [run] is null when adding. For an existing run the crew is read from its saved record;
     * an older run with no record is treated as Me, who did everything.
     */
    fun load(run: Run?, people: List<Person>, standard: WindowType, lastCrew: List<Long>) {
        val snapshot = run?.let { Crew.decode(it.crew) }.orEmpty()
        val snapshotById = snapshot.associateBy { it.personId }

        // People already in the run keep the name and pace saved with it.
        val current = people.map { p ->
            snapshotById[p.id]?.let {
                p.copy(name = it.name, interiorPace = it.interiorPace, exteriorPace = it.exteriorPace)
            } ?: p
        }
        // People who were in the run but have since been deleted.
        val gone = snapshot.filter { s -> people.none { it.id == s.personId } }
            .map { Person(it.name, it.interiorPace, it.exteriorPace, false, it.personId) }
        choices = current + gone

        val default = listOf(CountRow(standard.id, standard.name))
        val owner = people.firstOrNull { it.isOwner } ?: people.first()
        when {
            run == null -> {
                selected = lastCrew.filter { id -> people.any { it.id == id } }
                    .ifEmpty { listOf(owner.id) }
                selected.forEach { rows[it] = default }
            }
            snapshot.isNotEmpty() -> {
                selected = snapshot.map { it.personId }
                snapshot.forEach {
                    rows[it.personId] = it.counts.toRows().ifEmpty { default }
                    if (it.startMs > 0) starts[it.personId] = it.startMs
                    it.endMs?.let { end -> ends[it.personId] = end }
                }
            }
            else -> {
                selected = listOf(owner.id)
                rows[owner.id] = Breakdown.resolve(run.breakdown, run.interior, run.exterior, standard)
                    .toRows().ifEmpty { default }
            }
        }
        loaded = true
    }
}

/** Fills the state in once the people and the Standard type have loaded. */
@Composable
fun rememberCrewEditState(run: Run?, people: List<Person>, standard: WindowType?): CrewEditState {
    val context = LocalContext.current
    val state = remember { CrewEditState() }
    LaunchedEffect(people, standard?.id) {
        if (!state.loaded && people.isNotEmpty() && standard != null) {
            state.load(run, people, standard, PersonStore.lastCrewIds(context))
        }
    }
    return state
}

/**
 * Crew chips (when there's more than one person to pick), each person's counts by type, and
 * with more than one person on the run, when each arrived and left. [runStartMs] is when the
 * run started, which the times are measured from.
 */
@Composable
fun CrewEditor(
    state: CrewEditState,
    types: List<WindowType>,
    standard: WindowType?,
    runStartMs: Long
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.choices.size > 1) {
            CrewChips(
                people = state.choices,
                selectedIds = state.selected,
                onChange = { state.setSelected(it, standard) },
                enabled = true
            )
            SavedCrewChips(
                state.choices, state.selected, { state.setSelected(it, standard) }, enabled = true
            )
        }
        state.crewPeople().forEach { person ->
            if (state.selected.size > 1) {
                Text(person.name, style = MaterialTheme.typography.titleSmall)
            }
            TypeCountRows(
                rows = state.rows[person.id].orEmpty(),
                types = types,
                onRowsChange = { state.rows[person.id] = it },
                enabled = true
            )
            if (state.selected.size > 1 && !person.isOwner) {
                PresenceTimeRow(
                    label = "Arrived",
                    notSet = "from the start",
                    offsetMs = state.starts[person.id],
                    runStartMs = runStartMs,
                    onChange = { offset ->
                        if (offset == null || offset <= 0L) state.starts.remove(person.id)
                        else state.starts[person.id] = offset
                    }
                )
                PresenceTimeRow(
                    label = "Left",
                    notSet = "stayed to the end",
                    offsetMs = state.ends[person.id],
                    runStartMs = runStartMs,
                    onChange = { offset ->
                        if (offset == null) state.ends.remove(person.id)
                        else state.ends[person.id] = offset.coerceAtLeast(60_000L)
                    }
                )
            }
        }
    }
}

/** A clock time for one person, picked with a time picker. Null means not set. */
@Composable
private fun PresenceTimeRow(
    label: String,
    notSet: String,
    offsetMs: Long?,
    runStartMs: Long,
    onChange: (Long?) -> Unit
) {
    val format = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var picking by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (offsetMs == null) "$label: $notSet"
            else "$label: ${format.format(Date(runStartMs + offsetMs))}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { picking = true }) { Text("Set") }
        if (offsetMs != null) {
            TextButton(onClick = { onChange(null) }) { Text("Clear") }
        }
    }

    @Suppress("AssignedValueIsNeverRead")
    if (picking) {
        TimePickerDialog(
            initial = runStartMs + (offsetMs ?: 0L),
            onConfirm = { hour, minute ->
                picking = false
                onChange(withTimeOfDay(runStartMs, hour, minute) - runStartMs)
            },
            onDismiss = { picking = false }
        )
    }
}