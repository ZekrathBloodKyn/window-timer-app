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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.wolsey.windowtimer.data.db.Job
import dev.wolsey.windowtimer.data.store.JobStore
import dev.wolsey.windowtimer.data.store.PersonStore
import dev.wolsey.windowtimer.data.store.TypeStore
import dev.wolsey.windowtimer.data.store.VisitStore
import dev.wolsey.windowtimer.timer.QUARTERS
import dev.wolsey.windowtimer.timer.TimerService
import dev.wolsey.windowtimer.timer.formatClock
import dev.wolsey.windowtimer.timer.formatCountdown
import dev.wolsey.windowtimer.timer.markMillis
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
    onOpenJobs: () -> Unit
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
    val counts = rows.toCounts()
    val interiorCount = Breakdown.totalInterior(counts)
    val exteriorCount = Breakdown.totalExterior(counts)
    val total = if (running) serviceTotal else totalMillis(counts, types, crew)
    val canStart = total > 0

    val resetChecks = {
        TimerService.resetFired()
        fired = 0
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
            CrewChips(
                people = people,
                selectedIds = crewIds,
                onChange = {
                    onCrewChange(it)
                    resetChecks()
                },
                enabled = !running
            )
            SavedCrewChips(people, crewIds, onCrewChange, enabled = !running)
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
            text = if (canStart) {
                val crewText = if (crew.size > 1) ", ${crew.size} people" else ""
                "Estimated: ${formatClock(total)}  " +
                        "($interiorCount interior, $exteriorCount exterior$crewText)"
            } else "Enter window counts",
            style = MaterialTheme.typography.bodyLarge
        )

        val overtime = running && elapsed > total

        Text(
            text = when {
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

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            QUARTERS.forEachIndexed { index, fraction ->
                val done = fired > index
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${if (done) "✓" else "·"}  ${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium
                    )
                    val markMs = markMillis(total, fraction)
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
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, TimerService::class.java)
                            .setAction(TimerService.ACTION_START)
                            .putExtra(TimerService.EXTRA_TOTAL_MS, total)
                            .putExtra(TimerService.EXTRA_JOB_NAME, jobName.trim())
                            .putExtra(TimerService.EXTRA_INTERIOR, interiorCount)
                            .putExtra(TimerService.EXTRA_EXTERIOR, exteriorCount)
                            .putExtra(TimerService.EXTRA_BREAKDOWN, Breakdown.encode(counts))
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
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep running") }
            }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    if (showFinish) {
        FinishFlow(
            planned = counts,
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
            },
            onDismiss = { showFinish = false }
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