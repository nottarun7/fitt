package io.github.nottarun7.fitt.health

import io.github.nottarun7.fitt.interchange.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

sealed interface HealthItem {
    val clientId: String
    val start: Instant
    val end: Instant
    val name: String
    data class Workout(override val clientId: String, override val start: Instant, override val end: Instant,
        override val name: String, val exercises: Int, val sets: Int) : HealthItem
    data class Meal(override val clientId: String, override val start: Instant, override val end: Instant,
        override val name: String, val totals: NutritionTotals) : HealthItem
}
data class HealthSharePlan(val items: List<HealthItem>, val skippedWorkouts: Int, val skippedMeals: Int)

/** Keeps the provider mapping testable without Android, and never guesses calories burned or event times. */
object HealthSharePlanner {
    fun prepare(document: BackupDocument, workouts: Boolean, meals: Boolean, now: Instant, zone: ZoneId): HealthSharePlan {
        document.requireValid()
        val items = ArrayList<HealthItem>()
        var skippedWorkouts = 0; var skippedMeals = 0
        fun id(type: String, key: String) = "fitt-$type-v1-${UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8))}"
        if (workouts) document.workouts.filter { it.completedAtEpochMillis != null }.forEach { workout ->
            val start = Instant.ofEpochMilli(workout.startedAtEpochMillis)
            val end = Instant.ofEpochMilli(workout.completedAtEpochMillis!!)
            if (end <= start || end > now) skippedWorkouts++
            else items.add(HealthItem.Workout(id("workout", workout.id), start, end, workout.name.take(500),
                workout.exercises.size, workout.exercises.sumOf { it.sets.size }))
        }
        if (meals) document.nutrition.meals.forEach { meal ->
            val start = LocalDate.parse(meal.date).atStartOfDay(zone).toInstant()
            val end = start.plusSeconds(1)
            val totals = meal.items.nutritionTotals()
            // Health Connect 1.1 accepts at most 100000 kcal and 100000 g per macronutrient per record.
            if (start < Instant.EPOCH || end > now || listOf(totals.calories, totals.protein, totals.carbs, totals.fat).any { !it.isFinite() || it !in 0.0..100_000.0 }) skippedMeals++
            else items.add(HealthItem.Meal(id("meal", meal.id), start, end, meal.name.take(500), totals))
        }
        return HealthSharePlan(items, skippedWorkouts, skippedMeals)
    }
}
