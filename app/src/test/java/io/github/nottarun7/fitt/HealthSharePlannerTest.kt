package io.github.nottarun7.fitt

import io.github.nottarun7.fitt.health.*
import io.github.nottarun7.fitt.interchange.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HealthSharePlannerTest {
    private val now = Instant.parse("2026-10-25T13:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val item = BackupPortion("i", 0, null, "Label food", "1 bowl", 1.5, 200.0, 10.0, 25.0, 6.0)
    private fun document() = BackupDocument(1, emptyList(), listOf(
        BackupWorkout("finished", "Workout", null, 1000, 2000, emptyList()),
        BackupWorkout("active", "Active", null, 1000, null, emptyList())),
        nutrition = BackupNutrition(meals = listOf(BackupMeal("m", "2026-10-25", "Lunch", "", 1, listOf(item)))))
    @Test fun completedWorkoutsAndActualMealsMapWithoutInventingEventsOrCaloriesBurned() {
        val plan = HealthSharePlanner.prepare(document(), true, true, now, zone)
        assertEquals(2, plan.items.size)
        val workout = plan.items.filterIsInstance<HealthItem.Workout>().single()
        assertEquals(Instant.ofEpochMilli(1000), workout.start); assertEquals(Instant.ofEpochMilli(2000), workout.end)
        val meal = plan.items.filterIsInstance<HealthItem.Meal>().single()
        assertEquals(Instant.parse("2026-10-24T18:30:00Z"), meal.start)
        assertEquals(meal.start.plusSeconds(1), meal.end); assertEquals(300.0, meal.totals.calories, 0.0)
    }
    @Test fun futureAndZeroDurationEntriesAreSkippedAndLargeNutrientsAreNotClamped() {
        val data = document()
        val changed = data.copy(workouts = listOf(data.workouts.first().copy(completedAtEpochMillis = 1000),
            data.workouts.first().copy(id = "future", completedAtEpochMillis = now.plusSeconds(1).toEpochMilli())),
            nutrition = data.nutrition.copy(meals = listOf(data.nutrition.meals.single().copy(date = "2026-10-26"),
                data.nutrition.meals.single().copy(id = "big", items = listOf(item.copy(id = "big-item", calories = 100000.0))))))
        val plan = HealthSharePlanner.prepare(changed, true, true, now, zone)
        assertTrue(plan.items.isEmpty()); assertEquals(2, plan.skippedWorkouts); assertEquals(2, plan.skippedMeals)
    }
    @Test fun stableClientIdsUpdateSameEntriesAndNewLocalIdsStayIndependent() {
        val data = document()
        val first = HealthSharePlanner.prepare(data, true, true, now, zone)
        val renamed = HealthSharePlanner.prepare(data.copy(workouts = data.workouts.map { it.copy(name = "Renamed") }), true, true, now, zone)
        assertEquals(first.items.map { it.clientId }, renamed.items.map { it.clientId })
        val other = HealthSharePlanner.prepare(data.copy(workouts = listOf(data.workouts.first().copy(id = "different"))), true, false, now, zone)
        assertNotEquals(first.items.first().clientId, other.items.single().clientId)
    }
    @Test fun selectedTypesAndLocalDayOffsetsAreRespected() {
        assertTrue(HealthSharePlanner.prepare(document(), false, false, now, zone).items.isEmpty())
        assertTrue(HealthSharePlanner.prepare(document(), false, true, now, zone).items.single() is HealthItem.Meal)
        val data = document().copy(nutrition = document().nutrition.copy(meals = listOf(document().nutrition.meals.single().copy(date = "2026-03-08"))))
        val meal = HealthSharePlanner.prepare(data, false, true, now, ZoneId.of("America/New_York")).items.single()
        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), meal.start)
    }
}
