package dev.wolsey.windowtimer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.db.Visit
import dev.wolsey.windowtimer.ui.timer.JobNameField
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/** Adds a visit from the past: a job name, an arrival, and an optional departure. */
@Composable
fun AddVisitDialog(
    onSave: (Visit) -> Unit,
    onDismiss: () -> Unit
) {
    val format = remember { SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()) }

    var name by remember { mutableStateOf("") }
    var arrivedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var departedAt by remember { mutableStateOf<Long?>(null) }
    var pickingArrivalDate by remember { mutableStateOf(false) }
    var pickingArrivalTime by remember { mutableStateOf(false) }
    var pickingDeparture by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val arrivalInFuture = arrivedAt > now
    val departureBeforeArrival = departedAt?.let { it < arrivedAt } == true
    val departureInFuture = departedAt?.let { it > now } == true
    val valid = name.isNotBlank() && !arrivalInFuture &&
            !departureBeforeArrival && !departureInFuture

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add past visit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                JobNameField(
                    name = name,
                    onNameChange = { name = it },
                    onPick = { job -> name = job.name },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Arrived", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(format.format(Date(arrivedAt)), modifier = Modifier.weight(1f))
                    TextButton(onClick = { pickingArrivalDate = true }) { Text("Date") }
                    TextButton(onClick = { pickingArrivalTime = true }) { Text("Time") }
                }

                Text("Departed", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        departedAt?.let { format.format(Date(it)) } ?: "Not marked",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { pickingDeparture = true }) {
                        Text(if (departedAt == null) "Set time" else "Time")
                    }
                    if (departedAt != null) {
                        TextButton(onClick = { departedAt = null }) { Text("Clear") }
                    }
                }

                val problem = when {
                    arrivalInFuture -> "Arrival is in the future"
                    departureBeforeArrival -> "Departure is before arrival"
                    departureInFuture -> "Departure is in the future"
                    else -> null
                }
                if (problem != null) {
                    Text(
                        problem,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(Visit(jobName = name.trim(), arrivedAt = arrivedAt, departedAt = departedAt))
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    @Suppress("AssignedValueIsNeverRead")
    if (pickingArrivalDate) {
        DatePickDialog(
            initial = arrivedAt,
            onConfirm = { newArrival ->
                // Move the departure by the same amount, so the visit keeps its length.
                val shift = newArrival - arrivedAt
                arrivedAt = newArrival
                departedAt = departedAt?.plus(shift)
            },
            onDismiss = { pickingArrivalDate = false }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    if (pickingArrivalTime) {
        TimePickerDialog(
            initial = arrivedAt,
            onConfirm = { hour, minute ->
                arrivedAt = withTimeOfDay(arrivedAt, hour, minute)
                pickingArrivalTime = false
            },
            onDismiss = { pickingArrivalTime = false }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    if (pickingDeparture) {
        val base = departedAt ?: arrivedAt
        TimePickerDialog(
            initial = base,
            onConfirm = { hour, minute ->
                departedAt = withTimeOfDay(base, hour, minute)
                pickingDeparture = false
            },
            onDismiss = { pickingDeparture = false }
        )
    }
}

/**
 * Picks a date while keeping the time of day of [initial]. The date picker works in
 * UTC midnights, so this converts to and from the local date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickDialog(
    initial: Long,
    onConfirm: (newMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val localDate = Instant.ofEpochMilli(initial).atZone(zone).toLocalDate()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { utcMillis ->
                    val newDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    val timeOfDay = Instant.ofEpochMilli(initial).atZone(zone).toLocalTime()
                    onConfirm(
                        LocalDateTime.of(newDate, timeOfDay).atZone(zone).toInstant().toEpochMilli()
                    )
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}