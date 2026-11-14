package dev.wolsey.windowtimer.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.db.Person
import dev.wolsey.windowtimer.data.db.SavedCrew
import dev.wolsey.windowtimer.data.store.SavedCrewStore
import dev.wolsey.windowtimer.ui.settings.CrewDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedCrewChips(
    people: List<Person>,
    selectedIds: List<Long>,
    onChange: (List<Long>) -> Unit,
    enabled: Boolean
) {
    val context = LocalContext.current
    val crews by remember { SavedCrewStore.crews(context) }.collectAsState(initial = emptyList())
    var saving by remember { mutableStateOf(false) }

    // Anyone who has since been deleted drops out; a crew with no one left is hidden.
    val usable = crews.map { c -> c to c.ids.filter { id -> people.any { it.id == id } } }
        .filter { it.second.isNotEmpty() }
    val matching = usable.firstOrNull { it.second.toSet() == selectedIds.toSet() }?.first
    val canSave = matching == null && selectedIds.size > 1

    if (usable.isEmpty() && !canSave) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        usable.forEach { (crew, ids) ->
            FilterChip(
                selected = crew == matching,
                enabled = enabled,
                onClick = { onChange(ids) },
                label = { Text(crew.name) }
            )
        }
        if (canSave) {
            AssistChip(
                enabled = enabled,
                onClick = { saving = true },
                label = { Text("Save crew") }
            )
        }
    }

    @Suppress("AssignedValueIsNeverRead")
    if (saving) {
        CrewDialog(
            people = people,
            crew = SavedCrew("", SavedCrew.join(selectedIds)),
            onDismiss = { saving = false }
        )
    }
}