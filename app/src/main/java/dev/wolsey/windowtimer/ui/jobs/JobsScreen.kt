@file:Suppress("AssignedValueIsNeverRead")

package dev.wolsey.windowtimer.ui.jobs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.Breakdown
import dev.wolsey.windowtimer.data.db.Job
import dev.wolsey.windowtimer.data.store.JobStore
import dev.wolsey.windowtimer.data.store.TypeStore
import dev.wolsey.windowtimer.ui.timer.CountRow
import dev.wolsey.windowtimer.ui.timer.TypeCountRows
import dev.wolsey.windowtimer.ui.timer.toCounts
import dev.wolsey.windowtimer.ui.timer.toRows
import kotlinx.coroutines.launch

@Composable
fun JobsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // A live view of the saved jobs; it updates by itself whenever they change.
    val jobs by remember { JobStore.jobs(context) }.collectAsState(initial = emptyList())

    var editing by remember { mutableStateOf<Job?>(null) }
    var deleting by remember { mutableStateOf<Job?>(null) }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Jobs", style = MaterialTheme.typography.headlineMedium)
        }

        if (jobs.isEmpty()) {
            Text("No saved jobs yet. Save one from the timer screen.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(jobs, key = { it.id }) { job ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(
                                start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(job.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${job.interior} interior · ${job.exterior} exterior",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                // Only shown once a job uses more than one window type.
                                val breakdown = Breakdown.decode(job.breakdown)
                                if (breakdown.size > 1) {
                                    Text(
                                        Breakdown.describe(breakdown),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (job.address.isNotBlank()) {
                                    Text(job.address, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { editing = job }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${job.name}")
                            }
                            IconButton(onClick = { deleting = job }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${job.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("AssignedValueIsNeverRead")
    editing?.let { job ->
        EditJobDialog(
            job = job,
            onSave = { updated ->
                // Renaming also moves the job's recorded runs, all in one step.
                if (JobStore.update(context, job, updated)) {
                    editing = null
                    null
                } else {
                    "Another job already has that name"
                }
            },
            onDismiss = { editing = null }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    deleting?.let { job ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${job.name}\"?") },
            text = { Text("Its recorded runs stay in History.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { JobStore.delete(context, job) }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Keep") }
            }
        )
    }
}

/**
 * Edits one job. onSave returns an error message to show, or null when the
 * save worked (the caller then closes the dialog).
 */
@Composable
private fun EditJobDialog(
    job: Job,
    onSave: suspend (Job) -> String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(job.name) }
    var address by remember { mutableStateOf(job.address) }
    var error by remember { mutableStateOf<String?>(null) }

    val types by remember { TypeStore.types(context) }.collectAsState(initial = emptyList())
    val standard = types.firstOrNull { it.isStandard }

    // Filled in once the Standard type has loaded, so jobs saved before window
    // types existed show up as Standard.
    var rows by remember { mutableStateOf<List<CountRow>?>(null) }
    LaunchedEffect(standard?.id) {
        if (rows == null && standard != null) {
            rows = Breakdown.resolve(job.breakdown, job.interior, job.exterior, standard).toRows()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit job") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); error = null },
                    label = { Text("Job name") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.take(120) },
                    label = { Text("Address (optional)") },
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                rows?.let { current ->
                    TypeCountRows(
                        rows = current,
                        types = types,
                        onRowsChange = { rows = it },
                        enabled = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && rows != null,
                onClick = {
                    val counts = rows?.toCounts() ?: return@TextButton
                    scope.launch {
                        error = onSave(
                            job.copy(
                                name = name.trim(),
                                address = address.trim(),
                                interior = Breakdown.totalInterior(counts),
                                exterior = Breakdown.totalExterior(counts),
                                breakdown = Breakdown.encode(counts)
                            )
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}