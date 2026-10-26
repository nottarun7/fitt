package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nottarun7.fitt.data.AppPreferences
import io.github.nottarun7.fitt.data.ThemeMode

@Composable
fun SettingsScreen(preferences: AppPreferences, onBack: () -> Unit, onHealth: () -> Unit, onGuide: () -> Unit,
    updates: io.github.nottarun7.fitt.updates.UpdateViewModel? = null) {
    val mode by preferences.theme.collectAsStateWithLifecycle()
    Column {
        PageHeader("Settings", onBack = onBack)
        LazyColumn(Modifier.testTag("settingsList"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (updates != null) item { UpdateSettings(updates) }
            item {
                FittCard {
                    Text("Appearance", style = MaterialTheme.typography.titleLarge)
                    Text("Choose a theme for the whole app. System follows your phone's setting.")
                    ThemeMode.entries.forEach { choice ->
                        Row(Modifier.fillMaxWidth().testTag("theme-${choice.name}").selectable(mode == choice, role = Role.RadioButton,
                            onClick = { preferences.theme(choice) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(mode == choice, onClick = null)
                            Text(choice.label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
            item {
                FittCard {
                    Text("Health Connect", style = MaterialTheme.typography.titleLarge)
                    Text("Optionally share completed workouts and logged meals, and see today's steps. Connect and grant only the access you want.")
                    Button(onHealth, Modifier.fillMaxWidth().testTag("openHealthConnect")) { Text("Health Connect") }
                }
            }
            item {
                FittCard {
                    Text("Bring your own plan", style = MaterialTheme.typography.titleLarge)
                    Text("Use an external LLM to convert a PDF or document into Fitt's plan format, then review and import it.")
                    OutlinedButton(onGuide, Modifier.fillMaxWidth().testTag("openImportGuide")) { Text("Import guide & template") }
                }
            }
        }
    }
}
