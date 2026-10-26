package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nottarun7.fitt.data.SessionWithExercises

@Composable
fun HistoryScreen(state: WorkoutOverview, onOpen: (String) -> Unit) {
    Column {
        PageHeader("Workout history", "Your work, kept on your device")
        LazyColumn(Modifier.testTag("historyList"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.loading) item { CircularProgressIndicator() }
            if (state.error != null) item { EmptyMessage("Storage unavailable", state.error) }
            if (!state.loading && state.error == null && state.history.isEmpty()) item {
                EmptyMessage("The first of many", "Finish a workout to see its exercises, sets, loads, and time here.")
            }
            items(state.history, key = { it.session.id }) { row ->
                FittCard(Modifier.clickable { onOpen(row.session.id) }.testTag("history-${row.session.id}")) {
                    Text(formatDate(row.session.startedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(row.session.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${countLabel(row.exerciseCount, "exercise")} · ${countLabel(row.setCount, "set")} · ${formatDuration(row.session.startedAt, row.session.completedAt!!)}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("View workout", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(session: SessionWithExercises?, onBack: () -> Unit) {
    Column {
        PageHeader("Workout details", onBack = onBack)
        if (session == null) CircularProgressIndicator(Modifier.padding(24.dp))
        else LazyColumn(Modifier.testTag("historyDetail"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                FittCard {
                    Text(session.session.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(formatDate(session.session.startedAt))
                    val ended = session.session.completedAt
                    Text("${countLabel(session.setCount, "set")}${ended?.let { " · ${formatDuration(session.session.startedAt, it)}" }.orEmpty()}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Saved exercise names and targets reflect this workout, even after the routine changes.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(session.orderedExercises, key = { it.exercise.id }) { row ->
                FittCard {
                    Text(row.exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Target ${row.exercise.targetSets} × ${row.exercise.targetValue} ${metricLabel(row.exercise.metric, row.exercise.perSide).lowercase()}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (row.sets.isEmpty()) Text("No sets recorded", style = MaterialTheme.typography.bodyMedium)
                    row.orderedSets.forEachIndexed { index, set ->
                        Text("${index + 1}.  ${setDescription(set, row.exercise)}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
