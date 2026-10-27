package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nottarun7.fitt.catalogue.ExerciseCatalogue
import io.github.nottarun7.fitt.data.*

@Composable
fun WorkoutScreen(active: SessionWithExercises?, loading: Boolean, model: WorkoutViewModel, busy: Boolean, saving: Boolean,
                  onBack: () -> Unit, onFinished: (String) -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    var finish by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var options by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val editing = remember(active?.session?.id) { mutableStateMapOf<String, Boolean>() }
    val enteringSet = WindowInsets.ime.getBottom(LocalDensity.current) > 0 || editing.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        PageHeader("In progress", if (saving) "Saving changes…" else "Your session is saved", onBack, action = {
            if (active != null && !loading) {
                if (enteringSet) {
                    TextButton({ focusManager.clearFocus(); keyboard?.hide() },
                        Modifier.testTag("doneEnteringSet").semantics { contentDescription = "Done entering set" }) { Text("Done") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ finish = true }, Modifier.testTag("finishWorkout"),
                            enabled = !busy && !saving && active.setCount > 0) { Text("Finish") }
                        Box {
                            IconButton({ options = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "Workout options") }
                            DropdownMenu(options, { options = false }) {
                                DropdownMenuItem(text = { Text("Discard workout", color = MaterialTheme.colorScheme.error) },
                                    onClick = { options = false; discard = true }, modifier = Modifier.testTag("discardWorkout"))
                            }
                        }
                    }
                }
            }
        })
        if (loading) CircularProgressIndicator(Modifier.padding(24.dp))
        else if (active == null) Box(Modifier.padding(16.dp)) { EmptyMessage("No active workout", "Start a routine or a freestyle workout from Train.") }
        else {
            LazyColumn(Modifier.weight(1f).testTag("workoutList"), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    FittCard {
                        Text(active.session.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Started ${formatDate(active.session.startedAt)}", style = MaterialTheme.typography.bodySmall)
                        Text("${countLabel(active.setCount, "set")} logged", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        val planned = active.exercises.sumOf { it.exercise.targetSets }
                        if (planned > 0) LinearProgressIndicator(progress = { (active.setCount.toFloat() / planned).coerceAtMost(1f) }, Modifier.fillMaxWidth())
                    }
                }
                itemsIndexed(active.orderedExercises, key = { _, row -> row.exercise.id }) { index, row ->
                    ActiveExerciseCard(row, index + 1, model, busy) { source, focused ->
                        val key = "${row.exercise.id}:$source"
                        if (focused) editing[key] = true else editing.remove(key)
                    }
                }
                if (active.exercises.isEmpty()) item { EmptyMessage("Build this workout", "Add an exercise from the library or create your own. Your session is already saved for later.") }
                item {
                    OutlinedButton({ picker = true }, Modifier.fillMaxWidth().testTag("addSessionExercise"), enabled = !busy) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add exercise")
                    }
                }
            }
        }
    }
    if (picker && active != null) ExercisePicker(onDismiss = { picker = false }, enabled = !busy, onChoose = { choice ->
        model.addSessionExercise(active.session.id, choice) { picker = false }
    })
    if (finish && active != null) AlertDialog(onDismissRequest = { finish = false }, title = { Text("Finish workout?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your workout and logged sets will be kept in history. You can finish even if some targets are unfinished.")
                if (active.exercises.any { it.draft.value.isNotBlank() || it.draft.weight.isNotBlank() }) {
                    Text("Unlogged entries will not be added. Choose Keep training and log the set first.",
                        fontWeight = FontWeight.Medium, modifier = Modifier.testTag("unloggedSetWarning"))
                }
            }
        },
        confirmButton = { TextButton({ model.finishWorkout(active.session.id) { finish = false; onFinished(active.session.id) } },
            enabled = !busy && !saving, modifier = Modifier.testTag("confirmFinishWorkout")) { Text("Finish and save") } },
        dismissButton = { TextButton({ finish = false }, enabled = !busy) { Text("Keep training") } })
    if (discard && active != null) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard workout?") },
        text = { Text("This removes this unfinished session and all of its sets. Your routine is kept.") },
        confirmButton = { TextButton({ model.discardWorkout(active.session.id) { discard = false; onBack() } }, enabled = !busy) { Text("Discard workout") } },
        dismissButton = { TextButton({ discard = false }, enabled = !busy) { Text("Keep training") } })
}

@Composable
private fun ActiveExerciseCard(row: SessionExerciseWithSets, number: Int, model: WorkoutViewModel, busy: Boolean,
    onEditingChange: (String, Boolean) -> Unit) {
    val exercise = row.exercise
    val draft = row.draft
    var input by rememberSaveable(exercise.id, stateSaver = SetInputSaver) {
        mutableStateOf(SetInput(draft.value, draft.weight, draft.unit, draft.assistance))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(exercise.id) {
        onDispose { onEditingChange("entry", false); onEditingChange("dialog", false) }
    }
    LaunchedEffect(editingId) { onEditingChange("dialog", editingId != null) }
    FittCard(Modifier.testTag("sessionExercise-${exercise.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$number. ${exercise.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${row.sets.size} / ${exercise.targetSets} sets · target ${exercise.targetValue} ${metricLabel(exercise.metric, exercise.perSide).lowercase()}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ExerciseCatalogue.find(exercise.catalogueId) != null) TextButton({ showGuide = true }) { Text("Guide") }
        }
        row.orderedSets.forEachIndexed { index, set ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", Modifier.padding(end = 14.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(setDescription(set, exercise), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton({ editingId = set.id }, Modifier.testTag("editSet-${set.id}"), enabled = !busy) {
                    Icon(Icons.Default.Edit, "Edit set ${index + 1} of ${exercise.name}")
                }
            }
        }
        if (row.sets.isNotEmpty()) HorizontalDivider()
        Column(Modifier.onFocusChanged { onEditingChange("entry", it.hasFocus) }.focusGroup()) {
            SetEntryFields(input, {
                input = it; error = null; model.saveDraft(exercise.id, it)
            }, exercise.metric, exercise.perSide, exercise.id, enabled = !busy, error = error)
        }
        Button({
            val invalid = runCatching { input.validated() }.exceptionOrNull()
            if (invalid != null) error = invalid.message else {
                focusManager.clearFocus(); keyboard?.hide()
                model.logSet(exercise.id, input)
            }
        }, Modifier.fillMaxWidth().testTag("logSet-${exercise.id}"), enabled = !busy) { Text("Log set") }
    }
    row.sets.find { it.id == editingId }?.let { set ->
        EditSetDialog(set, exercise, model, busy, onDismiss = { editingId = null })
    }
    if (showGuide) ExerciseCatalogue.find(exercise.catalogueId)?.let { entry ->
        var guideId by rememberSaveable { mutableStateOf(entry.id) }
        ExerciseCatalogue.find(guideId)?.let { shown ->
            ExerciseDetails(shown, onDismiss = { showGuide = false }, onSelectSibling = { guideId = it })
        }
    }
}

@Composable
private fun EditSetDialog(set: SetEntity, exercise: SessionExerciseEntity, model: WorkoutViewModel, busy: Boolean, onDismiss: () -> Unit) {
    var input by rememberSaveable(set.id, stateSaver = SetInputSaver) {
        mutableStateOf(SetInput(set.value.toString(), set.weight?.let(::displayNumber).orEmpty(), set.unit, set.assistance))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Edit set") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            SetEntryFields(input, { input = it; error = null }, exercise.metric, exercise.perSide, "edit", !busy, error)
            TextButton({ model.deleteSet(set.id, onDismiss) }, enabled = !busy) { Text("Delete set", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({
        val invalid = runCatching { input.validated() }.exceptionOrNull()
        if (invalid != null) error = invalid.message else model.editSet(set.id, input, onDismiss)
    }, enabled = !busy, modifier = Modifier.testTag("saveSetChanges")) { Text("Save changes") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancel") } })
}
