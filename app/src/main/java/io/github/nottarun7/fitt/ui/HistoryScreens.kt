package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nottarun7.fitt.data.SessionWithExercises

@Composable
fun HistoryScreen(state: WorkoutOverview, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Workout history", "Completed sessions")
        LazyColumn(Modifier.weight(1f).testTag("historyList"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            if (state.loading) item { CircularProgressIndicator() }
            if (state.error != null) item { EmptyMessage("Storage unavailable", state.error) }
            if (!state.loading && state.error == null && state.history.isEmpty()) item {
                EmptyMessage("No completed workouts yet", "Finish a workout to see its exercises, sets, loads, and time here.")
            }
            if (state.history.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    HistoryMetric(state.history.size.toString(), "Sessions")
                    HistoryMetric(state.history.sumOf { it.setCount }.toString(), "Sets logged")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            itemsIndexed(state.history, key = { _, row -> row.session.id }) { index, row ->
                Column(Modifier.fillMaxWidth().testTag("history-${row.session.id}")
                    .clickable(onClickLabel = "View workout") { onOpen(row.session.id) }.padding(vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(formatDate(row.session.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("${countLabel(row.exerciseCount, "exercise")} · ${countLabel(row.setCount, "set")} · ${formatDuration(row.session.startedAt, row.session.completedAt!!)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index < state.history.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(session: SessionWithExercises?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Workout details", onBack = onBack)
        if (session == null) CircularProgressIndicator(Modifier.padding(24.dp))
        else LazyColumn(Modifier.weight(1f).testTag("historyDetail"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                FittCard {
                    Text(session.session.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(formatDate(session.session.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val ended = session.session.completedAt
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        HistoryMetric(session.setCount.toString(), "Sets logged", Modifier.weight(1f))
                        HistoryMetric(ended?.let { formatDuration(session.session.startedAt, it) } ?: "In progress", "Duration", Modifier.weight(1f))
                    }
                }
            }
            items(session.orderedExercises, key = { it.exercise.id }) { row ->
                FittCard {
                    Text(row.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Target ${row.exercise.targetSets} × ${row.exercise.targetValue} ${metricLabel(row.exercise.metric, row.exercise.perSide).lowercase()}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (row.sets.isEmpty()) Text("No sets recorded", style = MaterialTheme.typography.bodyMedium)
                    if (row.sets.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    row.orderedSets.forEachIndexed { index, set ->
                        Text("${index + 1}.  ${setDescription(set, row.exercise)}", Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item {
                Text("Exercise names and targets are saved with this session. Editing its routine does not change this history.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HistoryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
