package dev.wolsey.windowtimer.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.alerts.AlertConfig
import dev.wolsey.windowtimer.alerts.AlertSettings
import dev.wolsey.windowtimer.alerts.Alerts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun AlertEditor(index: Int, config: AlertConfig, onChange: (AlertConfig) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = listOf(
        "Early alerts", "Middle alerts", "Late alerts", "Final alert", "Part complete"
    )[index]
    var testing by remember { mutableStateOf(false) }
    var testJob by remember { mutableStateOf<Job?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // Copying the file can take a moment, so keep it off the UI thread.
                val name = withContext(Dispatchers.IO) {
                    AlertSettings.importSound(context, index, uri)
                }
                if (name != null) {
                    onChange(config.copy(customName = name))
                } else {
                    Toast.makeText(
                        context,
                        "Couldn't use that file (must be audio, under 10 MB)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onChange(config.copy(enabled = it)) }
                )
            }

            if (config.enabled) {
                if (config.customName != null) {
                    // Custom sound file in use.
                    Text(
                        "Sound: ${config.customName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose different file")
                    }
                    OutlinedButton(
                        onClick = {
                            AlertSettings.deleteSound(context, index)
                            onChange(config.copy(customName = null))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use beeps instead")
                    }
                } else {
                    // Generated beeps.
                    SettingSlider(
                        label = "Pitch: ${config.pitchHz} Hz",
                        value = config.pitchHz.toFloat(),
                        range = AlertSettings.PITCH_MIN.toFloat()..AlertSettings.PITCH_MAX.toFloat(),
                        steps = 0,
                        onChange = { onChange(config.copy(pitchHz = ((it / 10).roundToInt() * 10))) }
                    )
                    SettingSlider(
                        label = "Beeps: ${config.beeps}",
                        value = config.beeps.toFloat(),
                        range = AlertSettings.BEEPS_MIN.toFloat()..AlertSettings.BEEPS_MAX.toFloat(),
                        steps = AlertSettings.BEEPS_MAX - AlertSettings.BEEPS_MIN - 1,
                        onChange = { onChange(config.copy(beeps = it.roundToInt())) }
                    )
                    SettingSlider(
                        label = "Beep length: ${config.beepMs} ms",
                        value = config.beepMs.toFloat(),
                        range = AlertSettings.LENGTH_MIN.toFloat()..AlertSettings.LENGTH_MAX.toFloat(),
                        steps = 0,
                        onChange = { onChange(config.copy(beepMs = ((it / 50).roundToInt() * 50))) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rising pitch")
                        Switch(
                            checked = config.rising,
                            onCheckedChange = { onChange(config.copy(rising = it)) }
                        )
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose sound file")
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (testing) {
                            Alerts.stop()
                            testJob?.cancel()
                            testing = false
                        } else {
                            Alerts.play(context, index, config)
                            testing = true
                            testJob = scope.launch {
                                // Flip back to "Test" once the sound has finished on its own.
                                delay(Alerts.durationMs(context, index, config) + 200)
                                testing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (testing) "Stop" else "Test")
                }
            }
        }
    }
}