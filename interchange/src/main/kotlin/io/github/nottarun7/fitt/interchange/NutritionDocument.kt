package io.github.nottarun7.fitt.interchange

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class BackupFood(val id: String, val name: String, val servingLabel: String, val calories: Double,
    val protein: Double, val carbs: Double, val fat: Double, val updatedAt: Long)

/** Nutrients are per serving; quantity scales the snapshot without consulting the food library. */
@Serializable
data class BackupPortion(val id: String, val position: Int, val foodId: String?, val name: String,
    val servingLabel: String, val quantity: Double, val calories: Double, val protein: Double,
    val carbs: Double, val fat: Double)

@Serializable
data class BackupMeal(val id: String, val date: String, val name: String, val notes: String,
    val updatedAt: Long, val items: List<BackupPortion>)

/** Monday = bit 0, Sunday = bit 6. Local wall time, repeated weekly. */
@Serializable
data class BackupDietSchedule(val id: String, val name: String, val daysOfWeek: Int, val timeMinute: Int,
    val enabled: Boolean, val notes: String, val updatedAt: Long, val items: List<BackupPortion>)

@Serializable
data class BackupNutrition(
    @Required val foods: List<BackupFood> = emptyList(),
    @Required val meals: List<BackupMeal> = emptyList(),
    @Required val schedules: List<BackupDietSchedule> = emptyList(),
    @Required val reminderMinute: Int = 1200,
)

data class NutritionTotals(val calories: Double = 0.0, val protein: Double = 0.0,
    val carbs: Double = 0.0, val fat: Double = 0.0) {
    operator fun plus(other: NutritionTotals) = NutritionTotals(calories + other.calories,
        protein + other.protein, carbs + other.carbs, fat + other.fat)
}

fun Iterable<BackupPortion>.nutritionTotals(): NutritionTotals = fold(NutritionTotals()) { total, item ->
    total + NutritionTotals(item.calories * item.quantity, item.protein * item.quantity,
        item.carbs * item.quantity, item.fat * item.quantity)
}

fun BackupNutrition.canonical(): BackupNutrition = copy(foods = foods.sortedBy { it.id },
    meals = meals.sortedBy { it.id }.map { it.copy(items = it.items.sortedBy { row -> row.position }) },
    schedules = schedules.sortedBy { it.id }.map { it.copy(items = it.items.sortedBy { row -> row.position }) })

internal fun BackupNutrition.recordCount(): Long = 1L + foods.size + meals.size + schedules.size +
    meals.sumOf { it.items.size.toLong() } + schedules.sumOf { it.items.size.toLong() }

fun BackupNutrition.validateNutrition(): List<ValidationIssue> = buildList {
    fun check(valid: Boolean, path: String, message: String) {
        if (!valid && size < 100) add(ValidationIssue("nutrition.$path", message))
    }
    fun text(value: String, path: String, blank: Boolean = false, limit: Int = BackupLimits.TEXT_LENGTH) {
        check((blank || value.isNotBlank()) && value.length <= limit && validUnicode(value), path, "Invalid or oversized text.")
    }
    fun id(value: String, path: String) = text(value, path, limit = 200)
    fun ids(values: List<String>, path: String) {
        val seen = HashSet<String>()
        values.forEachIndexed { index, value -> id(value, "$path[$index].id"); check(seen.add(value), path, "Duplicate ID in this table.") }
    }
    fun nutrients(calories: Double, protein: Double, carbs: Double, fat: Double, path: String) {
        listOf("calories" to calories, "protein" to protein, "carbs" to carbs, "fat" to fat).forEach { (key, value) ->
            check(value.isFinite() && value in 0.0..100_000.0, "$path.$key", "Must be finite and between 0 and 100000 per serving.")
        }
    }
    fun items(items: List<BackupPortion>, path: String, required: Boolean = true) {
        check(!required || items.isNotEmpty(), path, "Add at least one food.")
        val positions = HashSet<Int>()
        items.forEachIndexed { index, item ->
            val at = "$path[$index]"
            text(item.name, "$at.name"); text(item.servingLabel, "$at.servingLabel")
            item.foodId?.let { id(it, "$at.foodId") }
            check(item.position >= 0 && positions.add(item.position), "$at.position", "Position must be non-negative and unique within its parent.")
            check(item.quantity.isFinite() && item.quantity > 0 && item.quantity <= 10_000, "$at.quantity", "Servings must be positive and no more than 10000.")
            nutrients(item.calories, item.protein, item.carbs, item.fat, at)
        }
    }
    ids(foods.map { it.id }, "foods"); ids(meals.map { it.id }, "meals"); ids(schedules.map { it.id }, "schedules")
    ids(meals.flatMap { it.items }.map { it.id }, "mealItems")
    ids(schedules.flatMap { it.items }.map { it.id }, "scheduleItems")
    check(reminderMinute in 0..1439, "reminderMinute", "Time must be within one day.")
    foods.forEachIndexed { index, food ->
        val at = "foods[$index]"
        text(food.name, "$at.name"); text(food.servingLabel, "$at.servingLabel")
        nutrients(food.calories, food.protein, food.carbs, food.fat, at)
        check(food.updatedAt >= 0, "$at.updatedAt", "Must not be negative.")
    }
    meals.forEachIndexed { index, meal ->
        val at = "meals[$index]"
        text(meal.name, "$at.name"); text(meal.notes, "$at.notes", blank = true)
        check(runCatching { LocalDate.parse(meal.date).toString() == meal.date }.getOrDefault(false), "$at.date", "Use an ISO date (YYYY-MM-DD).")
        check(meal.updatedAt >= 0, "$at.updatedAt", "Must not be negative.")
        items(meal.items, "$at.items")
    }
    schedules.forEachIndexed { index, schedule ->
        val at = "schedules[$index]"
        text(schedule.name, "$at.name"); text(schedule.notes, "$at.notes", blank = true)
        check(schedule.updatedAt >= 0, "$at.updatedAt", "Must not be negative.")
        check(schedule.daysOfWeek in 1..127, "$at.daysOfWeek", "Select at least one weekday.")
        check(schedule.timeMinute in -1..1439, "$at.timeMinute", "Time must be within one day, or -1 if unspecified.")
        check(schedule.items.isNotEmpty() || schedule.notes.isNotBlank(), "$at.notes", "Describe the diet instructions when no foods are specified.")
        items(schedule.items, "$at.items", required = false)
    }
}

private fun validUnicode(value: String): Boolean {
    var i = 0
    while (i < value.length) {
        val c = value[i++]
        if (c == '\u0000' || c.isLowSurrogate()) return false
        if (c.isHighSurrogate() && (i >= value.length || !value[i++].isLowSurrogate())) return false
    }
    return true
}
