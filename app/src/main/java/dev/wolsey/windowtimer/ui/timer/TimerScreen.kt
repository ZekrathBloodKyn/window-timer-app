package dev.wolsey.windowtimer.ui.timer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.wolsey.windowtimer.alerts.Alerts
import dev.wolsey.windowtimer.data.Breakdown
import dev.wolsey.windowtimer.data.Crew
import dev.wolsey.windowtimer.data.TypeCount
import dev.wolsey.windowtimer.data.db.Job
import dev.wolsey.windowtimer.data.store.JobStore
import dev.wolsey.windowtimer.data.store.PersonStore
import dev.wolsey.windowtimer.data.store.TypeStore
import dev.wolsey.windowtimer.data.store.VisitStore
import dev.wolsey.windowtimer.timer.AvailabilityCodec
import dev.wolsey.windowtimer.timer.IntervalSettings
import dev.wolsey.windowtimer.timer.Mark
import dev.wolsey.windowtimer.timer.NOT_HERE_MS
import dev.wolsey.windowtimer.timer.TimerService
import dev.wolsey.windowtimer.timer.buildMarks
import dev.wolsey.windowtimer.timer.formatClock
import dev.wolsey.windowtimer.timer.formatCountdown
import dev.wolsey.windowtimer.timer.interiorMillis
import dev.wolsey.windowtimer.timer.totalMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimerScreen(
    jobName: String,
    onJobNameChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    rows: List<CountRow>,
    onRowsChange: (List<CountRow>) -> Unit,
    crewIds: List<Long>,
    onCrewChange: (List<Long>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRoute: () -> Unit,
    onOpenJobs: () -> Unit,
    availability: String,
    onAvailabilityChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var confirmCancel by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var finishElapsedMs by remember { mutableLongStateOf(0L) }
    var counterFor by remember { mutableStateOf<Pair<Long, WindowKind>?>(null) }

    // Mirrors of the service state, refreshed a few times per second. Compose only
    // redraws for values held in state, so `paused` has to live here too.
    var running by remember { mutableStateOf(TimerService.running) }
    var paused by remember { mutableStateOf(TimerService.paused) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var serviceTotal by remember { mutableLongStateOf(0L) }
    var serviceMarks by remember { mutableStateOf<List<Mark>>(emptyList()) }
    var stopwatchMode by rememberSaveable { mutableStateOf(false) }
    var serviceStopwatch by remember { mutableStateOf(TimerService.stopwatch) }
    var fired by remember { mutableIntStateOf(TimerService.firedCount) }

    // Window types, people, and the open visit for the job name that's entered.
    val types by remember { TypeStore.types(context) }.collectAsState(initial = emptyList())
    val standard = types.firstOrNull { it.isStandard }
    val people by remember { PersonStore.people(context) }.collectAsState(initial = emptyList())
    val openVisit by remember(jobName) { VisitStore.openVisit(context, jobName) }
        .collectAsState(initial = null)
    val visitTimeFormat = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }

    // Ask for notification permission once (Android 13+).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The timer works either way; only the notification is hidden if denied. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            running = TimerService.running
            paused = TimerService.paused
            elapsed = TimerService.elapsedMs()
            serviceTotal = TimerService.totalMs
            serviceMarks = TimerService.marks
            serviceStopwatch = TimerService.stopwatch
            fired = TimerService.firedCount
            delay(250)
        }
    }

    // The Standard row is always there to start with.
    LaunchedEffect(standard?.id) {
        if (rows.isEmpty() && standard != null) {
            onRowsChange(listOf(CountRow(standard.id, standard.name)))
        }
    }

    // Start with the crew from the last run, or just "Me" the first time.
    LaunchedEffect(people) {
        if (people.isNotEmpty() && people.none { it.id in crewIds }) {
            val last = PersonStore.lastCrewIds(context).filter { id -> people.any { it.id == id } }
            onCrewChange(last.ifEmpty { listOfNotNull(people.firstOrNull { it.isOwner }?.id) })
        }
    }

    // Keep the screen on while a timer is actually counting; let it sleep when paused or idle.
    val view = LocalView.current
    DisposableEffect(running, paused) {
        view.keepScreenOn = running && !paused
        onDispose { view.keepScreenOn = false }
    }

    val crew = people.filter { it.id in crewIds }
    val crewIdSet = crew.map { it.id }.toSet()
    // The time now, refreshed twice a minute, for turning picked clock times into offsets.
    val clockNow by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val presence = AvailabilityCodec.decode(availability).filterKeys { it in crewIdSet }
    // As offsets from the timer's start, for the estimate.
    val offsets = presence.mapValues { it.value.resolve(clockNow) }
    val counts = rows.toCounts()
    val interiorCount = Breakdown.totalInterior(counts)
    val exteriorCount = Breakdown.totalExterior(counts)
    val total = if (running) serviceTotal else totalMillis(counts, types, crew, offsets)
    val isStopwatch = if (running) serviceStopwatch else stopwatchMode
    val canStart = isStopwatch || total > 0

    // While running, the alerts are the ones the service was started with.
    val plan = remember { IntervalSettings.load(context) }
    val interiorMs = interiorMillis(counts, types, crew, offsets)
    val marks = if (running) serviceMarks else buildMarks(total, interiorMs, plan)

    val resetChecks = {
        TimerService.resetFired()
        fired = 0
    }

    // While running, re-estimate when someone arrives, leaves, or is marked as joining later.
    LaunchedEffect(availability, running) {
        if (running && !isStopwatch) {
            val newTotal = totalMillis(counts, types, crew, offsets)
            val newInterior = interiorMillis(counts, types, crew, offsets)
            if (newTotal > 0) {
                context.startService(
                    Intent(context, TimerService::class.java)
                        .setAction(TimerService.ACTION_RETIME)
                        .putExtra(TimerService.EXTRA_TOTAL_MS, newTotal)
                        .putExtra(TimerService.EXTRA_INTERIOR_MS, newInterior)
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Window Timer", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onOpenJobs, modifier = Modifier.weight(1f)) {
                Text("Jobs")
            }
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                Text("History")
            }
            OutlinedButton(onClick = onOpenRoute, modifier = Modifier.weight(1f)) {
                Text("Route")
            }
        }

        @Suppress("AssignedValueIsNeverRead")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isStopwatch,
                enabled = !running,
                onClick = {
                    stopwatchMode = false
                },
                label = { Text("Countdown") }
            )
            FilterChip(
                selected = isStopwatch,
                enabled = !running,
                onClick = { stopwatchMode = true },
                label = { Text("Stopwatch") }
            )
        }

        JobNameField(
            name = jobName,
            onNameChange = onJobNameChange,
            onPick = { job ->
                onJobNameChange(job.name)
                onAddressChange(job.address)
                onRowsChange(
                    Breakdown.resolve(job.breakdown, job.interior, job.exterior, standard)
                        .toRows()
                        .ifEmpty { listOfNotNull(standard?.let { CountRow(it.id, it.name) }) }
                )
                resetChecks()
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = address,
            onValueChange = { onAddressChange(it.take(120)) },
            label = { Text("Address (optional)") },
            enabled = !running,
            maxLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )

        // Arrival / departure for the job name that's entered.
        val visit = openVisit
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (visit == null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            VisitStore.markArrival(context, jobName)
                            Toast.makeText(context, "Arrival marked", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = jobName.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mark arrival")
                }
            } else {
                Text(
                    text = "Arrived ${visitTimeFormat.format(Date(visit.arrivedAt))}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            VisitStore.markDeparture(context, visit)
                            Toast.makeText(context, "Departure marked", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Mark departure")
                }
            }
        }

        // Only shown once there's more than one person to choose from.
        if (people.size > 1) {
            // Who has "times unknown" switched on, kept as text so it survives rotation.
            var unknownText by rememberSaveable { mutableStateOf("") }
            val unknownIds = unknownText.split(',').mapNotNull { it.toLongOrNull() }.toSet()

            val personTimes = rememberPersonTimes(
                crew = crew,
                availability = presence,
                onChange = { onAvailabilityChange(AvailabilityCodec.encode(it)) },
                running = running,
                elapsedMs = elapsed,
                unknownIds = unknownIds,
                onUnknownChange = { id, on ->
                    unknownText = (if (on) unknownIds + id else unknownIds - id).joinToString(",")
                }
            )
            CrewChips(
                people = people,
                selectedIds = crewIds,
                onChange = {
                    onCrewChange(it)
                    resetChecks()
                },
                enabled = !running,
                canEditTimes = personTimes.canEdit,
                onEditTimes = personTimes.open
            )
            SavedCrewChips(people, crewIds, onCrewChange, enabled = !running)
            LiveTimes(
                crew = crew,
                availability = presence,
                onChange = { onAvailabilityChange(AvailabilityCodec.encode(it)) },
                running = running,
                elapsedMs = elapsed,
                unknownIds = unknownIds
            )
        }

        TypeCountRows(
            rows = rows,
            types = types,
            onRowsChange = {
                onRowsChange(it)
                resetChecks()
            },
            enabled = !running,
            onCount = { typeId, kind -> counterFor = typeId to kind }
        )

        OutlinedButton(
            onClick = {
                val name = jobName.trim()
                scope.launch {
                    JobStore.save(
                        context,
                        Job(
                            name = name,
                            interior = interiorCount,
                            exterior = exteriorCount,
                            savedAt = System.currentTimeMillis(),
                            address = address.trim(),
                            breakdown = Breakdown.encode(counts)
                        )
                    )
                    Toast.makeText(context, "Saved \"$name\"", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = !running && jobName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save job")
        }

        Text(
            text = if (isStopwatch) {
                "Stopwatch · no estimate. Count windows when you finish."
            } else if (canStart) {
                val crewText = if (crew.size > 1) ", ${crew.size} people" else ""
                "Estimated: ${formatClock(total)}  " +
                        "($interiorCount interior, $exteriorCount exterior$crewText)"
            } else "Enter window counts",
            style = MaterialTheme.typography.bodyLarge
        )

        val overtime = !isStopwatch && running && elapsed > total

        Text(
            text = when {
                isStopwatch -> formatClock(if (running) elapsed else 0L)
                overtime -> "+" + formatClock(elapsed - total)
                running -> formatCountdown(total - elapsed)
                canStart -> formatClock(total)
                else -> "--:--"
            },
            color = when {
                overtime -> MaterialTheme.colorScheme.error
                paused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else -> Color.Unspecified
            },
            fontSize = 64.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (!isStopwatch) Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            marks.forEachIndexed { index, mark ->
                val done = fired > index
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${if (done) "✓" else "·"}  ${mark.label}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    val markMs = mark.timeMs
                    val remaining = markMs - elapsed

                    Text(
                        text = when {
                            !canStart -> "--:--"
                            running && remaining >= 0 -> formatCountdown(remaining)
                            else -> if (!done) formatClock(markMs) else "00:00"
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        if (running) {
            OutlinedButton(
                onClick = {
                    val action =
                        if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE
                    context.startService(
                        Intent(context, TimerService::class.java).setAction(action)
                    )
                    paused = !paused   // the polling loop confirms it a moment later
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(if (paused) "Resume" else "Pause", fontSize = 20.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { confirmCancel = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Text("Cancel", fontSize = 22.sp)
                }
                Button(
                    onClick = {
                        finishElapsedMs = TimerService.elapsedMs()   // time at the moment of the tap
                        showFinish = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Text("Finish", fontSize = 22.sp)
                }
            }
        } else {
            Button(
                onClick = {
                    PersonStore.saveLastCrew(context, crew.map { it.id })

                    // Fix picked clock times against the real start time.
                    val startNow = System.currentTimeMillis()
                    val frozen = presence.mapValues { it.value.resolve(startNow) }
                    val startTotal = totalMillis(counts, types, crew, frozen)
                    val startInterior = interiorMillis(counts, types, crew, frozen)
                    onAvailabilityChange(AvailabilityCodec.encode(frozen))

                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, TimerService::class.java)
                            .setAction(TimerService.ACTION_START)
                            .putExtra(TimerService.EXTRA_TOTAL_MS, startTotal)
                            .putExtra(TimerService.EXTRA_INTERIOR_MS, startInterior)
                            .putExtra(TimerService.EXTRA_JOB_NAME, jobName.trim())
                            .putExtra(TimerService.EXTRA_INTERIOR, interiorCount)
                            .putExtra(TimerService.EXTRA_EXTERIOR, exteriorCount)
                            .putExtra(TimerService.EXTRA_BREAKDOWN, Breakdown.encode(counts))
                            .putExtra(TimerService.EXTRA_STOPWATCH, isStopwatch)
                    )
                },
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text("Start", fontSize = 22.sp)
            }
        }
    }

    @Suppress("AssignedValueIsNeverRead")
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Discard this run?") },
            text = { Text("Nothing will be recorded in history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    context.startService(
                        Intent(context, TimerService::class.java)
                            .setAction(TimerService.ACTION_CANCEL)
                    )
                    Alerts.stop()
                    onAvailabilityChange("")
                    Toast.makeText(context, "Run discarded", Toast.LENGTH_SHORT).show()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep running") }
            }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    if (showFinish) {
        // A stopwatch run has every type on offer, since nothing was planned per type.
        val stopwatchPlanned = types.map { type ->
            counts.firstOrNull { it.typeId == type.id } ?: TypeCount(type.id, type.name, 0, 0)
        }
        FinishFlow(
            planned = if (isStopwatch) stopwatchPlanned else counts,
            stopwatch = isStopwatch,
            crew = crew,
            onConfirm = { done, members ->
                showFinish = false
                Toast.makeText(
                    context, "Recorded ${formatClock(finishElapsedMs)}", Toast.LENGTH_SHORT
                ).show()
                context.startService(
                    Intent(context, TimerService::class.java)
                        .setAction(TimerService.ACTION_FINISH)
                        .putExtra(TimerService.EXTRA_ACTUAL_MS, finishElapsedMs)
                        .putExtra(TimerService.EXTRA_DONE_INTERIOR, Breakdown.totalInterior(done))
                        .putExtra(TimerService.EXTRA_DONE_EXTERIOR, Breakdown.totalExterior(done))
                        .putExtra(TimerService.EXTRA_DONE_BREAKDOWN, Breakdown.encode(done))
                        .putExtra(TimerService.EXTRA_DONE_CREW, Crew.encode(members))
                )
                Alerts.stop()
                onAvailabilityChange("")
            },
            onDismiss = { showFinish = false },
            availability = offsets.mapValues { (_, a) ->
                if (a.startMs >= NOT_HERE_MS) a.copy(startMs = finishElapsedMs) else a
            },
            elapsedMs = finishElapsedMs,
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    counterFor?.let { (typeId, kind) ->
        val row = rows.firstOrNull { it.typeId == typeId }
        if (row != null) {
            val kindLabel = if (kind == WindowKind.INTERIOR) "Interior" else "Exterior"
            val current = (if (kind == WindowKind.INTERIOR) row.interior else row.exterior)
                .toIntOrNull() ?: 0
            FullScreenCounter(
                title = "${row.name} · $kindLabel windows",
                count = current,
                onCountChange = { n ->
                    onRowsChange(
                        rows.map {
                            if (it.typeId != typeId) it
                            else if (kind == WindowKind.INTERIOR) it.copy(interior = n.toString())
                            else it.copy(exterior = n.toString())
                        }
                    )
                    resetChecks()
                },
                onDone = { counterFor = null }
            )
        }
    }
}