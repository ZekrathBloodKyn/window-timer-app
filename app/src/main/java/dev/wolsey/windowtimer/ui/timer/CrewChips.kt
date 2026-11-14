package dev.wolsey.windowtimer.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.db.Person

/** Chips to choose who is working this job. At least one person has to stay selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrewChips(
    people: List<Person>,
    selectedIds: List<Long>,
    onChange: (List<Long>) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Crew", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            people.forEach { person ->
                val selected = person.id in selectedIds
                FilterChip(
                    selected = selected,
                    enabled = enabled,
                    onClick = {
                        val next = if (selected) selectedIds - person.id else selectedIds + person.id
                        if (next.isNotEmpty()) onChange(next)
                    },
                    label = { Text(person.name) }
                )
            }
        }
    }
}