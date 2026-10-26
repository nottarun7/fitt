package io.github.nottarun7.fitt

import io.github.nottarun7.fitt.interchange.*
import io.github.nottarun7.fitt.progress.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ProgressAnalyticsTest {
    private val day = LocalDate.parse("2026-10-26")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val stamp = day.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
    private fun exercise(metric: BackupMetric = BackupMetric.REPS, perSide: Boolean = false, sets: List<BackupSet>) =
        BackupLoggedExercise(BackupExercise("x", "Press", 0, metric, 3, 8, null, perSide, false), sets, null)
    private fun workout(vararg exercises: BackupLoggedExercise, end: Long? = stamp) = BackupWorkout("w", "Day", null, stamp - 10, end, exercises.toList())
    private fun set(load: Double? = 10.0, unit: WeightUnit = WeightUnit.KG, assist: Boolean = false) = BackupSet("s", 0, 10, load, unit, assist)
    @Test fun completedOnlyAndDeviceLocalCompletionDateDriveDailyCounts() {
        val midnightUtc = Instant.parse("2026-10-25T20:00:00Z").toEpochMilli()
        val report = ProgressAnalytics.report(listOf(workout(exercise(sets = listOf(set())), end = midnightUtc), workout(end = null), workout(end = stamp + 86_400_000)), emptyList(), day, 7, zone)
        assertEquals(7, report.days.size); assertEquals(1, report.days.last().workouts); assertEquals(1, report.days.sumOf { it.workouts })
        assertEquals(1, report.days.last().sets); assertEquals(0, report.days.first().workouts)
    }
    @Test fun volumeNormalizesPoundsExcludesAssistanceTimedAndBodyweightAndCountsPerSideOnce() {
        val rows = listOf(exercise(perSide = true, sets = listOf(set(), set(10.0, WeightUnit.LB), set(100.0, assist = true), set(null))), exercise(BackupMetric.SECONDS, sets = listOf(set(500.0))))
        val report = ProgressAnalytics.report(listOf(workout(*rows.toTypedArray())), emptyList(), day, 30, zone)
        assertEquals(145.359237, report.days.last().volumeKg!!, .000001)
        assertEquals(5, report.days.last().sets); assertEquals(3, report.exercises.size)
        assertTrue(report.exercises.single { it.assisted }.topLoadKg.isEmpty())
    }
    @Test fun nutritionUsesSnapshotsQuantitiesAndUnloggedDaysAreGaps() {
        val portion = BackupPortion("p", 0, "removed-food", "Bowl", "1 bowl", 1.5, 400.0, 20.0, 50.0, 10.0)
        val meal = BackupMeal("m", day.toString(), "Lunch", "", 1, listOf(portion))
        val report = ProgressAnalytics.report(emptyList(), listOf(meal, meal.copy(id = "later", date = day.plusDays(1).toString())), day, 7, zone)
        assertEquals(1, report.loggedNutritionDays); assertNull(report.days.first().nutrition)
        assertEquals(600.0, report.days.last().nutrition!!.calories, 0.0)
        assertEquals(30.0, report.days.last().nutrition!!.protein, 0.0)
    }
    @Test fun exerciseProgressGroupsCaseInsensitiveCustomNamesAndUsesDailyMaxima() {
        val one = exercise(sets = listOf(set().copy(value = 8)))
        val two = one.copy(exercise = one.exercise.copy(name = " press "), sets = listOf(set(15.0).copy(value = 12)))
        val report = ProgressAnalytics.report(listOf(workout(one), workout(two)), emptyList(), day, 90, zone)
        val trend = report.exercises.single()
        assertEquals(12.0, trend.bestValues.getValue(day), 0.0); assertEquals(15.0, trend.topLoadKg.getValue(day), 0.0)
    }
    @Test fun emptyRangeHasNoInventedExerciseOrNutritionDataAndOverflowCannotReachChart() {
        val empty = ProgressAnalytics.report(emptyList(), emptyList(), day, 7, zone)
        assertTrue(empty.exercises.isEmpty()); assertTrue(empty.days.all { it.nutrition == null && it.volumeKg == 0.0 })
        val overflow = ProgressAnalytics.report(listOf(workout(exercise(sets = listOf(set(Double.MAX_VALUE))))), emptyList(), day, 7, zone)
        assertNull(overflow.days.last().volumeKg); assertEquals(1, overflow.overflowDays)
    }
}
