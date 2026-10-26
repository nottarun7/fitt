package io.github.nottarun7.fitt.progress

import io.github.nottarun7.fitt.interchange.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class ProgressDay(val date: LocalDate, val workouts: Int, val sets: Int, val volumeKg: Double?,
    val nutrition: NutritionTotals?, val meals: Int)
data class ExerciseTrend(val key: String, val name: String, val metric: BackupMetric, val assisted: Boolean,
    val perSide: Boolean, val bestValues: Map<LocalDate, Double>, val topLoadKg: Map<LocalDate, Double>)
data class ProgressReport(val days: List<ProgressDay>, val exercises: List<ExerciseTrend>, val overflowDays: Int) {
    val loggedNutritionDays get() = days.count { it.nutrition != null }
}

object ProgressAnalytics {
    fun report(workouts: List<BackupWorkout>, meals: List<BackupMeal>, end: LocalDate, count: Int, zone: ZoneId): ProgressReport {
        require(count in setOf(7, 30, 90))
        val start = end.minusDays(count - 1L)
        fun inRange(day: LocalDate) = day >= start && day <= end
        val completed = workouts.filter { it.completedAtEpochMillis != null }.groupBy {
            Instant.ofEpochMilli(it.completedAtEpochMillis!!).atZone(zone).toLocalDate()
        }.filterKeys(::inRange)
        val loggedMeals = meals.groupBy { LocalDate.parse(it.date) }.filterKeys(::inRange)
        val builders = linkedMapOf<String, Triple<BackupExercise, Boolean, MutableList<Pair<LocalDate, BackupSet>>>>()
        var overflow = 0
        val days = (0 until count).map { offset ->
            val date = start.plusDays(offset.toLong()); val sessions = completed[date].orEmpty()
            var volume = 0.0
            sessions.forEach { workout -> workout.exercises.forEach { logged -> logged.sets.forEach { set ->
                val exercise = logged.exercise
                val identity = exercise.catalogueId?.let { "catalogue:$it" } ?: "custom:${exercise.name.trim().lowercase(Locale.ROOT)}"
                val key = "$identity|${exercise.metric}|${exercise.perSide}|${set.assistance}"
                builders.getOrPut(key) { Triple(exercise, set.assistance, mutableListOf()) }.third.add(date to set)
                if (exercise.metric == BackupMetric.REPS && !set.assistance) set.weight?.let { volume += set.value * kg(it, set.unit) }
            } } }
            if (!volume.isFinite()) overflow++
            val dayMeals = loggedMeals[date].orEmpty()
            ProgressDay(date, sessions.size, sessions.sumOf { it.exercises.sumOf { row -> row.sets.size } }, volume.takeIf { it.isFinite() },
                if (dayMeals.isEmpty()) null else dayMeals.flatMap { it.items }.nutritionTotals(), dayMeals.size)
        }
        val trends = builders.map { (key, value) ->
            val (exercise, assisted, rows) = value
            ExerciseTrend(key, exercise.name, exercise.metric, assisted, exercise.perSide,
                rows.groupBy { it.first }.mapValues { (_, sets) -> sets.maxOf { it.second.value.toDouble() } },
                if (assisted) emptyMap() else rows.filter { it.second.weight != null }.groupBy { it.first }
                    .mapValues { (_, sets) -> sets.maxOf { kg(it.second.weight!!, it.second.unit) } })
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
        return ProgressReport(days, trends, overflow)
    }
    private fun kg(value: Double, unit: WeightUnit) = if (unit == WeightUnit.LB) value * 0.45359237 else value
}
