package dev.wolsey.windowtimer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.alerts.AlertSettings

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configs = remember {
        mutableStateListOf(*AlertSettings.load(context).toTypedArray())
    }

    // System back gesture/button returns to the timer.
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        ShopAddressSection()

        RoutingSection()

        WindowTypesSection()

        PeopleSection()

        CrewsSection()

        IntervalsSection()

        configs.forEachIndexed { index, config ->
            AlertEditor(
                index = index,
                config = config,
                onChange = { updated ->
                    configs[index] = updated
                    AlertSettings.save(context, index, updated)
                }
            )
        }

        OutlinedButton(
            onClick = {
                AlertSettings.defaults.forEachIndexed { index, default ->
                    AlertSettings.deleteSound(context, index)
                    configs[index] = default
                    AlertSettings.save(context, index, default)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset alerts to defaults")
        }
    }
}