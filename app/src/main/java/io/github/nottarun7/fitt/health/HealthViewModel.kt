package io.github.nottarun7.fitt.health

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nottarun7.fitt.data.BackupStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId

data class HealthUiState(val availability: Int = 0, val granted: Set<String> = emptySet(), val busy: Boolean = false,
    val steps: Long? = null, val message: String? = null, val workouts: Boolean = true, val meals: Boolean = true)

class HealthViewModel(private val gateway: HealthGateway, private val backups: BackupStore, private val versions: HealthVersionStore) : ViewModel() {
    private val mutable = MutableStateFlow(HealthUiState())
    val state = mutable.asStateFlow()
    private fun task(failureMessage: String = "Could not refresh Health Connect. Check its availability and access, then retry. Fitt data is unchanged.", work: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { work() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { mutable.update { it.copy(granted = emptySet(), steps = null, message = "Access changed. Reconnect or review Health Connect permissions, then retry.") } }
            catch (_: Exception) { mutable.update { it.copy(message = failureMessage) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun refresh() = task {
        val available = gateway.availability()
        mutable.update { it.copy(availability = available, steps = null, granted = emptySet()) }
        if (available == HealthConnectClient.SDK_AVAILABLE) {
            val granted = gateway.permissions()
            mutable.update { it.copy(granted = granted) }
            if (HealthAccess.steps in granted) {
                val now = Instant.now(); val start = now.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
                val steps = gateway.steps(start, now)
                mutable.update { it.copy(steps = steps) }
            }
        }
    }
    fun workouts(enabled: Boolean) { if (!mutable.value.busy) mutable.update { it.copy(workouts = enabled) } }
    fun meals(enabled: Boolean) { if (!mutable.value.busy) mutable.update { it.copy(meals = enabled) } }
    fun share() = task("Sharing stopped. Some batches may already be shared; retrying updates the same entries. Fitt data is unchanged.") {
        val granted = gateway.permissions(); val selected = mutable.value
        val workouts = selected.workouts && HealthAccess.exercise in granted
        val meals = selected.meals && HealthAccess.nutrition in granted
        require(workouts || meals)
        val zone = ZoneId.systemDefault(); val now = Instant.now()
        val plan = HealthSharePlanner.prepare(backups.export(), workouts, meals, now, zone)
        val version = versions.next(now.toEpochMilli())
        for (batch in plan.items.chunked(100)) gateway.write(batch, version, zone)
        val workoutsSent = plan.items.count { it is HealthItem.Workout }; val mealsSent = plan.items.count { it is HealthItem.Meal }
        mutable.update { it.copy(granted = granted, message = "Shared or updated $workoutsSent workouts and $mealsSent meals. Skipped ${plan.skippedWorkouts} workouts and ${plan.skippedMeals} meals with future/unsupported times or values. Templates and active workouts are not shared.") }
    }
    fun removeShared() = task("Could not finish removal. Some shared copies may already be removed; retry with the required write access. Local Fitt data is unchanged.") {
        val granted = gateway.permissions()
        val workouts = HealthAccess.exercise in granted; val meals = HealthAccess.nutrition in granted
        require(workouts || meals)
        gateway.remove(workouts, meals)
        mutable.update { it.copy(granted = granted, message = "Removed Fitt's ${if (workouts && meals) "workout and meal" else if (workouts) "workout" else "meal"} copies from Health Connect. Local Fitt data is unchanged.") }
    }
    companion object {
        fun factory(gateway: HealthGateway, backups: BackupStore, versions: HealthVersionStore) = viewModelFactory {
            initializer { HealthViewModel(gateway, backups, versions) }
        }
    }
}
