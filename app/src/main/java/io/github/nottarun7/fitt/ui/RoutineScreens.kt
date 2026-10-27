package io.github.nottarun7.fitt.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nottarun7.fitt.data.*

@Composable
fun TrainScreen(state: WorkoutOverview, busy: Boolean, onNew: () -> Unit,
                onEdit: (RoutineWithExercises) -> Unit, onDelete: (String) -> Unit,
                onStart: (String?) -> Unit, onResume: () -> Unit, onBackup: () -> Unit, onSettings: () -> Unit) {
    var deleting by remember { mutableStateOf<RoutineWithExercises?>(null) }
    Column {
        PageHeader("Fitt", "Your training log", action = { Row {
            TextButton(onBackup, enabled = !busy) { Text("Backups") }
            IconButton(onSettings, enabled = !busy) { Icon(Icons.Default.Settings, "Settings") }
        } })
        LazyColumn(Modifier.testTag("trainList"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.loading) item { CircularProgressIndicator() }
            if (state.error != null) item { EmptyMessage("Storage unavailable", state.error) }
            if (!state.loading && state.error == null) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, if (state.active != null) MaterialTheme.colorScheme.primary.copy(alpha = .5f) else MaterialTheme.colorScheme.outlineVariant),
                        shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.active != null) {
                                Text("Active session", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(state.active.session.name, style = MaterialTheme.typography.headlineSmall)
                                Text("${countLabel(state.active.setCount, "set")} logged · started ${formatDate(state.active.session.startedAt)}")
                                Button(onResume, Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("resumeWorkout"), shape = MaterialTheme.shapes.small) { Text("Resume workout") }
                                Text("Your session is saved. Continue where you left off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Start a workout", style = MaterialTheme.typography.headlineMedium)
                                Text("Use a routine below, or build a session as you train.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button({ onStart(null) }, Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("freestyleWorkout"), shape = MaterialTheme.shapes.small, enabled = !busy) {
                                    Icon(FittIcons.Training, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Start freestyle workout")
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(state.history.size.toString(), style = MaterialTheme.typography.headlineMedium)
                            Text("Workouts completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(state.history.sumOf { it.setCount }.toString(), style = MaterialTheme.typography.headlineMedium)
                            Text("Sets logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your routines", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onNew, Modifier.testTag("newRoutine"), enabled = !busy) { Icon(Icons.Default.Add, null); Text("New routine") }
                    }
                }
                if (state.routines.isEmpty()) item {
                    EmptyMessage("No routines yet", "Create a routine with your exercises and targets, then reuse it each session.")
                }
                items(state.routines, key = { it.routine.id }) { routine ->
                    FittCard(Modifier.testTag("routine-${routine.routine.id}")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(routine.routine.name, style = MaterialTheme.typography.titleMedium)
                                Text("${countLabel(routine.exercises.size, "exercise")} · ${routine.exercises.sumOf { it.targetSets }} planned sets",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton({ deleting = routine }, enabled = !busy) { Icon(Icons.Default.Delete, "Delete ${routine.routine.name}") }
                        }
                        if (routine.exercises.isNotEmpty()) Text(routine.orderedExercises.take(3).joinToString("  /  ") { it.name },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton({ onEdit(routine) }, Modifier.weight(1f), shape = MaterialTheme.shapes.small, enabled = !busy) { Text("Edit routine") }
                            Button({ onStart(routine.routine.id) }, Modifier.weight(1f).testTag("start-${routine.routine.id}"),
                                shape = MaterialTheme.shapes.small, enabled = !busy && state.active == null && routine.exercises.isNotEmpty()) { Text("Start workout") }
                        }
                        if (routine.exercises.isEmpty()) Text("Add an exercise to start this routine.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    deleting?.let { routine ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete routine?") },
            text = { Text("Delete ${routine.routine.name}? Your active workout and workout history will be kept.") },
            confirmButton = { TextButton({ onDelete(routine.routine.id); deleting = null }) { Text("Delete routine") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } })
    }
}

@Composable
fun RoutineEditorScreen(editor: EditorState?, model: WorkoutViewModel, busy: Boolean, onBack: () -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val leave = { if (editor?.dirty == true) confirmLeave = true else { model.discardEditor(); onBack() } }
    BackHandler(onBack = leave)
    Column(Modifier.fillMaxSize()) {
        PageHeader(if (editor?.key == "new") "New routine" else "Edit routine", "Set it up your way", onBack = leave)
        if (editor == null) {
            CircularProgressIndicator(Modifier.padding(24.dp))
        } else {
            val draft = editor.draft
            LazyColumn(Modifier.weight(1f).testTag("routineEditorList"), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    OutlinedTextField(draft.name, model::updateRoutineName, Modifier.fillMaxWidth().testTag("routineName"),
                        label = { Text("Routine name") }, placeholder = { Text("e.g. Upper body A") }, singleLine = true,
                        shape = RoundedCornerShape(14.dp), enabled = !busy)
                }
                item { Text(countLabel(draft.exercises.size, "exercise"), style = MaterialTheme.typography.titleMedium) }
                itemsIndexed(draft.exercises, key = { _, row -> row.id }) { index, row ->
                    FittCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(row.choice.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(metricLabel(row.choice.metric.name, row.choice.perSide), style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ model.removeRoutineExercise(row.id) }, enabled = !busy) {
                                Icon(Icons.Default.Delete, "Remove ${row.choice.name}")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(row.targetSets, { model.updateTargets(row.id, it, row.targetValue) }, Modifier.weight(1f),
                                label = { Text("Target sets") }, singleLine = true, enabled = !busy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                            OutlinedTextField(row.targetValue, { model.updateTargets(row.id, row.targetSets, it) }, Modifier.weight(1f),
                                label = { Text(metricLabel(row.choice.metric.name, row.choice.perSide)) }, singleLine = true, enabled = !busy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton({ model.moveExercise(row.id, -1) }, enabled = !busy && index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, "Move ${row.choice.name} up")
                            }
                            IconButton({ model.moveExercise(row.id, 1) }, enabled = !busy && index < draft.exercises.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, "Move ${row.choice.name} down")
                            }
                        }
                    }
                }
                item {
                    OutlinedButton({ picker = true }, Modifier.fillMaxWidth().testTag("addRoutineExercise"), enabled = !busy) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add exercise")
                    }
                }
                if (draft.exercises.isEmpty()) item {
                    Text("Browse machines, cables, free weights, and calisthenics progressions, or add your own exercise.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button({ model.saveRoutine(onBack) }, Modifier.fillMaxWidth().padding(16.dp).testTag("saveRoutine"), enabled = !busy) {
                Text(if (busy) "Saving…" else "Save routine")
            }
        }
    }
    if (picker) ExercisePicker(onDismiss = { picker = false }, onChoose = {
        model.addRoutineExercise(it); picker = false
    })
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("Leave without saving?") },
        text = { Text("Your changes to this routine have not been saved.") },
        confirmButton = { TextButton({ confirmLeave = false; model.discardEditor(); onBack() }) { Text("Discard changes") } },
        dismissButton = { TextButton({ confirmLeave = false }) { Text("Keep editing") } })
}
