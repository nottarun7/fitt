package io.github.nottarun7.fitt.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nottarun7.fitt.interchange.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BackupScreen(model: BackupViewModel, onBack: () -> Unit, onGuide: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var pendingPhotoExport by rememberSaveable { mutableStateOf(true) }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFileFormat.JSON.mime)) {
        if (it != null) model.export(it, BackupFileFormat.JSON)
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFileFormat.CSV.mime)) {
        if (it != null) model.export(it, BackupFileFormat.CSV)
    }
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFileFormat.ZIP.mime)) {
        if (it != null) model.export(it, BackupFileFormat.ZIP, pendingPhotoExport)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) model.read(it) }
    BackupContent(state, onBack = onBack, onExport = { format ->
        val name = "fitt-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))}.${format.extension}"
        when (format) { BackupFileFormat.JSON -> json.launch(name); BackupFileFormat.CSV -> csv.launch(name); BackupFileFormat.ZIP -> { pendingPhotoExport = state.includePhotos; zip.launch(name) } }
    }, onPick = { open.launch(arrayOf("*/*")) }, onOptions = model::options, onApply = model::applyImport, onGuide = onGuide, onPhotos = model::includePhotos)
}

/** File operations are injected so the full preview/confirmation flow is testable without a provider UI. */
@Composable
fun BackupContent(state: BackupUiState, onBack: () -> Unit, onExport: (BackupFileFormat) -> Unit,
                  onPick: () -> Unit, onOptions: (ImportOptions) -> Unit, onApply: () -> Unit, onGuide: (() -> Unit)? = null,
                  onPhotos: (Boolean) -> Unit = {}) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable(state.sourceName) { mutableStateOf(false) }
    var otherFormats by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = state.busy) { /* Complete the current atomic operation before leaving. */ }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Backups & files", "Your data, wherever you choose", onBack = { if (!state.busy) onBack() })
        LazyColumn(Modifier.weight(1f).testTag("backupList"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                FittCard {
                    Text("Save a backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Keep your routines, workouts, unfinished input, nutrition and photos together. Save editor changes first.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(state.includePhotos, onPhotos, enabled = !state.busy, modifier = Modifier.testTag("includePhotos"))
                        Text("Include private photos in ZIP (${state.photoCount})")
                    }
                    Button({ onExport(BackupFileFormat.ZIP) }, Modifier.fillMaxWidth().testTag("export-ZIP"), enabled = !state.busy) {
                        Text("Save backup (ZIP)")
                    }
                    TextButton({ otherFormats = !otherFormats }, enabled = !state.busy, modifier = Modifier.testTag("otherExportFormats")) {
                        Text(if (otherFormats) "Hide other formats" else "Other formats")
                    }
                    if (otherFormats) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(BackupFileFormat.JSON, BackupFileFormat.CSV).forEach { format ->
                            OutlinedButton({ onExport(format) }, Modifier.weight(1f).testTag("export-${format.name}"), enabled = !state.busy) { Text(format.name) }
                        }
                        }
                        Text("JSON and CSV contain data only. Restoring them preserves existing photos.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Backups can contain personal photos and health data. Files are not password-protected. Save them somewhere private.", style = MaterialTheme.typography.bodySmall)
                    Text("Notification permission and reminder opt-in stay on this device.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                FittCard {
                    Text("Import or restore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Choose a Fitt JSON, CSV, or ZIP file. Review validation, duplicates, and the planned changes before importing.")
                    Text("For a PDF or document, use the fitt.plan v1 conversion guide and import the resulting JSON.")
                    Button(onPick, Modifier.fillMaxWidth().testTag("chooseBackup"), enabled = !state.busy) { Text("Choose backup file") }
                    if (onGuide != null) TextButton(onGuide, enabled = !state.busy, modifier = Modifier.testTag("backupImportGuide")) { Text("Import guide & template") }
                    Text("Generic CSV layouts are not supported. ZIP: 256 MiB, up to 200 photos at 5 MiB each. JSON/CSV: 12 MiB; each document: 10 MiB.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.busy) item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp)); Text(state.operation)
                }
            }
            if (state.message != null) item {
                FittCard(Modifier.testTag("backupMessage")) {
                    Text(state.message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    state.result?.let { ImportCounts(it) }
                }
            }
            if (state.issues.isNotEmpty()) item {
                FittCard(Modifier.testTag("backupErrors")) {
                    Text("Operation not completed", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                    state.issues.take(10).forEach { Text("${it.path}: ${it.message}", style = MaterialTheme.typography.bodyMedium) }
                    if (state.issues.size > 10) Text("${state.issues.size - 10} more validation errors.")
                    Text("A rejected import does not change your saved data.", style = MaterialTheme.typography.bodySmall)
                }
            }
            val document = state.decoded?.document
            if (document != null) {
                item {
                    FittCard {
                        Text(state.sourceName ?: "Selected backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${countLabel(document.routines.size, "routine")} · ${countLabel(document.workouts.count { it.completedAtEpochMillis != null }, "finished workout")} · ${document.workouts.count { it.completedAtEpochMillis == null }} unfinished")
                        Text("${countLabel(document.nutrition.foods.size, "food")} · ${countLabel(document.nutrition.meals.size, "meal")} · ${countLabel(document.nutrition.schedules.size, "diet schedule")}")
                        Text(if (document.photoScope) "${document.photos.size} private photos in this backup" else "Data only · existing photos will be preserved, including during exact restore.")
                        if (state.decoded.isPlan) Text("Plan import · no completed activity will be created", style = MaterialTheme.typography.bodySmall)
                        else Text("Exported ${formatDate(document.exportedAtEpochMillis)}", style = MaterialTheme.typography.bodySmall)
                        state.decoded.notices.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                item {
                    FittCard {
                        Text("How to import", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        BackupChoice("Merge with this device", if (state.decoded.isPlan) "Keep unrelated workouts, foods, meals, and plans. Daily reminder time stays unchanged."
                            else "Keep unrelated workouts, foods, meals, and plans. Reminder time changes only with Use imported.", state.options.mode == ImportMode.MERGE, !state.busy) {
                            onOptions(state.options.copy(mode = ImportMode.MERGE))
                        }
                        if (!state.decoded.isPlan) BackupChoice("Restore backup exactly", "Replace workout and nutrition data. An older workout-only backup removes nutrition data. ${if (document.photoScope) "Replace private photos too; photos absent from this backup are removed." else "Keep existing private photos."} Phone-gallery copies stay untouched.",
                            state.options.mode == ImportMode.RESTORE_EXACTLY, !state.busy, "exactRestore") {
                            onOptions(state.options.copy(mode = ImportMode.RESTORE_EXACTLY))
                        }
                    }
                }
                if (state.options.mode == ImportMode.MERGE) item {
                    FittCard {
                        Text("Changed duplicate IDs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Records match by saved ID. Matching names alone stay separate. Identical records are always skipped.", style = MaterialTheme.typography.bodySmall)
                        listOf(
                            Triple(DuplicatePolicy.KEEP_EXISTING, "Keep existing", "Leave matching records on this device unchanged."),
                            Triple(DuplicatePolicy.USE_IMPORTED, "Use imported", "Replace each changed duplicate with the entire imported record."),
                            Triple(DuplicatePolicy.KEEP_BOTH, "Keep both", "Add copies with new IDs and preserve the existing records."),
                        ).forEach { (policy, title, detail) ->
                            BackupChoice(title, detail, state.options.duplicates == policy, !state.busy, "duplicate-${policy.name}") {
                                onOptions(state.options.copy(duplicates = policy))
                            }
                        }
                    }
                }
                val plan = state.plan
                if (plan != null) {
                    if (plan.summary.activeConflict) item {
                        FittCard {
                            Text("Two unfinished workouts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            BackupChoice("Keep current workout", "Skip the unfinished workout from this file. Finished history can still import.",
                                state.options.activeWorkout == ActiveWorkoutPolicy.KEEP_CURRENT, !state.busy) {
                                onOptions(state.options.copy(activeWorkout = ActiveWorkoutPolicy.KEEP_CURRENT))
                            }
                            BackupChoice("Use imported workout", "Remove the current unfinished workout and its sets, then restore the imported one.",
                                state.options.activeWorkout == ActiveWorkoutPolicy.USE_IMPORTED, !state.busy, "useImportedActive") {
                                onOptions(state.options.copy(activeWorkout = ActiveWorkoutPolicy.USE_IMPORTED))
                            }
                        }
                    }
                    item {
                        FittCard(Modifier.testTag("importPreview")) {
                            Text("Planned changes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            ImportCounts(plan.summary)
                            if (plan.summary.remappedChildIds > 0) Text("${plan.summary.remappedChildIds} exercise/set IDs will be adjusted to avoid collisions.", style = MaterialTheme.typography.bodySmall)
                            if (state.photoRepairs > 0) Text("${countLabel(state.photoRepairs, "photo file")} will be installed or repaired from this ZIP.")
                            if (!plan.hasChanges && state.photoRepairs == 0) Text("No changes to import. Your saved records already match the selected choices.", color = MaterialTheme.colorScheme.primary)
                            TextButton({ onOptions(state.options) }, enabled = !state.busy) { Text("Refresh preview") }
                            Button({ confirm = true }, Modifier.fillMaxWidth().testTag("applyBackup"),
                                enabled = !state.busy && state.issues.isEmpty() && (plan.hasChanges || state.photoRepairs > 0)) {
                                Text(if (state.options.mode == ImportMode.RESTORE_EXACTLY) "Review exact restore" else "Review import")
                            }
                        }
                    }
                    if (plan.duplicates.isNotEmpty()) {
                        item { Text("Changed duplicate records", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        items(if (showAll) plan.duplicates else plan.duplicates.take(10), key = { "${it.kind}:${it.id}" }) { duplicate ->
                            FittCard {
                                val unit = when (duplicate.kind) { "routine" -> "exercise"; "workout" -> "set"; "food" -> "food"; "photo" -> "photo"; else -> "portion" }
                                Text("Existing: ${duplicate.currentName}", fontWeight = FontWeight.SemiBold)
                                Text("${countLabel(duplicate.currentCount, unit)} → ${countLabel(duplicate.incomingCount, unit)}")
                                Text("Imported: ${duplicate.incomingName}")
                                Text("Matched ${duplicate.kind} ID: ${duplicate.id}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (!showAll && plan.duplicates.size > 10) item {
                            TextButton({ showAll = true }) { Text("Show all ${plan.duplicates.size} duplicates") }
                        }
                    }
                }
            }
        }
    }
    if (confirm && state.plan != null) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(if (state.options.mode == ImportMode.RESTORE_EXACTLY) "Replace device data?" else "Import these records?") },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.options.mode == ImportMode.RESTORE_EXACTLY) "This restores exactly what is in the selected backup, including its workout and nutrition data. Records not in the backup will be removed. Reminder permission and opt-in remain local."
                else "Apply the reviewed duplicate and unfinished-workout choices. Each routine, workout, food, meal, diet schedule, or photo is imported as a whole.")
            ImportCounts(state.plan.summary)
            Text(if (state.decoded?.document?.photoScope == true) "Private photos follow these choices. Restored photos stay private; phone-gallery copies are untouched."
                else "This file preserves existing private photos. Phone-gallery copies are untouched.")
            if (state.photoRepairs > 0) Text("Install or repair ${countLabel(state.photoRepairs, "photo file")}.")
        } },
        confirmButton = { TextButton({ confirm = false; onApply() }, enabled = !state.busy, modifier = Modifier.testTag("confirmBackup")) {
            Text(if (state.options.mode == ImportMode.RESTORE_EXACTLY) "Replace and restore" else "Import now")
        } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}

@Composable
private fun ImportCounts(summary: ImportSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Routines: ${summary.addedRoutines} added · ${summary.changedRoutines} changed · ${summary.removedRoutines} removed")
        Text("Workouts: ${summary.addedWorkouts} added · ${summary.changedWorkouts} changed · ${summary.removedWorkouts} removed")
        listOf("Foods" to summary.foods, "Meals" to summary.meals, "Diet schedules" to summary.schedules, "Private photos" to summary.photos).forEach { (label, changes) ->
            Text("$label: ${changes.added} added · ${changes.changed} changed · ${changes.removed} removed")
        }
        if (summary.reminderTimeChanged) Text("Reminder time will change. Notification consent stays on this device.")
        Text("${summary.identicalDuplicates} identical · ${summary.changedDuplicates} changed duplicates · ${summary.skippedRecords} incoming records skipped",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackupChoice(title: String, detail: String, selected: Boolean, enabled: Boolean, tag: String = title, onChoose: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onChoose).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
