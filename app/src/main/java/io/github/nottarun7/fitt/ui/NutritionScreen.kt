package io.github.nottarun7.fitt.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nottarun7.fitt.interchange.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun NutritionScreen(model: NutritionViewModel) {
    val state by model.overview.collectAsStateWithLifecycle()
    val form by model.form.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val reminder by model.reminders.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refreshReminders() }
        lifecycle.lifecycle.addObserver(observer); model.refreshReminders()
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) model.reminderEnabled(true) else model.permissionDenied()
    }
    var tab by rememberSaveable { mutableStateOf("Diary") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var query by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var deleteKind by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf("") }
    fun back() { if (!busy) { if (form?.dirty == true) leaving = true else model.closeEditor() } }
    BackHandler(form != null || busy) { if (form != null) back() }
    Column(Modifier.fillMaxSize()) {
        val draft = form
        if (draft != null) {
            PageHeader(when (draft.kind) { "food" -> "Food details"; "meal" -> "Meal details"; else -> "Diet schedule" }, onBack = ::back,
                action = { TextButton({ focus.clearFocus(); model.save() }, enabled = !busy, modifier = Modifier.testTag("saveNutrition")) { Text("Save") } })
            NutritionEditor(draft, model, busy, onPick = { focus.clearFocus(); picking = true })
        } else {
            PageHeader("Nutrition", "Meals and plans, stored on your device")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Diary", "Foods", "Plans", "Reminders").forEach { title ->
                    FilterChip(tab == title, { tab = title }, label = { Text(title) }, modifier = Modifier.testTag("nutrition-$title"))
                }
            }
            LazyColumn(Modifier.weight(1f).testTag("nutritionList"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.loading) item { CircularProgressIndicator() }
                if (state.error != null) item { EmptyMessage("Storage unavailable", state.error!!) }
                if (!state.loading && state.error == null) when (tab) {
                    "Diary" -> {
                        val day = runCatching { LocalDate.parse(date) }.getOrNull()
                        val meals = state.meals.filter { it.date == date }
                        item {
                            FittCard {
                                OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth().testTag("diaryDate"), label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, isError = day == null)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton({ date = day!!.minusDays(1).toString() }, enabled = day != null) { Text("Previous") }
                                    TextButton({ date = LocalDate.now().toString() }) { Text("Today") }
                                    TextButton({ date = day!!.plusDays(1).toString() }, enabled = day != null) { Text("Next") }
                                }
                                Text("Daily total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Totals(meals.flatMap { it.items }.nutritionTotals())
                                Button({ model.meal(date) }, Modifier.fillMaxWidth().testTag("newMeal"), enabled = !busy && day != null) { Text("Log a meal") }
                            }
                        }
                        if (day != null) {
                            val due = state.schedules.filter { it.enabled && it.daysOfWeek and (1 shl (day.dayOfWeek.value - 1)) != 0 }
                            if (due.isNotEmpty()) item { Text("Planned for this day", style = MaterialTheme.typography.titleLarge) }
                            items(due, key = { "due:${it.id}" }) { schedule ->
                                FittCard {
                                    Text("${minuteText(schedule.timeMinute).ifBlank { "Time not set" }} · ${schedule.name}", fontWeight = FontWeight.SemiBold)
                                    Text(schedule.items.joinToString { "${displayNumber(it.quantity)} × ${it.name}" })
                                    if (schedule.items.isEmpty()) Text(schedule.notes)
                                    TextButton({ model.logSchedule(schedule.id, date) }, enabled = !busy && schedule.items.isNotEmpty(), modifier = Modifier.testTag("logPlan-${schedule.id}")) { Text("Log planned meal") }
                                    if (schedule.items.isEmpty()) Text("Add foods with nutrition values in Plans before logging this meal.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        item { Text("Logged meals", style = MaterialTheme.typography.titleLarge) }
                        if (meals.isEmpty()) item { Text("No meals logged for this date. Add foods to your library, then build a meal.") }
                        items(meals, key = { "meal:${it.id}" }) { meal ->
                            FittCard(Modifier.testTag("meal-${meal.id}")) {
                                Text(meal.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                PortionSummary(meal.items); Totals(meal.items.nutritionTotals())
                                if (meal.notes.isNotBlank()) Text(meal.notes)
                                Row {
                                    TextButton({ model.meal(date, meal) }, enabled = !busy) { Text("Edit meal") }
                                    TextButton({ deleteKind = "meal"; deleteId = meal.id }, enabled = !busy) { Text("Delete meal") }
                                }
                            }
                        }
                    }
                    "Foods" -> {
                        item {
                            Button({ model.food() }, Modifier.fillMaxWidth().testTag("newFood"), enabled = !busy) { Text("Create custom food") }
                            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search your foods") }, singleLine = true)
                            Text("Enter values from your food label or recipe for one serving. No online database is needed.", Modifier.padding(top = 8.dp))
                        }
                        val found = state.foods.filter { it.name.contains(query, ignoreCase = true) }
                        if (found.isEmpty()) item { EmptyMessage("Your food library", "Create a food with its serving size and nutrition values to use it in meals and plans.") }
                        items(found, key = { it.id }) { food ->
                            FittCard(Modifier.testTag("food-${food.id}")) {
                                Text(food.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("Per ${food.servingLabel}")
                                Totals(NutritionTotals(food.calories, food.protein, food.carbs, food.fat))
                                Row {
                                    TextButton({ model.food(food) }, enabled = !busy) { Text("Edit food") }
                                    TextButton({ deleteKind = "food"; deleteId = food.id }, enabled = !busy) { Text("Delete food") }
                                }
                            }
                        }
                    }
                    "Plans" -> {
                        item {
                            Button({ model.schedule() }, Modifier.fillMaxWidth().testTag("newDietSchedule"), enabled = !busy) { Text("Create diet schedule") }
                            Text("Plan meals for selected weekdays and a local time. Plans appear in your diary; logging one adds a separate meal with its saved portions.", Modifier.padding(top = 8.dp))
                        }
                        if (state.schedules.isEmpty()) item { EmptyMessage("Make a weekly plan", "Add foods to your library, then choose meals, weekdays, and times.") }
                        items(state.schedules, key = { it.id }) { schedule ->
                            FittCard(Modifier.testTag("schedule-${schedule.id}")) {
                                Text(schedule.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("${minuteText(schedule.timeMinute).ifBlank { "Time not set" }} · ${weekdayText(schedule.daysOfWeek)}${if (schedule.enabled) "" else " · Paused"}")
                                PortionSummary(schedule.items)
                                if (schedule.items.isNotEmpty()) Totals(schedule.items.nutritionTotals())
                                else Text("Text plan · nutrition values not provided")
                                if (schedule.notes.isNotBlank()) Text(schedule.notes)
                                Row {
                                    TextButton({ model.schedule(schedule) }, enabled = !busy) { Text("Edit schedule") }
                                    TextButton({ deleteKind = "schedule"; deleteId = schedule.id }, enabled = !busy) { Text("Delete schedule") }
                                }
                            }
                        }
                    }
                    "Reminders" -> item {
                        var time by rememberSaveable(state.reminderMinute) { mutableStateOf(minuteText(state.reminderMinute)) }
                        FittCard {
                            Text("Daily nutrition reminder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("An optional daily notification to log food or review your plan. Android may deliver it later to save battery.")
                            OutlinedTextField(time, { time = it }, Modifier.fillMaxWidth().testTag("reminderTime"), label = { Text("Local time (HH:MM, 24-hour)") }, singleLine = true)
                            OutlinedButton({ model.reminderTime(time) }, enabled = !busy, modifier = Modifier.testTag("saveReminderTime")) { Text("Save reminder time") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable daily reminder", Modifier.weight(1f))
                                Switch(reminder.optedIn, { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else model.reminderEnabled(enabled)
                                }, enabled = !busy, modifier = Modifier.testTag("enableNutritionReminder").semantics { contentDescription = "Enable daily nutrition reminder" })
                            }
                            Text(when { !reminder.optedIn -> "Reminders are off."; !reminder.allowed -> "Paused by Android notification settings."; else -> "Reminder enabled for ${minuteText(state.reminderMinute)} local time." })
                            if (reminder.error != null) Text(reminder.error!!, color = MaterialTheme.colorScheme.error)
                            TextButton({ context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Android notification settings") }
                            Text("Backup files include the reminder time. Notification permission and your opt-in stay on this device.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message != null) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message!!, Modifier.weight(1f).testTag("nutritionMessage"), style = MaterialTheme.typography.bodySmall)
                TextButton(model::dismissMessage) { Text("Dismiss") }
            }
        }
    }
    if (picking && form != null) {
        var search by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { picking = false }, title = { Text("Add a food") }, text = {
            Column {
                OutlinedTextField(search, { search = it }, label = { Text("Search foods") }, singleLine = true)
                if (state.foods.isEmpty()) Text("Create a custom food in the Foods tab first.")
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(state.foods.filter { it.name.contains(search, ignoreCase = true) }, key = { it.id }) { food ->
                        TextButton({ model.add(food); picking = false }, Modifier.fillMaxWidth().testTag("pickFood-${food.id}")) { Text("${food.name} · ${food.servingLabel}") }
                    }
                }
            }
        }, confirmButton = { TextButton({ picking = false }) { Text("Done") } })
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("Discard unsaved changes?") },
        text = { Text("Your saved meals and foods will stay as they are.") },
        confirmButton = { TextButton({ leaving = false; model.closeEditor() }) { Text("Discard changes") } },
        dismissButton = { TextButton({ leaving = false }) { Text("Keep editing") } })
    if (deleteId.isNotEmpty()) AlertDialog(onDismissRequest = { deleteId = "" }, title = { Text("Delete $deleteKind?") },
        text = { Text(if (deleteKind == "food") "Meals and schedules keep their saved food and nutrition values." else "This removes the saved $deleteKind and its food portions.") },
        confirmButton = { TextButton({
            when (deleteKind) { "food" -> model.deleteFood(deleteId); "meal" -> model.deleteMeal(deleteId); else -> model.deleteSchedule(deleteId) }
            deleteId = ""
        }, enabled = !busy) { Text("Delete") } }, dismissButton = { TextButton({ deleteId = "" }) { Text("Cancel") } })
}

@Composable
private fun ColumnScope.NutritionEditor(form: NutritionForm, model: NutritionViewModel, busy: Boolean, onPick: () -> Unit) {
    LazyColumn(Modifier.weight(1f).testTag("nutritionEditor"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fun field(tag: String, label: String, value: String, decimal: Boolean = false, update: (NutritionForm, String) -> NutritionForm) = item {
            OutlinedTextField(value, { text -> model.edit { update(it, text) } }, Modifier.fillMaxWidth().testTag(tag), label = { Text(label) },
                enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
        }
        field("nutritionName", "Name", form.name) { d, v -> d.copy(name = v) }
        if (form.kind == "food") {
            item { Text("Nutrition for one serving. Use 0 for a nutrient listed as zero. A serving can be 100 g, 1 cup, 1 piece, or your recipe portion.") }
            field("foodServing", "Serving label", form.serving) { d, v -> d.copy(serving = v) }
            field("foodCalories", "Calories (kcal)", form.calories, true) { d, v -> d.copy(calories = v) }
            field("foodProtein", "Protein (g)", form.protein, true) { d, v -> d.copy(protein = v) }
            field("foodCarbs", "Carbohydrates (g)", form.carbs, true) { d, v -> d.copy(carbs = v) }
            field("foodFat", "Fat (g)", form.fat, true) { d, v -> d.copy(fat = v) }
        } else {
            if (form.kind == "meal") field("mealDate", "Date (YYYY-MM-DD)", form.date) { d, v -> d.copy(date = v) }
            else {
                field("scheduleTime", "Local time (HH:MM, optional)", form.time) { d, v -> d.copy(time = v) }
                item {
                    Text("Repeat on")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        java.time.DayOfWeek.values().forEach { day ->
                            val bit = 1 shl (day.value - 1)
                            FilterChip(form.days and bit != 0, { model.edit { it.copy(days = it.days xor bit) } },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }, enabled = !busy)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Schedule active", Modifier.weight(1f))
                        Switch(form.enabled, { v -> model.edit { it.copy(enabled = v) } }, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "Schedule active" })
                    }
                }
            }
            field("nutritionNotes", "Notes (optional)", form.notes) { d, v -> d.copy(notes = v) }
            item {
                Text("Food portions", style = MaterialTheme.typography.titleLarge)
                if (form.kind == "schedule") Text("For a text-only diet plan, leave portions empty and put the food instructions in Notes. Add nutrition values before logging it.")
            }
            items(form.items, key = { it.snapshot.id }) { row ->
                FittCard {
                    Text(row.snapshot.name, fontWeight = FontWeight.SemiBold)
                    Text("One serving: ${row.snapshot.servingLabel}")
                    OutlinedTextField(row.quantity, { quantity -> model.edit { draft -> draft.copy(items = draft.items.map {
                        if (it.snapshot.id == row.snapshot.id) it.copy(quantity = quantity) else it
                    }) } }, Modifier.fillMaxWidth().testTag("servings-${row.snapshot.id}"), label = { Text("Servings") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy)
                    TextButton({ model.edit { d -> d.copy(items = d.items.filterNot { it.snapshot.id == row.snapshot.id }) } }, enabled = !busy) { Text("Remove portion") }
                }
            }
            item { OutlinedButton(onPick, Modifier.fillMaxWidth().testTag("addMealFood"), enabled = !busy) { Text("Add food") } }
            item { Text("These portions keep their saved nutrition values when the food library changes.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun Totals(total: NutritionTotals) {
    Text("${nutritionNumber(total.calories)} kcal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Protein ${nutritionNumber(total.protein)} g · Carbs ${nutritionNumber(total.carbs)} g · Fat ${nutritionNumber(total.fat)} g")
}
@Composable private fun PortionSummary(items: List<BackupPortion>) {
    items.forEach { Text("${displayNumber(it.quantity)} × ${it.name} (${it.servingLabel})") }
}
private fun nutritionNumber(value: Double) = java.math.BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
fun weekdayText(mask: Int) = java.time.DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }
    .joinToString { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
