package dev.wolsey.windowtimer.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.TypeCount
import dev.wolsey.windowtimer.data.db.WindowType

enum class WindowKind { INTERIOR, EXTERIOR }

/** One window type's text boxes on the timer screen. */
data class CountRow(
    val typeId: Long,
    val name: String,
    val interior: String = "",
    val exterior: String = ""
)

/** Lets the rows survive rotation and process death. */
val CountRowsSaver: Saver<List<CountRow>, Any> = listSaver<List<CountRow>, List<Any>>(
    save = { rows -> rows.map { listOf(it.typeId, it.name, it.interior, it.exterior) } },
    restore = { saved ->
        saved.map { CountRow(it[0] as Long, it[1] as String, it[2] as String, it[3] as String) }
    }
)

fun List<CountRow>.toCounts(): List<TypeCount> = map {
    TypeCount(it.typeId, it.name, it.interior.toIntOrNull() ?: 0, it.exterior.toIntOrNull() ?: 0)
}

fun List<TypeCount>.toRows(): List<CountRow> = map {
    CountRow(
        it.typeId,
        it.name,
        if (it.interior > 0) it.interior.toString() else "",
        if (it.exterior > 0) it.exterior.toString() else ""
    )
}

/** An interior and exterior box (each with a Count button) for every window type on the job. */
@Composable
fun TypeCountRows(
    rows: List<CountRow>,
    types: List<WindowType>,
    onRowsChange: (List<CountRow>) -> Unit,
    enabled: Boolean,
    onCount: ((typeId: Long, kind: WindowKind) -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            val label = types.firstOrNull { it.id == row.typeId }?.name ?: row.name
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (enabled && rows.size > 1) {
                        IconButton(onClick = { onRowsChange(rows - row) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove $label")
                        }
                    }
                }
                CountField(
                    label = "Interior windows",
                    value = row.interior,
                    onValueChange = { v ->
                        onRowsChange(rows.map { if (it.typeId == row.typeId) it.copy(interior = v) else it })
                    },
                    enabled = enabled,
                    onCount = onCount?.let { callback -> { callback(row.typeId, WindowKind.INTERIOR) } }
                )
                CountField(
                    label = "Exterior windows",
                    value = row.exterior,
                    onValueChange = { v ->
                        onRowsChange(rows.map { if (it.typeId == row.typeId) it.copy(exterior = v) else it })
                    },
                    enabled = enabled,
                    onCount = onCount?.let { callback -> { callback(row.typeId, WindowKind.EXTERIOR) } }                )
            }
        }

        val available = types.filter { t -> rows.none { it.typeId == t.id } }
        if (enabled && available.isNotEmpty()) {
            Box {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add window type")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    available.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name) },
                            onClick = {
                                onRowsChange(rows + CountRow(type.id, type.name))
                                menuOpen = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onCount: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter(Char::isDigit).take(3)) },
            label = { Text(label) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        if (onCount != null) {
            OutlinedButton(onClick = onCount, enabled = enabled) { Text("Count") }
        }
    }
}