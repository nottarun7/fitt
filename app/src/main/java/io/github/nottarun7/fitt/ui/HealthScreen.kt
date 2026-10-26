package io.github.nottarun7.fitt.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nottarun7.fitt.health.*
import io.github.nottarun7.fitt.health.HealthPrivacyActivity

@Composable
fun HealthScreen(model: HealthViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { model.refresh() }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }
    var intentError by remember { mutableStateOf<String?>(null) }
    fun open(intent: Intent) { try { context.startActivity(intent) } catch (_: Exception) { intentError = "This action is not available on this device." } }
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        owner.lifecycle.addObserver(observer); model.refresh()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(state.busy) { }
    Column {
        PageHeader("Health Connect", "Optional sharing, controlled by you", onBack = { if (!state.busy) onBack() })
        LazyColumn(Modifier.weight(1f).testTag("healthList"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                FittCard {
                    Text(when (state.availability) {
                        HealthConnectClient.SDK_AVAILABLE -> "Health Connect is available"
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Install or update Health Connect"
                        HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect is unavailable on this device"
                        else -> "Checking availability…"
                    }, style = MaterialTheme.typography.titleLarge)
                    Text("Fitt stays usable offline whether you connect or not. Nothing is shared until you choose Share now.")
                    if (state.availability == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) OutlinedButton({
                        open(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                    }) { Text("Get Health Connect") }
                    if (state.availability == HealthConnectClient.SDK_AVAILABLE) {
                        Text("Write exercise sessions: ${if (HealthAccess.exercise in state.granted) "allowed" else "not allowed"}")
                        Text("Write nutrition: ${if (HealthAccess.nutrition in state.granted) "allowed" else "not allowed"}")
                        Text("Read today's steps: ${if (HealthAccess.steps in state.granted) "allowed" else "not allowed"}")
                        Button({
                            intentError = null
                            try { request.launch(HealthAccess.all) }
                            catch (_: Exception) { intentError = "Could not open Health Connect permissions. Check its installation and try again."; model.refresh() }
                        }, enabled = !state.busy, modifier = Modifier.testTag("connectHealth")) { Text("Connect / change access") }
                        TextButton({ open(HealthConnectClient.getHealthConnectManageDataIntent(context)) }, enabled = !state.busy) { Text("Manage Health Connect") }
                    }
                    TextButton({ open(Intent(context, HealthPrivacyActivity::class.java)) }) { Text("How Fitt uses health data") }
                }
            }
            if (state.availability == HealthConnectClient.SDK_AVAILABLE) {
                item {
                    FittCard {
                        Text("Today's steps", style = MaterialTheme.typography.titleLarge)
                        Text(when { HealthAccess.steps !in state.granted -> "Allow step access to see today's total."
                            state.steps == null -> "No step data is available for today."
                            else -> "${state.steps} steps" })
                        OutlinedButton(model::refresh, enabled = !state.busy) { Text("Refresh access & steps") }
                        Text("Health Connect aggregates sources; this total is displayed here and is not copied into Fitt backups.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    FittCard {
                        Text("Share Fitt entries", style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.workouts, model::workouts, enabled = !state.busy && HealthAccess.exercise in state.granted,
                                modifier = Modifier.semantics { contentDescription = "Share completed workouts" })
                            Text("Completed workouts")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.meals, model::meals, enabled = !state.busy && HealthAccess.nutrition in state.granted,
                                modifier = Modifier.semantics { contentDescription = "Share logged meals" })
                            Text("Logged meals")
                        }
                        Text("Workouts share their duration and title as strength training; detailed sets stay in Fitt. Meals share calories and macros. Because meals store a date, Health Connect uses midnight local time as a date placeholder.")
                        Text("Templates, diet schedules, and unfinished workouts are not shared. Future dates, zero-duration workouts, and values outside Health Connect limits are skipped. Time-zone offsets use this device's current zone.", style = MaterialTheme.typography.bodySmall)
                        Button({ sharing = true }, Modifier.fillMaxWidth().testTag("shareHealth"), enabled = !state.busy &&
                            ((state.workouts && HealthAccess.exercise in state.granted) || (state.meals && HealthAccess.nutrition in state.granted))) { Text("Review sharing") }
                        Text("Sharing again updates entries with stable IDs. Deleting something in Fitt does not remove a previously shared copy; use Remove shared data to clear Fitt's copies.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    FittCard {
                        Text("Remove shared data", style = MaterialTheme.typography.titleLarge)
                        Text("Remove Fitt's workout and nutrition copies from Health Connect for the types you allow. Your local Fitt entries stay saved.")
                        OutlinedButton({ removing = true }, enabled = !state.busy && (HealthAccess.exercise in state.granted || HealthAccess.nutrition in state.granted),
                            modifier = Modifier.testTag("removeHealth")) { Text("Remove Fitt's shared data") }
                    }
                }
            }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.message != null || intentError != null) item { FittCard { Text(intentError ?: state.message!!, Modifier.testTag("healthMessage")) } }
        }
    }
    if (sharing) AlertDialog(onDismissRequest = { sharing = false }, title = { Text("Share selected Fitt entries?") },
        text = { Text("Share or update completed workouts and logged meals for the selected types with permission. Other apps allowed in Health Connect may be able to read them.") },
        confirmButton = { TextButton({ sharing = false; model.share() }, enabled = !state.busy) { Text("Share now") } },
        dismissButton = { TextButton({ sharing = false }) { Text("Cancel") } })
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text("Remove Fitt's Health Connect copies?") },
        text = { Text("This removes all Fitt exercise-session and nutrition records in Health Connect for currently allowed write types. It does not remove local Fitt data or records written by other apps.") },
        confirmButton = { TextButton({ removing = false; model.removeShared() }, enabled = !state.busy) { Text("Remove shared copies") } },
        dismissButton = { TextButton({ removing = false }) { Text("Cancel") } })
}
