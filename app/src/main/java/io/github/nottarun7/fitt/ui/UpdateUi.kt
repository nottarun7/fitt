package io.github.nottarun7.fitt.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nottarun7.fitt.BuildConfig
import io.github.nottarun7.fitt.updates.*

@Composable
fun UpdatePromptHost(model: UpdateViewModel, automaticAllowed: Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (automaticAllowed) model.check() }
    val update = state.available
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    if (state.showPrompt && update != null) UpdatePrompt(update, onLater = model::later, onSkip = model::skip,
        onDownload = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))) }
                .onSuccess { model.later() }.onFailure { error = "No browser is available. Open the GitHub Releases page on another device." }
        }, error = error)
}

@Composable
fun UpdatePrompt(update: AvailableUpdate, onLater: () -> Unit, onSkip: () -> Unit, onDownload: () -> Unit, error: String? = null) {
    AlertDialog(onDismissRequest = onLater, title = { Text("Fitt ${update.versionName} is available") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Download the APK from GitHub, then open it to update Fitt. Android asks you to confirm the update. Your saved data stays in the app.")
            if (update.notes.isNotBlank()) Text(update.notes.take(600), style = MaterialTheme.typography.bodySmall)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { TextButton(onDownload, Modifier.testTag("downloadUpdate")) { Text("Update") } },
        dismissButton = { Row { TextButton(onSkip) { Text("Skip version") }; TextButton(onLater) { Text("Later") } } })
}

@Composable
fun UpdateSettings(model: UpdateViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    FittCard {
        Text("App updates", style = MaterialTheme.typography.titleLarge)
        Text("Installed: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Check automatically", Modifier.weight(1f))
            Switch(state.automatic, model::automatic, modifier = Modifier.testTag("automaticUpdates"))
        }
        Text("Checks GitHub once a day when you open Fitt. GitHub receives a normal connection request and app version; your workouts, meals and photos are never sent. Core features work offline.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton({ model.check(manual = true) }, enabled = !state.checking, modifier = Modifier.fillMaxWidth().testTag("checkUpdates")) {
            Text(if (state.checking) "Checking…" else "Check for updates")
        }
        state.message?.let { Text(it, Modifier.testTag("updateStatus"), style = MaterialTheme.typography.bodySmall) }
        state.available?.let { update ->
            Button({ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))) }.onFailure { error = "No browser is available." } }, Modifier.fillMaxWidth()) { Text("Update to ${update.versionName}") }
        }
        TextButton({ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))) }.onFailure { error = "No browser is available." } }) { Text("GitHub downloads & release notes") }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    }
}
