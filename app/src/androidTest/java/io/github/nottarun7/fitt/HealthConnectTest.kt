package io.github.nottarun7.fitt

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nottarun7.fitt.health.*
import io.github.nottarun7.fitt.interchange.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HealthConnectTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun realProviderWritesUpdatesAndReadsOwnRecordsWithoutDuplicates(): Unit = runBlocking {
        val app = ui.activity.application as FittApplication
        val gateway = AndroidHealthGateway(app)
        Assume.assumeTrue("Health Connect provider is required for this integration test", gateway.availability() == HealthConnectClient.SDK_AVAILABLE)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        HealthAccess.all.forEach { automation.grantRuntimePermission(app.packageName, it) }
        assertTrue(gateway.permissions().containsAll(HealthAccess.all))
        val now = Instant.now(); val zone = ZoneId.systemDefault(); val key = UUID.randomUUID().toString()
        val meal = BackupMeal("meal-$key", now.atZone(zone).toLocalDate().toString(), "Fitt synthetic integration test", "", 0,
            listOf(BackupPortion("item-$key", 0, null, "Synthetic food", "1 test serving", 1.5, 200.0, 10.0, 25.0, 6.0)))
        val data = BackupDocument(0, emptyList(), listOf(BackupWorkout("workout-$key", "Fitt synthetic integration test", null,
            now.minusSeconds(1200).toEpochMilli(), now.minusSeconds(600).toEpochMilli(), emptyList())), nutrition = BackupNutrition(meals = listOf(meal)))
        val plan = HealthSharePlanner.prepare(data, true, true, now, zone)
        val workout = plan.items.filterIsInstance<HealthItem.Workout>().single(); val nutrition = plan.items.filterIsInstance<HealthItem.Meal>().single()
        val client = HealthConnectClient.getOrCreate(app)
        try {
            gateway.write(plan.items, 100, zone)
            gateway.write(plan.items.map { if (it is HealthItem.Meal) it.copy(totals = it.totals.copy(calories = 450.0)) else it }, 101, zone)
            val range = TimeRangeFilter.between(now.minusSeconds(172800), now.plusSeconds(1))
            val origin = setOf(DataOrigin(app.packageName))
            val workouts = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range, dataOriginFilter = origin)).records.filter { it.metadata.clientRecordId == workout.clientId }
            val meals = client.readRecords(ReadRecordsRequest(NutritionRecord::class, range, dataOriginFilter = origin)).records.filter { it.metadata.clientRecordId == nutrition.clientId }
            assertEquals(1, workouts.size); assertEquals(1, meals.size)
            assertEquals(450.0, meals.single().energy!!.inKilocalories, 0.0)
            assertEquals(101, meals.single().metadata.clientRecordVersion)
            // The aggregate call must also succeed with foreground step access; null is valid on an empty emulator.
            gateway.steps(now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant(), now)
        } finally {
            // Remove only the synthetic IDs from this test, preserving any pre-existing Fitt/other-app health data.
            runCatching { client.deleteRecords(ExerciseSessionRecord::class, emptyList(), listOf(workout.clientId)) }
            runCatching { client.deleteRecords(NutritionRecord::class, emptyList(), listOf(nutrition.clientId)) }
        }
    }
    @Test fun sharingVersionsPersistAndIncreaseEvenIfTheClockMovesBackward() {
        val app = ui.activity.application as FittApplication
        val first = HealthVersionStore(app).next(1000)
        val second = HealthVersionStore(app).next(1)
        assertTrue(second > first)
    }
}
