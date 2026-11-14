package dev.wolsey.windowtimer.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.Breakdown
import dev.wolsey.windowtimer.data.Crew
import dev.wolsey.windowtimer.data.db.Run
import dev.wolsey.windowtimer.data.store.RunStore
import dev.wolsey.windowtimer.data.RunSummary
import dev.wolsey.windowtimer.data.db.Visit
import dev.wolsey.windowtimer.data.shares
import dev.wolsey.windowtimer.data.store.PersonStore
import dev.wolsey.windowtimer.data.store.VisitStore
import dev.wolsey.windowtimer.data.summarize
import dev.wolsey.windowtimer.timer.formatClock
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Live views of the database; they update by themselves whenever anything changes.
    val runs by remember { RunStore.runs(context) }.collectAsState(initial = emptyList())
    val visits by remember { VisitStore.visits(context) }.collectAsState(initial = emptyList())
    val people by remember { PersonStore.people(context) }.collectAsState(initial = emptyList())

    val dateFormat = remember { SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // null = all jobs; "" = entries saved without a name.
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var presetName by rememberSaveable { mutableStateOf(TimeFramePreset.LAST_30.name) }
    var customFrom by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var customTo by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var personId by rememberSaveable { mutableLongStateOf(-2L) }   // -1 = everyone
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }
    var editingRun by remember { mutableStateOf<Run?>(null) }
    var editingVisit by remember { mutableStateOf<Visit?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var addingVisit by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val entries = remember(runs, visits) { buildEntries(runs, visits) }
    val names = entryJobNames(entries)
    // If the last entry of the chosen job is gone, fall back to all jobs.
    val activeFilter = filter?.takeIf { f -> names.any { it.equals(f, ignoreCase = true) } }
    val preset = TimeFramePreset.entries.firstOrNull { it.name == presetName } ?: TimeFramePreset.LAST_30
    val range = timeRangeFor(preset, customFrom, customTo)
    val ownerId = people.firstOrNull { it.isOwner }?.id ?: 0L
    // With more than one person, start on the owner (the first person). Choosing "Everyone"
    // is remembered as -1. A person who has been deleted also falls back to the owner.
    val activePerson = when {
        people.size < 2 || personId == -1L -> null
        else -> people.firstOrNull { it.id == personId }
            ?: people.firstOrNull { it.isOwner }
            ?: people.first()
    }

    val shown = entries.filter { entry ->
        entry.matches(activeFilter) &&
                (range == null || entry.sortTime in range) &&
                // Choosing a person shows only the runs they worked; visits have no crew.
                (activePerson == null || (entry is HistoryEntry.RunEntry &&
                        entry.run.shares(ownerId).any { it.personId == activePerson.id }))
    }
    // Averages are about runs only; visits that had no run don't count.
    val summary = summarize(
        shown.filterIsInstance<HistoryEntry.RunEntry>().map { it.run },
        activePerson?.id,
        ownerId,
        visits.associateBy { it.id }
    )

    val visitWindowTotals = remember(runs) {
        runs.mapNotNull { r -> r.visitId?.let { it to r.windows } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "History",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            Box {
                TextButton(onClick = { addMenuOpen = true }) { Text("Add") }
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Add past run") },
                        onClick = { addMenuOpen = false; adding = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Add past visit") },
                        onClick = { addMenuOpen = false; addingVisit = true }
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Text("Nothing yet. Finish a timer or mark an arrival to record something.")
        } else {
            JobFilter(
                names = names,
                selected = activeFilter,
                onSelect = {
                    @Suppress("AssignedValueIsNeverRead")
                    filter = it
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterDropdown(
                    label = "Time frame",
                    selectedLabel = preset.label,
                    options = TimeFramePreset.entries.map { it to it.label },
                    onSelect = {
                        @Suppress("AssignedValueIsNeverRead")
                        presetName = it.name
                    },
                    modifier = Modifier.weight(1f)
                )
                if (people.size > 1) {
                    FilterDropdown(
                        label = "Person",
                        selectedLabel = activePerson?.name ?: "Everyone",
                        options = listOf(-1L to "Everyone") + people.map { it.id to it.name },
                        onSelect = {
                            @Suppress("AssignedValueIsNeverRead")
                            personId = it
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (preset == TimeFramePreset.CUSTOM) {
                val rangeFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pickingFrom = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("From ${rangeFormat.format(Date(customFrom))}") }
                    OutlinedButton(
                        onClick = { pickingTo = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("To ${rangeFormat.format(Date(customTo))}") }
                }
            }

            if (summary != null) SummaryCard(summary, activePerson?.name)
            if (shown.isEmpty()) Text("Nothing matches these filters.")
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    shown,
                    key = { entry ->
                        when (entry) {
                            is HistoryEntry.RunEntry -> "run-${entry.run.id}"
                            is HistoryEntry.VisitEntry -> "visit-${entry.visit.id}"
                        }
                    }
                ) { entry ->
                    when (entry) {
                        is HistoryEntry.RunEntry -> RunCard(
                            run = entry.run,
                            visit = entry.visit,
                            date = dateFormat.format(Date(entry.run.startedAt)),
                            timeFormat = timeFormat,
                            onEdit = { editingRun = entry.run },
                            onEditVisit = { entry.visit?.let { editingVisit = it } },
                            onDelete = { scope.launch { RunStore.delete(context, entry.run) } },
                            visitWindows = entry.run.visitId?.let { visitWindowTotals[it] } ?: entry.run.windows,
                        )

                        is HistoryEntry.VisitEntry -> VisitCard(
                            visit = entry.visit,
                            date = dateFormat.format(Date(entry.visit.arrivedAt)),
                            timeFormat = timeFormat,
                            onEdit = { editingVisit = entry.visit },
                            onDelete = { scope.launch { VisitStore.delete(context, entry.visit) } }
                        )
                    }
                }
            }
        }
    }

    @Suppress("AssignedValueIsNeverRead")
    editingRun?.let { run ->
        EditRunDialog(
            run = run,
            onSave = { updated ->
                scope.launch { RunStore.update(context, updated) }
                editingRun = null
            },
            onDismiss = { editingRun = null }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    editingVisit?.let { visit ->
        EditVisitDialog(
            visit = visit,
            onSave = { updated ->
                scope.launch { VisitStore.update(context, updated) }
                editingVisit = null
            },
            onDismiss = { editingVisit = null }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    if (adding) {
        AddRunDialog(
            onSave = { run ->
                scope.launch { RunStore.addHistorical(context, run) }
                adding = false
            },
            onDismiss = { adding = false }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    if (addingVisit) {
        AddVisitDialog(
            onSave = { visit ->
                scope.launch { VisitStore.add(context, visit) }
                addingVisit = false
            },
            onDismiss = { addingVisit = false }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    if (pickingFrom) {
        DatePickDialog(
            initial = customFrom,
            onConfirm = { customFrom = it },
            onDismiss = { pickingFrom = false }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    if (pickingTo) {
        DatePickDialog(
            initial = customTo,
            onConfirm = { customTo = it },
            onDismiss = { pickingTo = false }
        )
    }
}

/** "On site 9:12 AM – 11:40 AM (2:28:00)", or "Arrived 9:12 AM · still on site". */
private fun visitText(visit: Visit, timeFormat: SimpleDateFormat): String {
    val arrived = timeFormat.format(Date(visit.arrivedAt))
    val departed = visit.departedAt ?: return "Arrived $arrived · still on site"
    val length = formatClock(departed - visit.arrivedAt)
    return "On site $arrived – ${timeFormat.format(Date(departed))} ($length)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobFilter(names: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        selected == null -> "All jobs"
        selected.isBlank() -> "Unnamed job"
        else -> selected
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {
        @Suppress("AssignedValueIsNeverRead")
        expanded = it
    }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Job") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All jobs") },
                onClick = { onSelect(null); expanded = false }
            )
            names.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name.ifBlank { "Unnamed job" }) },
                    onClick = { onSelect(name); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> FilterDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            @Suppress("AssignedValueIsNeverRead")
            expanded = it
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: RunSummary, personName: String?) {
    val diff = summary.avgDiffMs
    val diffText = (if (diff >= 0) "+" else "−") + formatClock(abs(diff))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val runs = "${summary.count} run${if (summary.count == 1) "" else "s"}"
            Text(
                if (personName == null) "Summary · $runs" else "$personName · $runs",
                style = MaterialTheme.typography.titleMedium
            )
            if (summary.fullRunCount > 0) {
                val note =
                    if (summary.fullRunCount == summary.count) ""
                    else " (${summary.fullRunCount} full runs)"
                Text("Avg estimate ${formatClock(summary.avgPlannedMs)}$note")
                Text("Avg actual ${formatClock(summary.avgActualMs)} ($diffText vs estimate)")
            }
            summary.overallPace?.let {
                Text(
                    if (personName == null) "Crew pace %.1f windows/hr".format(it)
                    else "$personName's pace %.1f windows/hr".format(it)
                )
            }
            summary.visitPace?.let {
                Text("On-site pace %.1f windows/hr".format(it))
            }
            // Only worth showing when it differs, i.e. at least one run had more than one person.
            val crewPace = summary.overallPace
            val perPerson = summary.perPersonPace
            if (crewPace != null && perPerson != null && abs(crewPace - perPerson) >= 0.05) {
                Text("Per person %.1f windows/hr".format(perPerson))
            }
        }
    }
}

@Composable
private fun RunCard(
    run: Run,
    visit: Visit?,
    date: String,
    timeFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onEditVisit: () -> Unit,
    onDelete: () -> Unit,
    visitWindows: Int
) {
    // The estimate covered the full job, so only compare it when everything was done.
    val diffText = if (run.isPartial) "" else {
        val diff = run.actualMs - run.plannedMs
        " (" + (if (diff >= 0) "+" else "−") + formatClock(abs(diff)) + ")"
    }

    val interiorText =
        if (run.interior < run.plannedInterior) "${run.interior} of ${run.plannedInterior} interior"
        else "${run.interior} interior"
    val exteriorText =
        if (run.exterior < run.plannedExterior) "${run.exterior} of ${run.plannedExterior} exterior"
        else "${run.exterior} exterior"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        run.jobName.ifBlank { "Unnamed job" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(date, style = MaterialTheme.typography.bodySmall)
                    if (run.isPartial) {
                        Text(
                            "Partial job",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit run")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete run")
                    }
                }
            }
            Text(
                "Estimate ${formatClock(run.plannedMs)} · Actual ${formatClock(run.actualMs)}$diffText",
                style = MaterialTheme.typography.bodyMedium
            )
            val pace = run.actualPace?.let { " · %.1f windows/hr".format(it) } ?: ""
            Text(
                "$interiorText · $exteriorText$pace",
                style = MaterialTheme.typography.bodyMedium
            )
            val breakdown = Breakdown.decode(run.breakdown)
            if (breakdown.size > 1) {
                Text(Breakdown.describe(breakdown), style = MaterialTheme.typography.bodySmall)
            }
            val crew = Crew.decode(run.crew)
            if (crew.size > 1) {
                crew.forEach { member ->
                    // Everyone works the same clock, so a person's pace is their windows over the run time.
                    val pace = if (run.actualMs > 0 && member.windows > 0)
                        " · %.1f/hr".format(member.windows * 3_600_000.0 / run.actualMs)
                    else ""
                    Text(
                        "${member.name}: ${member.windows} windows$pace",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (visit != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        visitText(visit, timeFormat),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onEditVisit) { Text("Edit visit") }
                }
                val onSite = visit.departedAt?.let { it - visit.arrivedAt }
                if (onSite != null && onSite > 0 && visitWindows > 0) {
                    Text(
                        "On-site pace %.1f windows/hr".format(visitWindows * 3_600_000.0 / onSite),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun VisitCard(
    visit: Visit,
    date: String,
    timeFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        visit.jobName.ifBlank { "Unnamed job" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(date, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "No run",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit visit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete visit")
                    }
                }
            }
            Text(visitText(visit, timeFormat), style = MaterialTheme.typography.bodyMedium)
        }
    }
}