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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.Breakdown
import dev.wolsey.windowtimer.data.Crew
import dev.wolsey.windowtimer.data.db.Run
import dev.wolsey.windowtimer.data.store.PersonStore
import dev.wolsey.windowtimer.data.store.TypeStore

/**
 * Edits the name, actual time, and who did how many windows of each type for a recorded run.
 * The date, estimate and planned counts stay as they were.
 */
@Composable
fun EditRunDialog(
    run: Run,
    onSave: (Run) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalSeconds = run.actualMs / 1000

    var name by remember { mutableStateOf(run.jobName) }
    var hours by remember { mutableStateOf((totalSeconds / 3600).toString()) }
    var minutes by remember { mutableStateOf(((totalSeconds % 3600) / 60).toString()) }
    var seconds by remember { mutableStateOf((totalSeconds % 60).toString()) }

    val types by remember { TypeStore.types(context) }.collectAsState(initial = emptyList())
    val standard = types.firstOrNull { it.isStandard }
    val people by remember { PersonStore.people(context) }.collectAsState(initial = emptyList())
    val crewState = rememberCrewEditState(run, people, standard)

    val plannedText = standard?.let {
        Breakdown.describe(
            Breakdown.resolve(run.plannedBreakdown, run.plannedInterior, run.plannedExterior, it)
        )
    }.orEmpty()

    val newSeconds = (hours.toLongOrNull() ?: 0L) * 3600 +
            (minutes.toLongOrNull() ?: 0L) * 60 +
            (seconds.toLongOrNull() ?: 0L)
    val timeValid = newSeconds > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit run") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Job name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Actual time", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hours") },
                        singleLine = true,
                        isError = !timeValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                        label = { Text("Min") },
                        singleLine = true,
                        isError = !timeValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                        label = { Text("Sec") },
                        singleLine = true,
                        isError = !timeValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Windows done", style = MaterialTheme.typography.titleSmall)
                if (plannedText.isNotBlank() && run.plannedMs > 0) {
                    Text("Planned: $plannedText", style = MaterialTheme.typography.bodySmall)
                }
                if (crewState.loaded) {
                    CrewEditor(crewState, types, standard, run.startedAt)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = timeValid && crewState.loaded,
                onClick = {
                    val members = crewState.members()
                    val totals = Crew.totals(members)
                    onSave(
                        run.copy(
                            jobName = name.trim(),
                            actualMs = newSeconds * 1000L,
                            interior = Breakdown.totalInterior(totals),
                            exterior = Breakdown.totalExterior(totals),
                            breakdown = Breakdown.encode(totals),
                            crew = Crew.encode(members)
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}