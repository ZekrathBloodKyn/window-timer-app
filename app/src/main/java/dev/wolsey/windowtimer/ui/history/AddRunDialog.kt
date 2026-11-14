package dev.wolsey.windowtimer.ui.history

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.Breakdown
import dev.wolsey.windowtimer.data.Crew
import dev.wolsey.windowtimer.data.store.PersonStore
import dev.wolsey.windowtimer.data.db.Run
import dev.wolsey.windowtimer.data.store.TypeStore
import dev.wolsey.windowtimer.timer.totalMillis
import dev.wolsey.windowtimer.ui.timer.JobNameField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adds a run from the past. All the windows entered are treated as done, and the
 * estimate is worked out from the crew's paces for those counts.
 */
@Composable
fun AddRunDialog(
    onSave: (Run) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val format = remember { SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()) }

    var name by remember { mutableStateOf("") }
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    val types by remember { TypeStore.types(context) }.collectAsState(initial = emptyList())
    val standard = types.firstOrNull { it.isStandard }
    val people by remember { PersonStore.people(context) }.collectAsState(initial = emptyList())
    val crewState = rememberCrewEditState(null, people, standard)

    val members = crewState.members()
    val totals = Crew.totals(members)
    val interiorCount = Breakdown.totalInterior(totals)
    val exteriorCount = Breakdown.totalExterior(totals)

    val totalSeconds = (hours.toLongOrNull() ?: 0L) * 3600 +
            (minutes.toLongOrNull() ?: 0L) * 60 +
            (seconds.toLongOrNull() ?: 0L)
    val inFuture = startedAt > System.currentTimeMillis()
    val valid = crewState.loaded && totalSeconds > 0 &&
            interiorCount + exteriorCount > 0 && !inFuture

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add past run") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                JobNameField(
                    name = name,
                    onNameChange = { name = it },
                    onPick = { job ->
                        name = job.name
                        // Picking a saved job hands its windows out between the crew by pace.
                        if (crewState.loaded) {
                            crewState.applyPlanned(
                                Breakdown.resolve(job.breakdown, job.interior, job.exterior, standard),
                                standard
                            )
                        }
                    },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Started", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(format.format(Date(startedAt)), modifier = Modifier.weight(1f))
                    TextButton(onClick = { pickingDate = true }) { Text("Date") }
                    TextButton(onClick = { pickingTime = true }) { Text("Time") }
                }
                if (inFuture) {
                    Text(
                        "Start time is in the future",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text("Actual time", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hours") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                        label = { Text("Min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                        label = { Text("Sec") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Windows done", style = MaterialTheme.typography.titleSmall)
                if (crewState.loaded) {
                    CrewEditor(crewState, types, standard)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val encoded = Breakdown.encode(totals)
                    onSave(
                        Run(
                            jobName = name.trim(),
                            startedAt = startedAt,
                            plannedMs = totalMillis(totals, types, crewState.crewPeople()),
                            actualMs = totalSeconds * 1000L,
                            interior = interiorCount,
                            exterior = exteriorCount,
                            breakdown = encoded,
                            plannedBreakdown = encoded,
                            crew = Crew.encode(members)
                        )
                    )
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    @Suppress("AssignedValueIsNeverRead")
    if (pickingDate) {
        DatePickDialog(
            initial = startedAt,
            onConfirm = { startedAt = it },
            onDismiss = { pickingDate = false }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    if (pickingTime) {
        TimePickerDialog(
            initial = startedAt,
            onConfirm = { hour, minute ->
                startedAt = withTimeOfDay(startedAt, hour, minute)
                pickingTime = false
            },
            onDismiss = { pickingTime = false }
        )
    }
}