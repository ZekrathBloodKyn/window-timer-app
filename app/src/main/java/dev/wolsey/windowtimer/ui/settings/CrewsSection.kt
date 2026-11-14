package dev.wolsey.windowtimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.db.SavedCrew
import dev.wolsey.windowtimer.data.store.PersonStore
import dev.wolsey.windowtimer.data.store.SavedCrewStore
import kotlinx.coroutines.launch

@Composable
fun CrewsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val people by remember { PersonStore.people(context) }.collectAsState(initial = emptyList())
    val crews by remember { SavedCrewStore.crews(context) }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<SavedCrew?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Crews", style = MaterialTheme.typography.titleMedium)
        Text(
            "Save a group of people to pick them together on the timer screen.",
            style = MaterialTheme.typography.bodySmall
        )

        crews.forEach { crew ->
            val names = crew.ids.mapNotNull { id -> people.firstOrNull { it.id == id }?.name }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(crew.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        names.joinToString(", ").ifEmpty { "No one left in this crew" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { editing = crew }) { Text("Edit") }
                TextButton(onClick = { scope.launch { SavedCrewStore.delete(context, crew.id) } }) {
                    Text("Delete")
                }
            }
        }

        OutlinedButton(
            enabled = people.size > 1,
            onClick = { editing = SavedCrew("", "") }
        ) { Text("Add crew") }
        if (people.size <= 1) {
            Text("Add another person above to make a crew.", style = MaterialTheme.typography.bodySmall)
        }
    }

    editing?.let {
        @Suppress("AssignedValueIsNeverRead")
        CrewDialog(people = people, crew = it, onDismiss = { editing = null })
    }
}