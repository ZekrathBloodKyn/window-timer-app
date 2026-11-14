package dev.wolsey.windowtimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.db.Person
import dev.wolsey.windowtimer.data.db.SavedCrew
import dev.wolsey.windowtimer.data.store.SavedCrewStore
import dev.wolsey.windowtimer.ui.timer.CrewChips
import kotlinx.coroutines.launch

/** [crew] with id 0 is a new crew; otherwise it's edited in place. */
@Composable
fun CrewDialog(
    people: List<Person>,
    crew: SavedCrew,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(crew.name) }
    var ids by remember {
        mutableStateOf(
            crew.ids.filter { id -> people.any { it.id == id } }
                .ifEmpty { listOfNotNull(people.firstOrNull()?.id) }
        )
    }
    var taken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (crew.id == 0L) "New crew" else "Edit crew") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30); taken = false },
                    label = { Text("Crew name") },
                    singleLine = true,
                    isError = taken,
                    supportingText = { if (taken) Text("A crew with that name already exists") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Who's in it", style = MaterialTheme.typography.titleSmall)
                CrewChips(people = people, selectedIds = ids, onChange = { ids = it }, enabled = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && ids.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val saved = SavedCrewStore.save(
                            context,
                            crew.copy(name = name.trim(), memberIds = SavedCrew.join(ids))
                        )
                        @Suppress("AssignedValueIsNeverRead")
                        if (saved) onDismiss() else taken = true
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}