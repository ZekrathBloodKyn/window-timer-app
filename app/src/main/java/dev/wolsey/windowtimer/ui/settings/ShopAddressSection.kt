package dev.wolsey.windowtimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.ShopSettings

/** The address the Route screen's Shop button navigates to. Saved as you type. */
@Composable
fun ShopAddressSection() {
    val context = LocalContext.current
    var address by remember { mutableStateOf(ShopSettings.load(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Shop", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = address,
            onValueChange = {
                address = it.take(120)
                ShopSettings.save(context, address)
            },
            label = { Text("Shop address") },
            maxLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
    }
}