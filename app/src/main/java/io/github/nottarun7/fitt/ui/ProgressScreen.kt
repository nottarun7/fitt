package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nottarun7.fitt.interchange.BackupMetric
import io.github.nottarun7.fitt.progress.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressScreen(model: ProgressViewModel, onBackup: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Progress", "Patterns from your saved logs", action = { TextButton(onBackup) { Text("Backups") } })
        PrimaryTabRow(tab) {
            listOf("Workouts", "Nutrition", "Photos").forEachIndexed { index, label -> Tab(tab == index, { tab = index }, text = { Text(label) }, modifier = Modifier.testTag("progress-$label")) }
        }
        if (tab == 2) PhotoGallery(model) else {
            FittSegmentedControl(listOf("7 days", "30 days", "90 days"), listOf(7, 30, 90).indexOf(state.days),
                { model.range(listOf(7, 30, 90)[it]) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            val report = state.report
            if (report == null && state.error == null) CircularProgressIndicator(Modifier.padding(24.dp))
            if (report != null) ProgressCharts(report, tab == 1)
        }
    }
}

@Composable
fun ProgressCharts(report: ProgressReport, nutrition: Boolean) {
    var workoutMetric by rememberSaveable { mutableIntStateOf(0) }
    var nutritionMetric by rememberSaveable { mutableIntStateOf(0) }
    var exerciseKey by rememberSaveable { mutableStateOf<String?>(null) }
    var exerciseMenu by remember { mutableStateOf(false) }
    val dates = report.days.map { it.date }
    LazyColumn(Modifier.fillMaxSize().testTag("progressCharts"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (nutrition) {
            if (report.loggedNutritionDays == 0) item { EmptyMessage("No meals logged in this range", "Log a meal in Nutrition to start seeing calorie and macro trends. Diet schedules appear after you log them as meals.") }
            else {
                val names = listOf("Calories", "Protein", "Carbs", "Fat")
                val unit = if (nutritionMetric == 0) "kcal" else "g"
                val values = report.days.map { day -> day.nutrition?.let { when (nutritionMetric) { 0 -> it.calories; 1 -> it.protein; 2 -> it.carbs; else -> it.fat } } }
                item {
                    FittCard {
                        Text("Daily nutrition", style = MaterialTheme.typography.titleLarge)
                        MetricChips(names, nutritionMetric) { nutritionMetric = it }
                        val logged = values.filterNotNull()
                        Text("${chartNumber(logged.sum() / logged.size)} $unit average · ${countLabel(report.loggedNutritionDays, "logged day")}", style = MaterialTheme.typography.titleMedium)
                        Text("Averages include logged days only. A partially logged day may understate your intake. Gaps mean no meals were logged.", style = MaterialTheme.typography.bodySmall)
                        TrendChart(names[nutritionMetric], dates, values, unit)
                    }
                }
            }
        } else {
            if (report.days.sumOf { it.workouts } == 0) item { EmptyMessage("No completed workouts in this range", "Finish a workout to see your trends. Routines and unfinished workouts do not count as completed activity.") }
            else {
                item {
                    FittCard {
                        Text("${report.days.sumOf { it.workouts }} workouts · ${report.days.sumOf { it.sets }} sets", style = MaterialTheme.typography.titleLarge)
                        MetricChips(listOf("Workouts", "Sets", "Volume"), workoutMetric) { workoutMetric = it }
                        val unit = listOf("workouts", "sets", "kg·reps")[workoutMetric]
                        val values = report.days.map { when (workoutMetric) { 0 -> it.workouts.toDouble(); 1 -> it.sets.toDouble(); else -> it.volumeKg } }
                        TrendChart("Daily ${listOf("workouts", "sets", "volume")[workoutMetric]}", dates, values, unit, bars = true)
                        Text("Volume = reps × recorded added load, converted to kg. Bodyweight, assistance and timed sets add no volume. Per-side sets count once as logged. It is not a measure of total effort.", style = MaterialTheme.typography.bodySmall)
                        if (report.overflowDays > 0) Text("Some volume values are too large to display safely.", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (report.exercises.isNotEmpty()) item {
                    val selected = report.exercises.firstOrNull { it.key == exerciseKey } ?: report.exercises.first()
                    FittCard {
                        Text("Exercise progression", style = MaterialTheme.typography.titleLarge)
                        Box {
                            OutlinedButton({ exerciseMenu = true }, Modifier.fillMaxWidth()) { Text(trendName(selected)) }
                            DropdownMenu(exerciseMenu, { exerciseMenu = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                                report.exercises.forEach { trend -> DropdownMenuItem(text = { Text(trendName(trend)) }, onClick = { exerciseKey = trend.key; exerciseMenu = false }) }
                            }
                        }
                        val unit = if (selected.metric == BackupMetric.SECONDS) "sec" else "reps"
                        TrendChart("Best set", dates, dates.map { selected.bestValues[it] }, unit)
                        if (selected.topLoadKg.isNotEmpty()) TrendChart("Highest recorded added load", dates, dates.map { selected.topLoadKg[it] }, "kg")
                        Text("Each point is the highest recorded value that day. Reps/time and load can come from different sets. Assistance is shown separately; exercise names alone may group custom exercises.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun trendName(trend: ExerciseTrend) = trend.name + (if (trend.perSide) " · per side" else "") +
    (if (trend.assisted) " · assisted" else "") + (if (trend.metric == BackupMetric.SECONDS) " · timed" else " · reps")

@Composable
private fun MetricChips(names: List<String>, selected: Int, change: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { index, name -> FilterChip(selected == index, { change(index) }, label = { Text(name) }) }
    }
}

/** The table provides exact values and a screen-reader alternative to the drawn chart. */
@Composable
fun TrendChart(title: String, dates: List<LocalDate>, values: List<Double?>, unit: String, bars: Boolean = false) {
    var expanded by rememberSaveable(title, dates.size) { mutableStateOf(false) }
    val safe = values.map { it?.takeIf { number -> number.isFinite() && number >= 0 } }
    val max = (safe.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text("0–${chartNumber(max)} $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "$title chart in $unit. Use View values for daily readings." }.testTag("chart-$title")) {
        val base = size.height - 8.dp.toPx(); val top = 8.dp.toPx(); val height = base - top
        for (line in 0..3) { val y = base - height * line / 3; drawLine(grid.copy(alpha = .65f), Offset(0f, y), Offset(size.width, y), .75.dp.toPx()) }
        val step = size.width / dates.size.coerceAtLeast(1)
        var previous: Offset? = null
        safe.forEachIndexed { index, value ->
            if (value == null) previous = null else {
                val point = Offset(step * (index + .5f), base - (value / max).toFloat() * height)
                if (bars) drawRoundRect(color, Offset(point.x - step * .28f, point.y), Size(step * .56f, base - point.y), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                else { previous?.let { drawLine(color, it, point, 2.5.dp.toPx(), cap = StrokeCap.Round) }; drawCircle(color, if (dates.size > 30) 2.dp.toPx() else 3.dp.toPx(), point); previous = point }
            }
        }
    }
    if (dates.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(dates.first(), dates[dates.size / 2], dates.last()).forEach { Text(it.format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.labelSmall) }
    }
    TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide values" else "View values") }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        dates.indices.reversed().forEach { index ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dates[index].toString(), style = MaterialTheme.typography.bodySmall)
                Text(safe[index]?.let { "${chartNumber(it)} $unit" } ?: "No log", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FittSegmentedControl(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                val color by animateColorAsState(if (index == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    tween(140), label = "Range selection")
                Surface(Modifier.weight(1f).selectable(index == selected, role = Role.RadioButton, onClick = { onSelect(index) }), shape = RoundedCornerShape(8.dp), color = color) {
                    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (index == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

internal fun chartNumber(value: Double): String = if (value >= 1_000_000) String.format(Locale.getDefault(), "%.2e", value)
    else String.format(Locale.getDefault(), "%.1f", value).removeSuffix(".0")
