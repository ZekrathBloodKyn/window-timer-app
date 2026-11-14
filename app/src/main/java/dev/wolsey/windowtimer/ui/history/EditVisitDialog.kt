package dev.wolsey.windowtimer.ui.history

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.wolsey.windowtimer.data.db.Visit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class PickTarget { ARRIVAL, DEPARTURE }

/** Returns [base] with its time of day replaced, keeping the same date. */
fun withTimeOfDay(base: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = base
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

@Composable
fun EditVisitDialog(
    visit: Visit,
    onSave: (Visit) -> Unit,
    onDismiss: () -> Unit
) {
    val format = remember { SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()) }

    var name by remember { mutableStateOf(visit.jobName) }
    var arrivedAt by remember { mutableLongStateOf(visit.arrivedAt) }
    var departedAt by remember { mutableStateOf(visit.departedAt) }
    var picking by remember { mutableStateOf<PickTarget?>(null) }

    val departureBeforeArrival = departedAt?.let { it < arrivedAt } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit visit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Job name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                TimeRow(
                    label = "Arrived",
                    text = format.format(Date(arrivedAt)),
                    onChange = { picking = PickTarget.ARRIVAL },
                    onClear = null
                )
                TimeRow(
                    label = "Departed",
                    text = departedAt?.let { format.format(Date(it)) } ?: "Not marked",
                    onChange = { picking = PickTarget.DEPARTURE },
                    onClear = if (departedAt != null) ({ departedAt = null }) else null
                )

                if (departureBeforeArrival) {
                    Text(
                        "Departure is before arrival",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !departureBeforeArrival,
                onClick = {
                    onSave(
                        visit.copy(
                            jobName = name.trim(),
                            arrivedAt = arrivedAt,
                            departedAt = departedAt
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    @Suppress("AssignedValueIsNeverRead")
    picking?.let { target ->
        val base = if (target == PickTarget.ARRIVAL) arrivedAt else (departedAt ?: arrivedAt)
        TimePickerDialog(
            initial = base,
            onConfirm = { hour, minute ->
                val updated = withTimeOfDay(base, hour, minute)
                if (target == PickTarget.ARRIVAL) arrivedAt = updated else departedAt = updated
                picking = null
            },
            onDismiss = { picking = null }
        )
    }
}

@Composable
private fun TimeRow(
    label: String,
    text: String,
    onChange: () -> Unit,
    onClear: (() -> Unit)?
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, modifier = Modifier.weight(1f))
            TextButton(onClick = onChange) { Text("Change") }
            if (onClear != null) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initial: Long,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val calendar = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    val state = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = DateFormat.is24HourFormat(context)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
                }
            }
        }
    }
}