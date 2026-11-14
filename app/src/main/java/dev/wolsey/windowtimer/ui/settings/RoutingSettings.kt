package dev.wolsey.windowtimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.RoutingSettings

/** The key used to put the day's stops in driving order. Saved as you type. */
@Composable
fun RoutingSection() {
    val context = LocalContext.current
    var key by remember { mutableStateOf(RoutingSettings.apiKey(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Routing", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ordering stops uses OpenRouteService, which is free. Get a key at openrouteservice.org " +
                    "(sign up, then request a token) and paste it here. Addresses are sent to " +
                    "that service to find them on the map and work out driving times.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = key,
            onValueChange = {
                key = it.trim().take(200)
                RoutingSettings.saveApiKey(context, key)
            },
            label = { Text("OpenRouteService API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}