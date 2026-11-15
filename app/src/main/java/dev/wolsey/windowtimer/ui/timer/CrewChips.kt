package dev.wolsey.windowtimer.ui.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.db.Person

/**
 * Chips to choose who is working this job. At least one person has to stay selected.
 *
 * With [onEditTimes] set, people for whom [canEditTimes] is true get a pencil on their chip
 * for setting when they arrive or leave. While [enabled] is false, which is during a run,
 * tapping such a chip opens the same thing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrewChips(
    people: List<Person>,
    selectedIds: List<Long>,
    onChange: (List<Long>) -> Unit,
    enabled: Boolean,
    canEditTimes: (Person) -> Boolean = { false },
    onEditTimes: ((Person) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Crew", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            people.forEach { person ->
                val selected = person.id in selectedIds
                val editable = selected && onEditTimes != null && canEditTimes(person)
                FilterChip(
                    selected = selected,
                    // Stays tappable during a run when it opens the times instead.
                    enabled = enabled || editable,
                    onClick = {
                        if (enabled) {
                            val next = if (selected) selectedIds - person.id else selectedIds + person.id
                            if (next.isNotEmpty()) onChange(next)
                        } else {
                            onEditTimes?.invoke(person)
                        }
                    },
                    label = { Text(person.name) },
                    trailingIcon = if (editable) {
                        {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Set ${person.name}'s arrival and departure",
                                modifier = Modifier
                                    .size(FilterChipDefaults.IconSize)
                                    .clickable { onEditTimes.invoke(person) }
                            )
                        }
                    } else null
                )
            }
        }
    }
}