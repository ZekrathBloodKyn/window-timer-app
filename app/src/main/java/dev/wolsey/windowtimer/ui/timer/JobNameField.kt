package dev.wolsey.windowtimer.ui.timer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.wolsey.windowtimer.data.db.Job
import dev.wolsey.windowtimer.data.store.JobStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobNameField(
    name: String,
    onNameChange: (String) -> Unit,
    onPick: (Job) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // A live view of the saved jobs; it updates by itself whenever they change.
    val jobs by remember { JobStore.jobs(context) }.collectAsState(initial = emptyList())

    val matches = if (name.isBlank()) jobs
    else jobs.filter { it.name.contains(name.trim(), ignoreCase = true) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = {
            @Suppress("AssignedValueIsNeverRead")
            if (enabled) expanded = it
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                onNameChange(it.take(40))
                expanded = true
            },
            label = { Text("Job name") },
            enabled = enabled,
            singleLine = true,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (name.isNotEmpty() && enabled) {
                        IconButton(onClick = {
                            onNameChange("")
                            expanded = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear job name")
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
        )

        if (matches.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                matches.forEach { job ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(job.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${job.interior} interior · ${job.exterior} exterior",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            onPick(job)
                            expanded = false
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                scope.launch { JobStore.delete(context, job) }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${job.name}")
                            }
                        }
                    )
                }
            }
        }
    }
}