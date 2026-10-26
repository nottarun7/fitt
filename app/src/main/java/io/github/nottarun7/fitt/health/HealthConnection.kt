package io.github.nottarun7.fitt.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId

interface HealthGateway {
    fun availability(): Int
    suspend fun permissions(): Set<String>
    suspend fun steps(start: Instant, end: Instant): Long?
    suspend fun write(items: List<HealthItem>, version: Long, zone: ZoneId)
    suspend fun remove(workouts: Boolean, meals: Boolean)
}

object HealthAccess {
    val exercise = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val nutrition = HealthPermission.getWritePermission(NutritionRecord::class)
    val steps = HealthPermission.getReadPermission(StepsRecord::class)
    val all = setOf(exercise, nutrition, steps)
}

class AndroidHealthGateway(private val context: Context) : HealthGateway {
    private fun client() = HealthConnectClient.getOrCreate(context)
    override fun availability(): Int = if (Build.VERSION.SDK_INT < 28) HealthConnectClient.SDK_UNAVAILABLE else HealthConnectClient.getSdkStatus(context)
    override suspend fun permissions(): Set<String> = client().permissionController.getGrantedPermissions()
    override suspend fun steps(start: Instant, end: Instant): Long? {
        if (end <= start) return null
        return client().aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)))[StepsRecord.COUNT_TOTAL]
    }
    override suspend fun write(items: List<HealthItem>, version: Long, zone: ZoneId) {
        val records: List<Record> = items.map<HealthItem, Record> { item ->
            val metadata = Metadata.manualEntry(clientRecordId = item.clientId, clientRecordVersion = version)
            val startOffset = zone.rules.getOffset(item.start); val endOffset = zone.rules.getOffset(item.end)
            when (item) {
                is HealthItem.Workout -> { val record: Record = ExerciseSessionRecord(item.start, startOffset, item.end, endOffset, metadata,
                    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, item.name,
                    "Recorded in Fitt: ${item.exercises} exercises, ${item.sets} sets. Reps, loads, assistance, and detailed exercise history remain in Fitt."); record }
                is HealthItem.Meal -> { val record: Record = NutritionRecord(startTime = item.start, startZoneOffset = startOffset, endTime = item.end,
                    endZoneOffset = endOffset, metadata = metadata, name = item.name, energy = Energy.kilocalories(item.totals.calories),
                    protein = Mass.grams(item.totals.protein), totalCarbohydrate = Mass.grams(item.totals.carbs), totalFat = Mass.grams(item.totals.fat)); record }
            }
        }
        client().insertRecords(records)
    }
    override suspend fun remove(workouts: Boolean, meals: Boolean) {
        // Health Connect restricts time-range deletions to this application's own records.
        val range = TimeRangeFilter.after(Instant.EPOCH.minusSeconds(1))
        if (workouts) client().deleteRecords(ExerciseSessionRecord::class, range)
        if (meals) client().deleteRecords(NutritionRecord::class, range)
    }
}

class HealthVersionStore(context: Context) {
    private val preferences = context.getSharedPreferences("health_connect_device", Context.MODE_PRIVATE)
    @Synchronized fun next(now: Long): Long {
        val old = preferences.getLong("version", 0)
        check(old < Long.MAX_VALUE) { "Health Connect version counter is exhausted." }
        val version = maxOf(now, old + 1)
        check(preferences.edit().putLong("version", version).commit()) { "Could not save the Health Connect version." }
        return version
    }
}
