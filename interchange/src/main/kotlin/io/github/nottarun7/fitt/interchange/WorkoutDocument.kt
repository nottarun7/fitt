package io.github.nottarun7.fitt.interchange

import kotlinx.serialization.Serializable
import kotlinx.serialization.Required

const val WORKOUT_DOCUMENT_FORMAT = "fitt.workouts"
const val WORKOUT_SCHEMA_VERSION = 1

/** A portable snapshot. Timestamps are UTC epoch milliseconds; IDs are stable strings. */
@Serializable
data class WorkoutDocument(
    val exportedAtEpochMillis: Long,
    val routines: List<WorkoutRoutine>,
    val workouts: List<WorkoutSession>,
    val format: String = WORKOUT_DOCUMENT_FORMAT,
    val schemaVersion: Int = WORKOUT_SCHEMA_VERSION,
)

@Serializable
data class WorkoutRoutine(
    val id: String,
    val name: String,
    val exercises: List<PlannedExercise>,
)

@Serializable
data class PlannedExercise(
    val id: String,
    val name: String,
    val targetSets: Int,
    val targetReps: Int? = null,
)

/** Exercise names and sets are snapshots: changing/deleting a routine must not rewrite history. */
@Serializable
data class WorkoutSession(
    val id: String,
    val name: String,
    val startedAtEpochMillis: Long,
    val exercises: List<LoggedExercise>,
    val routineId: String? = null,
    val completedAtEpochMillis: Long? = null,
)

@Serializable
data class LoggedExercise(
    val id: String,
    val name: String,
    val sets: List<LoggedSet>,
)

/** Null weight means bodyweight/unrecorded load; zero is a valid explicit load. */
@Serializable
data class LoggedSet(
    val reps: Int,
    val weight: Double? = null,
    @Required
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@Serializable
enum class WeightUnit { KG, LB }
