package io.github.nottarun7.fitt.data

import androidx.room.withTransaction
import java.util.UUID

data class ExerciseChoice(
    val catalogueId: String?,
    val name: String,
    val metric: TrackingMetric = TrackingMetric.REPS,
    val perSide: Boolean = false,
    val assistance: Boolean = false,
)

data class RoutineExerciseDraft(
    val id: String = UUID.randomUUID().toString(),
    val choice: ExerciseChoice,
    val targetSets: String = "3",
    val targetValue: String = if (choice.metric == TrackingMetric.SECONDS) "20" else "8",
)

data class RoutineDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val exercises: List<RoutineExerciseDraft> = emptyList(),
)

data class SetInput(val value: String, val weight: String = "", val unit: String = "KG", val assistance: Boolean = false) {
    fun validated(): Pair<Int, Double?> {
        val amount = value.trim().toIntOrNull()
        require(amount != null && amount > 0) { "Enter a positive whole number for reps or seconds." }
        val load = weight.trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()
        require(weight.isBlank() || (load != null && load.isFinite() && load >= 0)) {
            "Enter a non-negative load, or leave it empty for bodyweight/unrecorded load."
        }
        require(unit == "KG" || unit == "LB") { "Choose kg or lb." }
        return amount to load
    }
}

/** All multi-row changes are atomic. Session snapshots never depend on live routine rows. */
class WorkoutRepository(private val database: WorkoutDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = database.workouts()
    val routines = dao.observeRoutines()
    val active = dao.observeActive()
    val history = dao.observeHistory()
    fun session(id: String) = dao.observeSession(id)

    suspend fun saveRoutine(draft: RoutineDraft): String {
        require(draft.name.isNotBlank()) { "Give your routine a name." }
        draft.exercises.forEach {
            require(it.choice.name.isNotBlank()) { "Exercise names cannot be empty." }
            require((it.targetSets.toIntOrNull() ?: 0) > 0) { "Target sets must be a positive whole number." }
            require((it.targetValue.toIntOrNull() ?: 0) > 0) { "Target reps or seconds must be a positive whole number." }
        }
        require(draft.exercises.map { it.id }.distinct().size == draft.exercises.size) { "Duplicate exercise IDs." }
        database.withTransaction {
            dao.saveRoutine(RoutineEntity(draft.id, draft.name.trim(), clock()))
            dao.clearRoutineExercises(draft.id)
            dao.insertRoutineExercises(draft.exercises.mapIndexed { position, row ->
                RoutineExerciseEntity(row.id, draft.id, row.choice.catalogueId, row.choice.name.trim(),
                    row.choice.metric.name, position, row.targetSets.toInt(), row.targetValue.toInt(),
                    row.choice.perSide, row.choice.assistance)
            })
        }
        return draft.id
    }

    suspend fun deleteRoutine(id: String) = dao.deleteRoutine(id)

    suspend fun startWorkout(routineId: String? = null): String = database.withTransaction {
        dao.active()?.let { return@withTransaction it.session.id }
        val routine = routineId?.let { requireNotNull(dao.routine(it)) { "This routine no longer exists." } }
        require(routine == null || routine.exercises.isNotEmpty()) { "Add an exercise before starting this routine." }
        val sessionId = UUID.randomUUID().toString()
        dao.insertSession(SessionEntity(sessionId, routine?.routine?.name ?: "Freestyle workout", routineId, clock()))
        dao.insertSessionExercises(routine?.orderedExercises.orEmpty().map { row ->
            SessionExerciseEntity(UUID.randomUUID().toString(), sessionId, row.catalogueId, row.name, row.metric,
                row.position, row.targetSets, row.targetValue, row.perSide, row.assistance)
        })
        sessionId
    }

    suspend fun addExercise(sessionId: String, choice: ExerciseChoice) = database.withTransaction {
        val session = requireActive(sessionId)
        require(choice.name.isNotBlank()) { "Give the exercise a name." }
        dao.insertSessionExercises(listOf(SessionExerciseEntity(
            UUID.randomUUID().toString(), sessionId, choice.catalogueId, choice.name.trim(), choice.metric.name,
            (session.exercises.maxOfOrNull { it.exercise.position } ?: -1) + 1,
            3, if (choice.metric == TrackingMetric.SECONDS) 20 else 8, choice.perSide, choice.assistance,
        )))
    }

    suspend fun saveDraft(exerciseId: String, input: SetInput) = database.withTransaction {
        val exercise = requireNotNull(dao.exercise(exerciseId)) { "Exercise no longer exists." }
        requireActive(exercise.sessionId)
        dao.saveDraft(SetDraftEntity(exerciseId, input.value, input.weight, input.unit, input.assistance))
    }

    suspend fun logSet(exerciseId: String, input: SetInput) = database.withTransaction {
        val (value, weight) = input.validated()
        val exercise = requireNotNull(dao.exercise(exerciseId)) { "Exercise no longer exists." }
        requireActive(exercise.sessionId)
        dao.insertSet(SetEntity(UUID.randomUUID().toString(), exerciseId, dao.nextSetPosition(exerciseId),
            value, weight, input.unit, input.assistance))
        dao.saveDraft(SetDraftEntity(exerciseId, input.value, input.weight, input.unit, input.assistance))
    }

    suspend fun editSet(id: String, input: SetInput) = database.withTransaction {
        val (value, weight) = input.validated()
        val set = requireNotNull(dao.set(id)) { "Set no longer exists." }
        val exercise = requireNotNull(dao.exercise(set.exerciseId))
        requireActive(exercise.sessionId)
        dao.updateSet(set.copy(value = value, weight = weight, unit = input.unit, assistance = input.assistance))
    }

    suspend fun deleteSet(id: String) = database.withTransaction {
        val set = requireNotNull(dao.set(id)) { "Set no longer exists." }
        val exercise = requireNotNull(dao.exercise(set.exerciseId))
        requireActive(exercise.sessionId)
        dao.deleteSet(set)
    }

    suspend fun finishWorkout(id: String) = database.withTransaction {
        val session = requireActive(id)
        require(session.setCount > 0) { "Log at least one set before finishing, or discard this workout." }
        dao.finishSession(id, maxOf(clock(), session.session.startedAt))
        dao.clearDrafts(id)
    }

    suspend fun discardWorkout(id: String) = database.withTransaction {
        requireActive(id)
        dao.discardSession(id)
    }

    private suspend fun requireActive(id: String): SessionWithExercises {
        val session = requireNotNull(dao.session(id)) { "This workout no longer exists." }
        require(session.session.activeSlot == 1 && session.session.completedAt == null) { "This workout is already finished." }
        return session
    }
}
