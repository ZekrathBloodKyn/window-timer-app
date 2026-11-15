package dev.wolsey.windowtimer.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.Crew
import dev.wolsey.windowtimer.data.CrewMember
import dev.wolsey.windowtimer.data.TypeCount
import dev.wolsey.windowtimer.data.db.Person
import dev.wolsey.windowtimer.timer.Availability

/**
 * The Finish flow: a Yes / No / Cancel dialog asking whether every window was done.
 * Yes records the planned counts; No asks how many each person actually did, by type.
 * With more than one person the planned counts are first split by pace.
 *
 * onConfirm receives the total done by type and each crew member's own counts.
 * onDismiss means the person backed out, so the timer keeps running.
 */
@Composable
fun FinishFlow(
    planned: List<TypeCount>,
    crew: List<Person>,
    onConfirm: (done: List<TypeCount>, members: List<CrewMember>) -> Unit,
    onDismiss: () -> Unit,
    stopwatch: Boolean = false,
    availability: Map<Long, Availability> = emptyMap(),
    elapsedMs: Long = 0L,
) {
    var askCounts by remember { mutableStateOf(stopwatch) }

    // Normally the crew is never empty; this is just a safety net.
    val who = crew.ifEmpty { listOf(Person("Me", 24, 22, true, 0)) }
    // How long each person actually worked, so the suggested split follows it.
    val present = who.associate { p ->
        val a = availability[p.id] ?: Availability()
        val end = (a.endMs ?: elapsedMs).coerceAtMost(elapsedMs)
        p.id to (end - a.startMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    }
    val split = remember(planned, who, present) {
        Crew.split(planned, who, if (availability.isEmpty()) emptyMap() else present)
    }

    fun finish(perPerson: List<List<TypeCount>>) {
        val members = who.mapIndexed { p, person ->
            val a = availability[person.id]
            CrewMember(
                person.id, person.name, person.interiorPace, person.exteriorPace,
                perPerson[p].filter { it.windows > 0 },
                startMs = (a?.startMs ?: 0L).coerceIn(0L, elapsedMs),
                endMs = a?.endMs?.takeIf { it < elapsedMs }
            )
        }
        val done = planned.indices.map { t ->
            TypeCount(
                planned[t].typeId,
                planned[t].name,
                perPerson.sumOf { it[t].interior },
                perPerson.sumOf { it[t].exterior }
            )
        }.filter { it.windows > 0 }
        onConfirm(done, if (crew.isEmpty()) emptyList() else members)
    }

    if (!askCounts) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Finish job") },
            text = { Text("Were all the windows done?") },
            confirmButton = {
                Row {
                    @Suppress("AssignedValueIsNeverRead")
                    TextButton(onClick = { askCounts = true }) { Text("No") }
                    TextButton(onClick = { finish(split) }) { Text("Yes") }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    } else {
        // One pair of boxes per person per type, pre-filled with the planned split.
        // Index = ((person * types) + type) * 2, plus 1 for exterior.
        val texts = remember {
            split.flatMap { personCounts ->
                personCounts.flatMap { listOf(it.interior.toString(), it.exterior.toString()) }
            }.toMutableStateList()
        }

        fun fromTexts(): List<List<TypeCount>> = who.indices.map { p ->
            planned.indices.map { t ->
                val base = (p * planned.size + t) * 2
                planned[t].copy(
                    interior = texts[base].toIntOrNull() ?: 0,
                    exterior = texts[base + 1].toIntOrNull() ?: 0
                )
            }
        }

        val entered = fromTexts()
        val doneInterior = entered.sumOf { person -> person.sumOf { it.interior } }
        val doneExterior = entered.sumOf { person -> person.sumOf { it.exterior } }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Windows completed") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    who.forEachIndexed { p, person ->
                        if (who.size > 1) {
                            Text(person.name, style = MaterialTheme.typography.titleSmall)
                        }
                        planned.forEachIndexed { t, count ->
                            val base = (p * planned.size + t) * 2
                            OutlinedTextField(
                                value = texts[base],
                                onValueChange = {
                                    texts[base] = it.filter(Char::isDigit).take(3)
                                },
                                label = {
                                    Text(
                                        if (stopwatch) "${count.name} interior"
                                        else "${count.name} interior (planned ${split[p][t].interior})"
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = texts[base + 1],
                                onValueChange = {
                                    texts[base + 1] = it.filter(Char::isDigit).take(3)
                                },
                                label = {
                                    Text(
                                        if (stopwatch) "${count.name} exterior"
                                        else "${count.name} exterior (planned ${split[p][t].exterior})"
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (who.size > 1) {
                        Text(
                            if (stopwatch) "Done: $doneInterior interior · $doneExterior exterior"
                            else "Done: $doneInterior of ${planned.sumOf { it.interior }} interior · " +
                                    "$doneExterior of ${planned.sumOf { it.exterior }} exterior",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { finish(fromTexts()) }) { Text("Save run") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (!stopwatch) {
                        @Suppress("AssignedValueIsNeverRead")
                        TextButton(onClick = { askCounts = false }) { Text("Back") }
                    }
                }
            }
        )
    }
}