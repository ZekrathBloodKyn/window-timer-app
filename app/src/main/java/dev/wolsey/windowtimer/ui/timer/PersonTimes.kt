package dev.wolsey.windowtimer.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.db.Person
import dev.wolsey.windowtimer.timer.Availability
import dev.wolsey.windowtimer.timer.NOT_HERE_MS
import dev.wolsey.windowtimer.ui.history.TimePickerDialog
import dev.wolsey.windowtimer.ui.history.withTimeOfDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Pick { ARRIVE, LEAVE }

/** What the crew chips need: who gets a pencil, and a way to open their dialog. */
class PersonTimes(
    val canEdit: (Person) -> Boolean,
    val open: (Person) -> Unit
)

/**
 * Sets up arrival and departure times for the crew. Call it where the chips are drawn: it
 * shows the dialog when one is open and returns what to hand to [CrewChips].
 */
@Suppress("AssignedValueIsNeverRead")
@Composable
fun rememberPersonTimes(
    crew: List<Person>,
    availability: Map<Long, Availability>,
    onChange: (Map<Long, Availability>) -> Unit,
    running: Boolean,
    elapsedMs: Long,
    unknownIds: Set<Long>,
    onUnknownChange: (personId: Long, unknown: Boolean) -> Unit
): PersonTimes {
    val format = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var editingId by remember { mutableStateOf<Long?>(null) }

    fun of(person: Person) = availability[person.id] ?: Availability()
    val now = System.currentTimeMillis()

    // Clock times for arriving and leaving. While running they're worked out from the timer.
    fun arrivesAt(a: Availability): Long? =
        if (a.notHere) null
        else a.startAt ?: a.startMs.takeIf { it > 0 }?.let { now + it - elapsedMs }
    fun leavesAt(a: Availability): Long? =
        a.endAt ?: a.endMs?.let { now + it - elapsedMs }
    fun clock(ms: Long) = format.format(Date(ms))

    // At least one person has to stay to the end.
    val staying = crew.count { val a = of(it); a.endMs == null && a.endAt == null }

    crew.firstOrNull { it.id == editingId }?.let { person ->
        val a = of(person)
        PersonTimesDialog(
            person = person,
            availability = a,
            arrivesAt = arrivesAt(a),
            leavesAt = leavesAt(a),
            canLeave = (a.endMs != null || a.endAt != null) || staying > 1,
            running = running,
            elapsedMs = elapsedMs,
            clock = ::clock,
            unknown = person.id in unknownIds,
            onUnknownChange = { on ->
                if (!on && (a.joining || a.startMs >= NOT_HERE_MS)) {
                    // No longer "joining later": count them as here from now (or from the start).
                    onChange(
                        availability + (person.id to a.copy(
                            joining = false,
                            startMs = if (running) elapsedMs.coerceAtLeast(1L) else 0L
                        ))
                    )
                }
                onUnknownChange(person.id, on)
            },
            onChange = { onChange(availability + (person.id to it)) },
            onDismiss = { editingId = null }
        )
    }

    return PersonTimes(
        // Only with two or more people, and never for the default person, who works the whole job.
        canEdit = { person -> crew.size > 1 && !person.isOwner && crew.any { it.id == person.id } },
        open = { editingId = it.id }
    )
}

@Composable
private fun PersonTimesDialog(
    person: Person,
    availability: Availability,
    arrivesAt: Long?,
    leavesAt: Long?,
    canLeave: Boolean,
    running: Boolean,
    elapsedMs: Long,
    clock: (Long) -> String,
    unknown: Boolean,
    onUnknownChange: (Boolean) -> Unit,
    onChange: (Availability) -> Unit,
    onDismiss: () -> Unit
) {
    var picking by remember { mutableStateOf<Pick?>(null) }

    val arrivalInFuture = running && (availability.notHere || availability.startMs > elapsedMs)
    val hasLeft = running && availability.endMs != null && availability.endMs <= elapsedMs
    val hasLeaveTime = availability.endMs != null || availability.endAt != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(person.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Times unknown", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Shows Arrived and Left buttons under the crew to tap when it happens.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = unknown, onCheckedChange = onUnknownChange)
                }

                HorizontalDivider()

                if (unknown) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !availability.notHere,
                            onClick = {
                                if (availability.notHere) {
                                    onChange(
                                        availability.copy(
                                            joining = false,
                                            startMs = if (running) elapsedMs.coerceAtLeast(1L) else 0L,
                                            startAt = null
                                        )
                                    )
                                }
                            },
                            label = { Text("On site now") }
                        )
                        FilterChip(
                            selected = availability.notHere,
                            onClick = {
                                onChange(
                                    availability.copy(
                                        joining = true,
                                        startMs = 0L,
                                        startAt = null
                                    ))
                            },
                            label = { Text("Joining later") }
                        )
                    }
                    if (availability.notHere) {
                        Text(
                            "Left out of the estimate until you tap Arrived.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text("Arrives", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            availability.notHere -> "Joining later, hasn't arrived yet"
                            arrivesAt != null -> "From ${clock(arrivesAt)}"
                            else -> "Works from the start"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { picking = Pick.ARRIVE }) { Text("Set time") }
                        if (arrivalInFuture) {
                            TextButton(
                                onClick = {
                                    onChange(
                                        availability.copy(
                                            startMs = elapsedMs.coerceAtLeast(1L),
                                            startAt = null,
                                            joining = false
                                        )
                                    )
                                }
                            ) { Text("Arrived now") }
                        }
                        if (arrivesAt != null) {
                            TextButton(onClick = {
                                onChange(
                                    availability.copy(
                                        startMs = 0L,
                                        startAt = null
                                    )
                                )
                            }) {
                                Text("Clear")
                            }
                        }
                    }

                    HorizontalDivider()

                    Text("Leaves", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            leavesAt == null -> "Stays to the end"
                            hasLeft -> "Left at ${clock(leavesAt)}"
                            else -> "Until ${clock(leavesAt)}"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!canLeave) {
                        Text(
                            "At least one person has to stay to the end.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = canLeave,
                            onClick = { picking = Pick.LEAVE }) { Text("Set time") }
                        if (running && canLeave && !hasLeft) {
                            TextButton(onClick = {
                                onChange(
                                    availability.copy(
                                        endMs = elapsedMs,
                                        endAt = null
                                    )
                                )
                            }) {
                                Text("Left now")
                            }
                        }
                        if (hasLeft) {
                            TextButton(onClick = {
                                onChange(
                                    availability.copy(
                                        endMs = null,
                                        endAt = null
                                    )
                                )
                            }) {
                                Text("Back")
                            }
                        } else if (hasLeaveTime) {
                            TextButton(onClick = {
                                onChange(
                                    availability.copy(
                                        endMs = null,
                                        endAt = null
                                    )
                                )
                            }) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )

    @Suppress("AssignedValueIsNeverRead")
    picking?.let { which ->
        val now = System.currentTimeMillis()
        TimePickerDialog(
            initial = (if (which == Pick.ARRIVE) arrivesAt else leavesAt) ?: now,
            onConfirm = { hour, minute ->
                picking = null
                val chosen = withTimeOfDay(now, hour, minute)
                if (running) {
                    // The timer is already going: turn the clock time into time since the start.
                    val offset = (elapsedMs + (chosen - now)).coerceAtLeast(0L)
                    onChange(
                        if (which == Pick.ARRIVE) availability.copy(
                            startMs = offset,
                            startAt = null,
                            joining = false
                        )
                        else availability.copy(endMs = offset, endAt = null)
                    )
                } else {
                    // Not started yet: keep the clock time until Start.
                    onChange(
                        if (which == Pick.ARRIVE) availability.copy(
                            startAt = chosen,
                            startMs = 0L,
                            joining = false
                        )
                        else availability.copy(endAt = chosen, endMs = null)
                    )
                }
            },
            onDismiss = { picking = null }
        )
    }
}

/**
 * Arrived and Left buttons for the people whose "times unknown" switch is on (set in their
 * pencil dialog). Tap one when it happens; it records the current moment. Someone who is on
 * site already only gets Left.
 */
@Composable
fun LiveTimes(
    crew: List<Person>,
    availability: Map<Long, Availability>,
    onChange: (Map<Long, Availability>) -> Unit,
    running: Boolean,
    elapsedMs: Long,
    unknownIds: Set<Long>
) {
    val people = crew.filter { !it.isOwner && it.id in unknownIds }
    if (crew.size < 2 || people.isEmpty()) return

    val format = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    // The clock time of a moment measured from the timer's start.
    fun clock(offsetMs: Long) = format.format(Date(now + offsetMs - elapsedMs))

    fun of(person: Person) = availability[person.id] ?: Availability()
    val staying = crew.count { val a = of(it); a.endMs == null && a.endAt == null }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        people.forEach { person ->
            val a = of(person)
            val arrived = running && !a.notHere && a.startMs in 1..elapsedMs
            val left = running && a.endMs != null && a.endMs <= elapsedMs
            val canLeave = (a.endMs != null || a.endAt != null) || staying > 1

            Text(person.name, style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (a.notHere || arrived) {
                    OutlinedButton(
                        enabled = running && !arrived,
                        onClick = {
                            onChange(
                                availability + (person.id to a.copy(
                                    startMs = elapsedMs.coerceAtLeast(1L),
                                    startAt = null,
                                    joining = false
                                ))
                            )
                        }
                    ) {
                        Text(if (arrived) "Arrived ${clock(a.startMs)}" else "Arrived")
                    }
                }
                OutlinedButton(
                    enabled = running && !left && canLeave,
                    onClick = {
                        onChange(
                            availability + (person.id to a.copy(
                                endMs = elapsedMs.coerceAtLeast(1L), endAt = null
                            ))
                        )
                    }
                ) {
                    Text(if (left) "Left ${clock(a.endMs)}" else "Left")
                }
            }
            when {
                !running -> Text(
                    "The buttons work once the timer is running.",
                    style = MaterialTheme.typography.bodySmall
                )
                !left && !canLeave -> Text(
                    "At least one person has to stay to the end.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}