package io.github.nottarun7.fitt.ui

import android.os.Bundle
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nottarun7.fitt.data.*
import io.github.nottarun7.fitt.interchange.*
import io.github.nottarun7.fitt.reminders.NutritionReminders
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class PortionForm(val snapshot: BackupPortion, val quantity: String = displayNumber(snapshot.quantity))
data class NutritionForm(val kind: String, val id: String = newNutritionId(), val name: String = "", val notes: String = "",
    val date: String = LocalDate.now().toString(), val serving: String = "", val calories: String = "", val protein: String = "",
    val carbs: String = "", val fat: String = "", val time: String = "12:00", val days: Int = 127, val enabled: Boolean = true,
    val items: List<PortionForm> = emptyList(), val dirty: Boolean = false)

class NutritionViewModel(private val repository: NutritionRepository, val reminders: NutritionReminders,
    private val saved: SavedStateHandle) : ViewModel() {
    val overview = repository.overview.catch { emit(NutritionOverview(loading = false, error = "Could not open your nutrition data. Reopen Fitt to retry.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NutritionOverview())
    private val mutableForm: MutableStateFlow<NutritionForm?> = MutableStateFlow(saved.get<Bundle>("nutritionForm")?.toNutritionForm())
    val form: StateFlow<NutritionForm?> = mutableForm.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private fun setForm(form: NutritionForm?) { mutableForm.value = form; saved["nutritionForm"] = form?.bundle() }
    fun dismissMessage() { mutableMessage.value = null }
    fun edit(change: (NutritionForm) -> NutritionForm) { if (!busy.value) form.value?.let { setForm(change(it).copy(dirty = true)) } }
    fun closeEditor() { if (!busy.value) setForm(null) }
    fun food(food: BackupFood? = null) = setForm(food?.let {
        NutritionForm("food", it.id, it.name, serving = it.servingLabel, calories = displayNumber(it.calories),
            protein = displayNumber(it.protein), carbs = displayNumber(it.carbs), fat = displayNumber(it.fat))
    } ?: NutritionForm("food"))
    fun meal(date: String, meal: BackupMeal? = null) = setForm(meal?.let {
        NutritionForm("meal", it.id, it.name, it.notes, it.date, items = it.items.map(::PortionForm))
    } ?: NutritionForm("meal", name = "Meal", date = date))
    fun schedule(schedule: BackupDietSchedule? = null) = setForm(schedule?.let {
        NutritionForm("schedule", it.id, it.name, it.notes, time = minuteText(it.timeMinute), days = it.daysOfWeek,
            enabled = it.enabled, items = it.items.map(::PortionForm))
    } ?: NutritionForm("schedule", name = "Meal plan"))
    fun add(food: BackupFood) = edit { it.copy(items = it.items + PortionForm(food.portion(it.items.size))) }
    private fun task(success: String? = null, work: suspend () -> Unit) {
        if (busy.value) return
        mutableBusy.value = true; mutableMessage.value = null
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { work() }; mutableMessage.value = success }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (invalid: IllegalArgumentException) { mutableMessage.value = invalid.message ?: "Check your entries." }
            catch (_: Exception) { mutableMessage.value = "Could not save this change. Try again." }
            finally { mutableBusy.value = false }
        }
    }
    fun save() {
        val draft = form.value ?: return
        task("Saved.") {
            fun number(value: String, label: String): Double = value.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("Enter a number for $label. Use 0 when the label lists none.")
            when (draft.kind) {
                "food" -> repository.saveFood(BackupFood(draft.id, draft.name, draft.serving,
                    number(draft.calories, "calories"), number(draft.protein, "protein"), number(draft.carbs, "carbohydrates"), number(draft.fat, "fat"), 0))
                "meal", "schedule" -> {
                    val items = draft.items.mapIndexed { index, row -> row.snapshot.copy(position = index, quantity = number(row.quantity, "servings")) }
                    if (draft.kind == "meal") repository.saveMeal(BackupMeal(draft.id, draft.date, draft.name, draft.notes, 0, items))
                    else repository.saveSchedule(BackupDietSchedule(draft.id, draft.name, draft.days,
                        if (draft.time.isBlank()) -1 else parseMinute(draft.time), draft.enabled, draft.notes, 0, items))
                }
            }
            withContext(Dispatchers.Main) { setForm(null) }
        }
    }
    fun deleteFood(id: String) = task("Food removed. Existing meals and plans keep their saved values.") { repository.deleteFood(id) }
    fun deleteMeal(id: String) = task("Meal removed.") { repository.deleteMeal(id) }
    fun deleteSchedule(id: String) = task("Schedule removed.") { repository.deleteSchedule(id) }
    fun logSchedule(id: String, date: String) = task("Planned meal logged.") {
        val day = runCatching { LocalDate.parse(date) }.getOrElse { throw IllegalArgumentException("Enter a valid date: YYYY-MM-DD.") }
        repository.logSchedule(id, day)
    }
    fun reminderTime(time: String) = task("Reminder time saved.") { repository.reminderTime(parseMinute(time)); reminders.sync() }
    fun reminderEnabled(enabled: Boolean) = task { reminders.enabled(enabled) }
    fun refreshReminders() {
        reminders.refresh()
        viewModelScope.launch(Dispatchers.IO) { runCatching { reminders.sync() } }
    }
    fun permissionDenied() { mutableMessage.value = "Reminders remain off. You can allow notifications in Android settings."; reminders.refresh() }
    companion object {
        fun factory(repository: NutritionRepository, reminders: NutritionReminders) = viewModelFactory {
            initializer { NutritionViewModel(repository, reminders, createSavedStateHandle()) }
        }
    }
}

fun minuteText(minute: Int): String = if (minute < 0) "" else "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)
fun parseMinute(value: String): Int {
    require(value.matches(Regex("\\d{2}:\\d{2}"))) { "Use a 24-hour time: HH:MM." }
    val time = runCatching { LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)) }
        .getOrElse { throw IllegalArgumentException("Use a valid 24-hour time: HH:MM.") }
    return time.hour * 60 + time.minute
}

private fun NutritionForm.bundle() = Bundle().apply {
    putString("kind", kind); putString("id", id); putString("name", name); putString("notes", notes); putString("date", date)
    putString("serving", serving); putString("calories", calories); putString("protein", protein); putString("carbs", carbs); putString("fat", fat)
    putString("time", time); putInt("days", days); putBoolean("enabled", enabled); putBoolean("dirty", dirty)
    putParcelableArrayList("items", ArrayList(items.map { row -> Bundle().apply {
        putString("snapshot", Json.encodeToString(BackupPortion.serializer(), row.snapshot)); putString("quantity", row.quantity)
    } }))
}
@Suppress("DEPRECATION")
private fun Bundle.toNutritionForm(): NutritionForm = NutritionForm(getString("kind")!!, getString("id")!!, getString("name").orEmpty(),
    getString("notes").orEmpty(), getString("date").orEmpty(), getString("serving").orEmpty(), getString("calories").orEmpty(),
    getString("protein").orEmpty(), getString("carbs").orEmpty(), getString("fat").orEmpty(), getString("time") ?: "12:00",
    getInt("days", 127), getBoolean("enabled", true), getParcelableArrayList<Bundle>("items").orEmpty().map {
        PortionForm(Json.decodeFromString(BackupPortion.serializer(), it.getString("snapshot")!!), it.getString("quantity").orEmpty())
    }, getBoolean("dirty"))
