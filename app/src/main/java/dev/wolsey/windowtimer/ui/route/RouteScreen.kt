package dev.wolsey.windowtimer.ui.route

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.wolsey.windowtimer.data.RoutingSettings
import dev.wolsey.windowtimer.data.ShopSettings
import dev.wolsey.windowtimer.data.routing.LatLng
import dev.wolsey.windowtimer.data.routing.RoutePlanner
import dev.wolsey.windowtimer.data.routing.RoutingException
import dev.wolsey.windowtimer.data.store.GeoStore
import dev.wolsey.windowtimer.data.store.JobStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Maps links take one destination plus up to 9 waypoints. */
private const val MAX_STOPS = 10

@Composable
fun RouteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // A live view of the saved jobs; it updates by itself whenever they change.
    val jobs by remember { JobStore.jobs(context) }.collectAsState(initial = emptyList())

    // The route is a list of job names, kept in order and across rotation.
    val route = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf<String>() }

    val shopAddress = remember { ShopSettings.load(context) }

    // NEW: ordering options and the state of the last plan.
    var autoOrder by remember { mutableStateOf(RoutingSettings.autoOrder(context)) }
    var returnToShop by remember { mutableStateOf(RoutingSettings.returnToShop(context)) }
    var planning by remember { mutableStateOf(false) }
    var planMessage by remember { mutableStateOf<String?>(null) }
    var driveSeconds by remember { mutableStateOf<Double?>(null) }
    var unplaced by remember { mutableStateOf<List<String>>(emptyList()) }
    val places by remember { GeoStore.places(context) }.collectAsState(initial = emptyList())
    var fixing by remember { mutableStateOf<String?>(null) }   // address being checked
    var refresh by remember { mutableIntStateOf(0) }           // bump to plan the route again

    BackHandler(onBack = onBack)

    // Positions in `route` of the stops that still exist. A renamed or deleted job leaves a
    // stale name behind, so button actions must use the route position, not the list position.
    val stopIndices = route.indices.filter { i ->
        jobs.any { it.name.equals(route[i], ignoreCase = true) }
    }
    val stops = stopIndices.map { i -> jobs.first { it.name.equals(route[i], ignoreCase = true) } }
    val available = jobs.filter { job ->
        job.address.isNotBlank() && route.none { it.equals(job.name, ignoreCase = true) }
    }
    val withoutAddress = jobs.filter { it.address.isBlank() }

    fun placeFor(address: String) = places.firstOrNull { it.address == GeoStore.key(address) }

    // A spot you set yourself is what Maps navigates to, not the address text.
    fun navTarget(address: String): String =
        placeFor(address)?.takeIf { it.manual }?.let { "${it.lat},${it.lng}" } ?: address

    val shopPoint = placeFor(shopAddress)?.let { LatLng(it.lat, it.lng) }

    // NEW: puts the stops in driving order and rewrites the route.
    suspend fun orderStops() {
        if (stops.size < 2) {
            driveSeconds = null
            unplaced = emptyList()
            return
        }
        planning = true
        planMessage = null
        try {
            val result = RoutePlanner.plan(context, shopAddress, stops, returnToShop)
            val ordered = (result.ordered + result.unplaced).map { it.name }
            // Stale names stay out; the route becomes exactly the planned order.
            route.clear()
            route.addAll(ordered)
            driveSeconds = result.driveSeconds
            unplaced = result.unplaced.map { it.name }
        } catch (e: RoutingException) {
            planMessage = e.message
            driveSeconds = null
        } finally {
            planning = false
        }
    }

    // NEW: re-order by itself when the set of stops, their addresses or the options change.
    // Putting the stops in a different order doesn't change the key, so this can't loop.
    val stopKey = stops.map { it.name.lowercase() + "|" + it.address.trim().lowercase() }.sorted()
    // Spots you set yourself change the route too; automatic lookups don't, or planning
    // would trigger itself.
    val manualKey = places.filter { it.manual }.map { "${it.address}|${it.lat}|${it.lng}" }.sorted()
    LaunchedEffect(stopKey, returnToShop, autoOrder, manualKey, refresh) {
        if (autoOrder) orderStops()
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
            Row {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Route", style = MaterialTheme.typography.headlineMedium)
            }
            OutlinedButton(
                onClick = { openInMaps(context, shopAddress) },
                enabled = shopAddress.isNotBlank()
            ) { Text("Shop") }
        }

        if (shopAddress.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (val shop = placeFor(shopAddress)) {
                        null -> "Shop location not looked up yet"
                        else -> if (shop.manual) "Shop: location set by you"
                        else "Shop found: ${shop.label.ifBlank { "%.4f, %.4f".format(shop.lat, shop.lng) }}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { fixing = shopAddress }) { Text("Location") }
            }
        }

        Text("Today's stops", style = MaterialTheme.typography.titleMedium)

        // NEW: ordering options.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Order stops automatically", modifier = Modifier.weight(1f))
            Switch(
                checked = autoOrder,
                onCheckedChange = {
                    autoOrder = it
                    RoutingSettings.saveAutoOrder(context, it)
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("End back at the shop", modifier = Modifier.weight(1f))
            Switch(
                checked = returnToShop,
                onCheckedChange = {
                    returnToShop = it
                    RoutingSettings.saveReturnToShop(context, it)
                }
            )
        }

        if (stops.isEmpty()) {
            Text("No stops yet. Add jobs from the list below.")
        } else {
            stops.forEachIndexed { index, job ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.titleLarge)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(job.name, style = MaterialTheme.typography.titleMedium)
                            Text(job.address, style = MaterialTheme.typography.bodySmall)
                            placeFor(job.address)?.let { place ->
                                Text(
                                    if (place.manual) "Location set by you"
                                    else "Found: ${place.label.ifBlank { "%.4f, %.4f".format(place.lat, place.lng) }}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row {
                                TextButton(
                                    onClick = { openInMaps(context, navTarget(job.address)) },
                                    enabled = job.address.isNotBlank()
                                ) { Text("Navigate") }
                                TextButton(
                                    onClick = { fixing = job.address },
                                    enabled = job.address.isNotBlank()
                                ) { Text("Location") }
                            }
                        }
                        // NEW: with automatic ordering the app decides the order, so the
                        // manual arrows only show when it's off.
                        if (!autoOrder) {
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    route.add(stopIndices[index - 1], route.removeAt(stopIndices[index]))
                                }
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(
                                enabled = index < stops.lastIndex,
                                onClick = {
                                    route.add(stopIndices[index + 1], route.removeAt(stopIndices[index]))
                                }
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                        IconButton(onClick = { route.removeAt(stopIndices[index]) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove from route")
                        }
                    }
                }
            }

            // NEW: what the last plan found.
            if (planning) Text("Planning route…", style = MaterialTheme.typography.bodySmall)
            planMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            driveSeconds?.let {
                Text(
                    "Driving time about ${formatDrive(it)}" +
                            if (returnToShop) " (round trip from the shop)" else " (from the shop)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (unplaced.isNotEmpty()) {
                Text(
                    "Couldn't find on the map, so left at the end: ${unplaced.joinToString()}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!autoOrder) {
                OutlinedButton(
                    onClick = { scope.launch { orderStops() } },
                    enabled = !planning && stops.size > 1,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Order stops now") }
            }

            if (stops.size > MAX_STOPS) {
                Text(
                    "Maps supports up to $MAX_STOPS stops. Remove ${stops.size - MAX_STOPS}.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            // NEW: after a plan, Maps starts from the shop and can finish there.
            val fromShop = driveSeconds != null && shopAddress.isNotBlank()
            val addresses = stops.map { navTarget(it.address) } +
                    if (fromShop && returnToShop) listOf(navTarget(shopAddress)) else emptyList()

            Row {
                OutlinedButton(
                    onClick = { route.clear() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("Clear route", fontSize = 18.sp)
                }
                Button(
                    onClick = {
                        openInMaps(context, addresses, origin = navTarget(shopAddress).takeIf { fromShop })
                    },
                    enabled = addresses.size <= MAX_STOPS,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Open route in Maps", fontSize = 18.sp)
                }
            }

            Text(
                if (fromShop) "Starts at the shop" + if (returnToShop) " and ends there." else " and ends at the last stop."
                else "Starts from your current location and ends at the last stop.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text("Add a job", style = MaterialTheme.typography.titleMedium)

        if (available.isEmpty()) {
            Text(
                if (jobs.isEmpty()) "No saved jobs yet."
                else "Every job with an address is already on the route."
            )
        } else {
            available.forEach { job ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(job.name, style = MaterialTheme.typography.titleMedium)
                        Text(job.address, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Navigate",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (job.address.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier
                                .clickable(enabled = job.address.isNotBlank()) {
                                    openInMaps(
                                        context,
                                        job.address
                                    )
                                }
                                .padding(vertical = 6.dp)
                        )
                    }
                    OutlinedButton(onClick = { route.add(job.name) }) { Text("Add") }
                }
            }
        }

        if (withoutAddress.isNotEmpty()) {
            Text(
                "No address (add one on the timer screen and save the job again): " +
                        withoutAddress.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall
            )
        }

        fixing?.let { address ->
            LocationDialog(
                title = if (address == shopAddress) "Shop location"
                else jobs.firstOrNull { it.address == address }?.name ?: "Location",
                address = address,
                place = placeFor(address),
                near = if (address == shopAddress) null else shopPoint,
                onChanged = { refresh++ },
                onDismiss = { fixing = null }
            )
        }
    }
}

/** "1 h 12 min" or "48 min". */
private fun formatDrive(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt()
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

/**
 * Opens Google Maps with directions through every address, in order. With an [origin] the
 * route starts there; otherwise it starts from the phone's current location.
 */
private fun openInMaps(context: Context, addresses: List<String>, origin: String? = null) {
    if (addresses.isEmpty()) return

    val builder = "https://www.google.com/maps/dir/".toUri().buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("travelmode", "driving")
        .appendQueryParameter("destination", addresses.last())
    if (origin != null) builder.appendQueryParameter("origin", origin)
    if (addresses.size > 1) {
        builder.appendQueryParameter("waypoints", addresses.dropLast(1).joinToString("|"))
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, builder.build()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open the route", Toast.LENGTH_LONG).show()
    }
}

/** Opens Google Maps with directions to one address. */
private fun openInMaps(context: Context, address: String) {
    if (address.isBlank()) return
    openInMaps(context, listOf(address))
}