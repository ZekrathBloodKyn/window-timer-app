package dev.wolsey.windowtimer.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.db.Person
import dev.wolsey.windowtimer.timer.Availability
import dev.wolsey.windowtimer.timer.formatClock

private fun minutesText(ms: Long) = if (ms <= 0) "" else (ms / 60_000).toString()

/**
 * Who starts late or leaves early. Before the run: minutes after the timer starts.
 * During the run: status, with "Left now" and "Joined now" buttons.
 */
@Composable
fun AvailabilityRows(
    crew: List<Person>,
    availability: Map<Long, Availability>,
    onChange: (Map<Long, Availability>) -> Unit,
    running: Boolean,
    elapsedMs: Long
) {
    fun of(person: Person) = availability[person.id] ?: Availability()
    fun set(person: Person, a: Availability) = onChange(availability + (person.id to a))

    // At least one person has to stay to the end.
    val staying = crew.count { of(it).endMs == null }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Arrivals and departures", style = MaterialTheme.typography.titleSmall)
        if (!running) {
            Text(
                "Leave blank for anyone working the whole job. Minutes count from when the timer starts.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        crew.forEach { person ->
            val a = of(person)
            if (!running) {
                Text(person.name, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutesText(a.startMs),
                        onValueChange = { text ->
                            val minutes = text.filter(Char::isDigit).take(3).toLongOrNull() ?: 0L
                            set(person, a.copy(startMs = minutes * 60_000L))
                        },
                        label = { Text("Starts after (min)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutesText(a.endMs ?: 0L),
                        onValueChange = { text ->
                            val minutes = text.filter(Char::isDigit).take(3).toLongOrNull() ?: 0L
                            set(person, a.copy(endMs = (minutes * 60_000L).takeIf { it > 0 }))
                        },
                        label = { Text("Leaves after (min)") },
                        enabled = !(a.endMs == null && staying <= 1),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                val end = a.endMs
                val status = when {
                    end != null && end <= elapsedMs -> "left at ${formatClock(end)}"
                    a.startMs > elapsedMs -> "joins at ${formatClock(a.startMs)}"
                    end != null -> "working, leaves at ${formatClock(end)}"
                    else -> "working"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${person.name}: $status", modifier = Modifier.weight(1f))
                    when {
                        a.startMs > elapsedMs ->
                            TextButton(onClick = { set(person, a.copy(startMs = elapsedMs)) }) {
                                Text("Joined now")
                            }
                        end != null && end <= elapsedMs ->
                            TextButton(onClick = { set(person, a.copy(endMs = null)) }) {
                                Text("Back")
                            }
                        end != null || staying > 1 ->
                            TextButton(onClick = { set(person, a.copy(endMs = elapsedMs)) }) {
                                Text("Left now")
                            }
                    }
                }
            }
        }
    }
}