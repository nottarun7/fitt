package io.github.nottarun7.fitt.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.nottarun7.fitt.catalogue.*
import io.github.nottarun7.fitt.data.*

@Composable
fun CatalogueScreen(onChoose: ((ExerciseChoice) -> Unit)? = null, onDismiss: (() -> Unit)? = null, enabled: Boolean = true) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var muscle by rememberSaveable { mutableStateOf("All") }
    var family by rememberSaveable { mutableStateOf("All") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var custom by rememberSaveable { mutableStateOf(false) }
    val matches = remember(query, category, muscle, family) { ExerciseCatalogue.search(query, category, muscle, family) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize()) {
        PageHeader(if (onChoose != null) "Choose an exercise" else "Exercise library",
            "${ExerciseCatalogue.all.size} movements, always available offline", onBack = onDismiss)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("exerciseSearch"),
                placeholder = { Text("Search exercise, muscle, or equipment") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp),
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear search") } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterSelect("Equipment", category, listOf("All", "Machines", "Cables", "Calisthenics", "Free weights"),
                    { category = it; if (it != "Calisthenics" && it != "All") family = "All" }, Modifier.weight(1f))
                FilterSelect("Muscle", muscle, listOf("All", "Chest", "Back", "Shoulders", "Biceps", "Triceps", "Quads", "Hamstrings", "Glutes", "Calves", "Core"),
                    { muscle = it }, Modifier.weight(1f))
            }
            if (category == "Calisthenics") FilterSelect("Progression", family, listOf("All") + ExerciseCatalogue.families, { family = it }, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${matches.size} exercises", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onChoose != null) TextButton({ custom = true }, enabled = enabled, modifier = Modifier.testTag("customExercise")) {
                    Icon(Icons.Default.Add, null); Text("Custom exercise")
                }
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("catalogueList"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            if (matches.isEmpty()) item { EmptyMessage("No matches", "Try a different search or reset the filters. You can also add a custom exercise to a routine or workout.") }
            items(matches, key = { it.id }) { entry ->
                Column(Modifier.fillMaxWidth().clickable(enabled = enabled) {
                    focus.clearFocus(); keyboard?.hide(); selectedId = entry.id
                }.testTag("exercise-${entry.id}").padding(horizontal = 4.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(entry.muscles, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (entry.metric == TrackingMetric.SECONDS) Text("Timed", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Icon(FittIcons.Forward, null, Modifier.padding(start = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(entry.family?.let { "$it · step ${entry.step} · ${entry.equipment.label}" } ?: entry.equipment.label,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    ExerciseCatalogue.find(selectedId)?.let { entry ->
        ExerciseDetails(entry, onDismiss = { selectedId = null }, onSelectSibling = { selectedId = it },
            onChoose = onChoose, enabled = enabled)
    }
    if (custom) CustomExerciseDialog(query, onDismiss = { custom = false }, onChoose = {
        onChoose?.invoke(it); custom = false
    })
}

@Composable
private fun FilterSelect(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(if (selected == "All") "$label: all" else selected)
        }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 360.dp)) {
            options.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

@Composable
fun ExercisePicker(onDismiss: () -> Unit, onChoose: (ExerciseChoice) -> Unit, enabled: Boolean = true) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding()) { CatalogueScreen(onChoose, onDismiss, enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetails(entry: ExerciseDefinition, onDismiss: () -> Unit, onSelectSibling: (String) -> Unit,
                    onChoose: ((ExerciseChoice) -> Unit)? = null, enabled: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    var linkError by remember(entry.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.88f), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onDismiss) { Icon(Icons.Default.Close, "Close exercise details") }
                }
            }
            item {
                Text(entry.muscles, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("${entry.equipment.label} · ${metricLabel(entry.metric.name, entry.perSide)}", style = MaterialTheme.typography.bodyMedium)
            }
            item { Text(entry.notes, style = MaterialTheme.typography.bodyLarge) }
            if (onChoose != null) item {
                Button({ onChoose(entry.choice()) }, Modifier.fillMaxWidth().testTag("chooseExercise"), enabled = enabled) { Text("Add exercise") }
            }
            entry.referenceUrl?.let { url -> item {
                val publisher = when {
                    "acefitness.org" in url -> "ACE"
                    "hybridcalisthenics.com" in url -> "Hybrid Calisthenics"
                    else -> "GMB Fitness"
                }
                OutlinedButton({
                    try { uriHandler.openUri(url) }
                    catch (_: ActivityNotFoundException) { linkError = true }
                    catch (_: IllegalArgumentException) { linkError = true }
                }, Modifier.fillMaxWidth()) { Text("Open movement guide · $publisher") }
                Text("Opens the publisher's website in your browser.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (linkError) Text("No browser could open this link.", color = MaterialTheme.colorScheme.error)
            } }
            entry.family?.let { family ->
                item {
                    Text("$family progressions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Suggested variations, not automatic advancement. Pick a level that fits your current ability.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(ExerciseCatalogue.progression(family), key = { it.id }) { sibling ->
                    Surface(Modifier.fillMaxWidth().clickable { onSelectSibling(sibling.id) }, shape = RoundedCornerShape(14.dp),
                        color = if (sibling.id == entry.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${sibling.step}", Modifier.padding(end = 16.dp), style = MaterialTheme.typography.titleMedium)
                            Column {
                                Text(sibling.name, fontWeight = if (sibling.id == entry.id) FontWeight.Bold else FontWeight.Normal)
                                Text(metricLabel(sibling.metric.name, sibling.perSide), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomExerciseDialog(initialName: String, onDismiss: () -> Unit, onChoose: (ExerciseChoice) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var metric by rememberSaveable { mutableStateOf(TrackingMetric.REPS.name) }
    var perSide by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Custom exercise") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.testTag("customExerciseName"), label = { Text("Exercise name") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackingMetric.entries.forEach { value -> FilterChip(metric == value.name, { metric = value.name }, label = { Text(value.label) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(perSide, { perSide = it }); Text("Reps or time are per side")
            }
        }
    }, confirmButton = { TextButton({ onChoose(ExerciseChoice(null, name.trim(), TrackingMetric.valueOf(metric), perSide)) },
        enabled = name.isNotBlank(), modifier = Modifier.testTag("confirmCustomExercise")) { Text("Add exercise") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
