package dev.wolsey.windowtimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.data.TypePace
import dev.wolsey.windowtimer.data.TypePaces
import dev.wolsey.windowtimer.data.db.Person
import dev.wolsey.windowtimer.data.db.WindowType
import dev.wolsey.windowtimer.data.paceFor
import dev.wolsey.windowtimer.data.store.PersonStore
import dev.wolsey.windowtimer.data.store.TypeStore
import dev.wolsey.windowtimer.data.typePaceMap
import kotlinx.coroutines.launch

/** The people who can work a job, each with their own pace for every window type. */
@Composable
fun PeopleSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val people by remember { PersonStore.people(context) }.collectAsState(initial = emptyList())

    var editing by remember { mutableStateOf<Person?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("People", style = MaterialTheme.typography.titleMedium)
        Text(
            "Each person's pace in windows per hour. Set Standard here, and optionally a pace " +
                    "for any other window type. A crew's estimate adds their paces together.",
            style = MaterialTheme.typography.bodySmall
        )

        people.forEach { person ->
            val custom = person.typePaceMap().size
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${person.interiorPace}/hr interior · ${person.exteriorPace}/hr exterior" +
                                if (custom > 0) " · $custom custom type${if (custom > 1) "s" else ""}" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { editing = person }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${person.name}")
                }
                if (!person.isOwner) {
                    IconButton(onClick = { scope.launch { PersonStore.delete(context, person) } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${person.name}")
                    }
                }
            }
        }

        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Add person")
        }
    }

    @Suppress("AssignedValueIsNeverRead")
    if (adding) {
        EditPersonDialog(
            initial = null,
            onSave = { PersonStore.save(context, it) },
            onDismiss = { adding = false }
        )
    }

    @Suppress("AssignedValueIsNeverRead")
    editing?.let { person ->
        EditPersonDialog(
            initial = person,
            onSave = { PersonStore.save(context, it) },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun EditPersonDialog(
    initial: Person?,
    onSave: suspend (Person) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val types by remember { TypeStore.types(context) }.collectAsState(initial = emptyList())
    val standard = types.firstOrNull { it.isStandard }
    val otherTypes = types.filter { !it.isStandard }

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var interior by remember { mutableStateOf((initial?.interiorPace ?: 24).toString()) }
    var exterior by remember { mutableStateOf((initial?.exteriorPace ?: 22).toString()) }
    var nameTaken by remember { mutableStateOf(false) }

    // Per-type boxes, keyed by type id. Blank means "scale from Standard".
    val typeInterior = remember {
        mutableStateMapOf<Long, String>().apply {
            initial?.typePaceMap()?.forEach { (id, p) -> p.interior?.let { put(id, it.toString()) } }
        }
    }
    val typeExterior = remember {
        mutableStateMapOf<Long, String>().apply {
            initial?.typePaceMap()?.forEach { (id, p) -> p.exterior?.let { put(id, it.toString()) } }
        }
    }

    // What a blank box would use, worked out from the Standard paces currently typed in.
    val preview = Person(
        name = "",
        interiorPace = interior.toIntOrNull() ?: initial?.interiorPace ?: 24,
        exteriorPace = exterior.toIntOrNull() ?: initial?.exteriorPace ?: 22
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add person" else "Edit person") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30); nameTaken = false },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameTaken,
                    supportingText = if (nameTaken) {
                        { Text("Someone with that name already exists") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Standard windows", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = interior,
                        onValueChange = { interior = it.filter(Char::isDigit).take(2) },
                        label = { Text("Interior/hr") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = exterior,
                        onValueChange = { exterior = it.filter(Char::isDigit).take(2) },
                        label = { Text("Exterior/hr") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (standard != null && otherTypes.isNotEmpty()) {
                    Text("Other window types", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Leave a box empty to scale from this person's Standard pace; the grey " +
                                "number shows what that would be.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    otherTypes.forEach { type ->
                        Text(type.name, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = typeInterior[type.id].orEmpty(),
                                onValueChange = { typeInterior[type.id] = it.filter(Char::isDigit).take(2) },
                                label = { Text("Interior/hr") },
                                placeholder = {
                                    Text("%.0f".format(autoPace(preview, type, true, standard)))
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = typeExterior[type.id].orEmpty(),
                                onValueChange = { typeExterior[type.id] = it.filter(Char::isDigit).take(2) },
                                label = { Text("Exterior/hr") },
                                placeholder = {
                                    Text("%.0f".format(autoPace(preview, type, false, standard)))
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
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

                    // Keep saved paces for types that aren't shown (e.g. none loaded yet).
                    val kept = initial?.typePaceMap().orEmpty().filterKeys { id -> otherTypes.none { it.id == id } }
                    val edited = otherTypes.associate { type ->
                        type.id to TypePace(
                            interior = typeInterior[type.id]?.toIntOrNull()
                                ?.coerceIn(WindowType.MIN_PACE, WindowType.MAX_PACE),
                            exterior = typeExterior[type.id]?.toIntOrNull()
                                ?.coerceIn(WindowType.MIN_PACE, WindowType.MAX_PACE)
                        )
                    }

                    val person = (initial ?: Person("", 0, 0)).copy(
                        name = name.trim(),
                        interiorPace = interiorPace,
                        exteriorPace = exteriorPace,
                        typePaces = TypePaces.encode(kept + edited)
                    )
                    scope.launch {
                        @Suppress("AssignedValueIsNeverRead")
                        if (onSave(person)) onDismiss() else nameTaken = true
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** The pace a person would get for [type] with nothing saved, scaled from their Standard pace. */
private fun autoPace(person: Person, type: WindowType, interior: Boolean, standard: WindowType): Double =
    person.copy(typePaces = "").paceFor(type, interior, standard)