package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nottarun7.fitt.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PageHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        action?.invoke()
    }
}

@Composable
fun FittCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun EmptyMessage(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun metricLabel(metric: String, perSide: Boolean = false): String =
    (if (metric == TrackingMetric.SECONDS.name) "Seconds" else "Reps") + if (perSide) " / side" else ""

fun countLabel(count: Int, singular: String): String = "$count ${if (count == 1) singular else singular + "s"}"

fun formatDate(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

fun formatDuration(start: Long, end: Long): String {
    val seconds = ((end - start).coerceAtLeast(0) / 1000)
    return when {
        seconds < 60 -> "$seconds sec"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 3600} hr ${(seconds % 3600) / 60} min"
    }
}

fun displayNumber(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
fun setDescription(set: SetEntity, exercise: SessionExerciseEntity): String {
    val amount = if (exercise.metric == TrackingMetric.SECONDS.name) "${set.value} sec" else countLabel(set.value, "rep")
    val perSide = if (exercise.perSide) " / side" else ""
    val load = set.weight?.let { "${if (set.assistance) "Assist " else ""}${displayNumber(it)} ${set.unit.lowercase()}" } ?: "No load recorded"
    return "$amount$perSide · $load"
}

val SetInputSaver = listSaver<SetInput, Any>(
    save = { listOf(it.value, it.weight, it.unit, it.assistance) },
    restore = { SetInput(it[0] as String, it[1] as String, it[2] as String, it[3] as Boolean) },
)

@Composable
fun SetEntryFields(input: SetInput, onChange: (SetInput) -> Unit, metric: String, perSide: Boolean,
                   tag: String, enabled: Boolean = true, error: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(input.value, { onChange(input.copy(value = it)) },
                Modifier.weight(1f).testTag("value-$tag"), label = { Text(metricLabel(metric, perSide)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled, isError = error != null, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(input.weight, { onChange(input.copy(weight = it)) },
                Modifier.weight(1f).testTag("weight-$tag"), label = { Text("Load (${input.unit.lowercase()})") },
                placeholder = { Text("Optional") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled,
                isError = error != null, shape = RoundedCornerShape(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(input.unit == "KG", { onChange(input.copy(unit = "KG")) }, label = { Text("kg") }, enabled = enabled)
            FilterChip(input.unit == "LB", { onChange(input.copy(unit = "LB")) }, label = { Text("lb") }, enabled = enabled)
            FilterChip(input.assistance, { onChange(input.copy(assistance = !input.assistance)) },
                label = { Text("Assistance") }, enabled = enabled)
        }
        if (input.assistance) Text("Load records assistance provided, not added resistance.", style = MaterialTheme.typography.bodySmall)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
