package dev.wolsey.windowtimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.db.WindowType
import dev.wolsey.windowtimer.data.store.TypeStore
import kotlinx.coroutines.launch

/** Lists the window types, each with its own interior and exterior pace. */
@Composable
fun WindowTypesSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val types by remember { TypeStore.types(context) }.collectAsState(initial = emptyList())

    var editing by remember { mutableStateOf<WindowType?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Window types", style = MaterialTheme.typography.titleMedium)
        Text(
            "Each type has its own pace in windows per hour.",
            style = MaterialTheme.typography.bodySmall
        )

        types.forEach { type ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(type.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${type.interiorPace}/hr interior · ${type.exteriorPace}/hr exterior" +
                                if (type.isStandard) " · reference pace" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { editing = type }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${type.name}")
                }
                if (!type.isStandard) {
                    IconButton(onClick = { scope.launch { TypeStore.delete(context, type) } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${type.name}")
                    }
                }
            }
        }

        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Add type")
        }
    }

    @Suppress("AssignedValueIsNeverRead")
    if (adding) {
        EditTypeDialog(
            initial = null,
            onSave = { TypeStore.save(context, it) },
            onDismiss = { adding = false }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    editing?.let { type ->
        EditTypeDialog(
            initial = type,
            onSave = { TypeStore.save(context, it) },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun EditTypeDialog(
    initial: WindowType?,
    onSave: suspend (WindowType) -> Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var interior by remember { mutableStateOf((initial?.interiorPace ?: 24).toString()) }
    var exterior by remember { mutableStateOf((initial?.exteriorPace ?: 22).toString()) }
    var nameTaken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add window type" else "Edit window type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30); nameTaken = false },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameTaken,
                    supportingText = if (nameTaken) {
                        { Text("A type with that name already exists") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = interior,
                    onValueChange = { interior = it.filter(Char::isDigit).take(2) },
                    label = { Text("Interior windows per hour") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = exterior,
                    onValueChange = { exterior = it.filter(Char::isDigit).take(2) },
                    label = { Text("Exterior windows per hour") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    // An empty or out-of-range box is clamped rather than rejected.
                    val interiorPace = (interior.toIntOrNull() ?: initial?.interiorPace ?: 24)
                        .coerceIn(WindowType.MIN_PACE, WindowType.MAX_PACE)
                    val exteriorPace = (exterior.toIntOrNull() ?: initial?.exteriorPace ?: 22)
                        .coerceIn(WindowType.MIN_PACE, WindowType.MAX_PACE)
                    val type = (initial ?: WindowType("", 0, 0)).copy(
                        name = name.trim(),
                        interiorPace = interiorPace,
                        exteriorPace = exteriorPace
                    )
                    scope.launch {
                        @Suppress("AssignedValueIsNeverRead")
                        if (onSave(type)) onDismiss() else nameTaken = true
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}