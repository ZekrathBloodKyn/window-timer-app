package dev.wolsey.windowtimer.ui.route

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.core.net.toUri
import dev.wolsey.windowtimer.data.db.GeoPlace
import dev.wolsey.windowtimer.data.routing.LatLng
import dev.wolsey.windowtimer.data.routing.RoutingException
import dev.wolsey.windowtimer.data.routing.parseCoordinates
import dev.wolsey.windowtimer.data.store.GeoStore
import kotlinx.coroutines.launch

/**
 * Shows where the app thinks [address] is, and lets you correct it. [place] is null until
 * the address has been looked up. [onChanged] is called after anything that moves a stop,
 * so the route can be planned again.
 */
@Composable
fun LocationDialog(
    title: String,
    address: String,
    place: GeoPlace?,
    near: LatLng?,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var search by remember { mutableStateOf(address) }
    var coordinates by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Address: $address", style = MaterialTheme.typography.bodySmall)

                if (place == null) {
                    Text("Not looked up yet. It's found when the route is next planned, or look it up below.")
                } else {
                    Text(
                        if (place.manual) "You set this location."
                        else "Found: ${place.label.ifBlank { "(no name returned)" }}"
                    )
                    Text("%.5f, %.5f".format(place.lat, place.lng), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        val uri = "https://www.google.com/maps/search/?api=1&query=${place.lat},${place.lng}".toUri()
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "No app found to show the map", Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Show on map") }
                }

                HorizontalDivider()

                Text("Search again", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it.take(120) },
                    label = { Text("Search for") },
                    supportingText = { Text("Try a corrected address or a nearby business. The job's address isn't changed.") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !busy && search.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            try {
                                val found = GeoStore.lookup(context, address, search, near)
                                if (found == null) {
                                    message = "Nothing found for that search."
                                } else {
                                    message = "Location updated."
                                    onChanged()
                                }
                            } catch (e: RoutingException) {
                                message = e.message
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Looking up…" else "Look up") }

                HorizontalDivider()

                Text("Set the exact spot", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = coordinates,
                    onValueChange = { coordinates = it.take(60) },
                    label = { Text("Latitude, longitude") },
                    placeholder = { Text("40.1149, -111.6549") },
                    supportingText = {
                        Text("In Google Maps, press and hold on the spot, then tap the numbers to copy them.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = coordinates.isNotBlank(),
                    onClick = {
                        val point = parseCoordinates(coordinates)
                        if (point == null) {
                            message = "That doesn't look like a latitude and longitude."
                        } else {
                            scope.launch {
                                GeoStore.setManual(context, address, point)
                                coordinates = ""
                                message = "Location set."
                                onChanged()
                            }
                        }
                    }
                ) { Text("Use these coordinates") }

                if (place != null) {
                    TextButton(onClick = {
                        scope.launch {
                            GeoStore.clear(context, address)
                            message = "Will be looked up again."
                            onChanged()
                        }
                    }) { Text(if (place.manual) "Go back to automatic" else "Forget and look up again") }
                }

                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}