package io.github.nottarun7.fitt.interchange

data class ValidationIssue(val path: String, val message: String)

/** Reports all domain errors without changing or partially accepting the document. */
fun WorkoutDocument.validate(): List<ValidationIssue> = buildList {
    fun check(condition: Boolean, path: String, message: String) {
        if (!condition) add(ValidationIssue(path, message))
    }

    fun text(value: String, path: String) {
        check(value.isNotBlank(), path, "Must not be blank.")
    }

    fun ids(values: List<String>, path: String) {
        val seen = mutableSetOf<String>()
        values.forEachIndexed { index, id ->
            text(id, "$path[$index].id")
            check(seen.add(id), "$path[$index].id", "Duplicate ID in this collection.")
        }
    }

    check(format == WORKOUT_DOCUMENT_FORMAT, "format", "Not a Fitt workout document.")
    check(schemaVersion == WORKOUT_SCHEMA_VERSION, "schemaVersion", "Unsupported schema version.")
    check(exportedAtEpochMillis >= 0, "exportedAtEpochMillis", "Must not be negative.")

    ids(routines.map { it.id }, "routines")
    routines.forEachIndexed { routineIndex, routine ->
        val path = "routines[$routineIndex]"
        text(routine.name, "$path.name")
        ids(routine.exercises.map { it.id }, "$path.exercises")
        routine.exercises.forEachIndexed { exerciseIndex, exercise ->
            val exercisePath = "$path.exercises[$exerciseIndex]"
            text(exercise.name, "$exercisePath.name")
            check(exercise.targetSets > 0, "$exercisePath.targetSets", "Must be positive.")
            check(exercise.targetReps == null || exercise.targetReps > 0,
                "$exercisePath.targetReps", "Must be positive when specified.")
        }
    }

    ids(workouts.map { it.id }, "workouts")
    workouts.forEachIndexed { workoutIndex, workout ->
        val path = "workouts[$workoutIndex]"
        text(workout.name, "$path.name")
        workout.routineId?.let { text(it, "$path.routineId") }
        check(workout.startedAtEpochMillis >= 0, "$path.startedAtEpochMillis", "Must not be negative.")
        workout.completedAtEpochMillis?.let {
            check(it >= workout.startedAtEpochMillis,
                "$path.completedAtEpochMillis", "Must not precede the start time.")
        }
        // A source routine may no longer exist. History remains independently portable.
        ids(workout.exercises.map { it.id }, "$path.exercises")
        workout.exercises.forEachIndexed { exerciseIndex, exercise ->
            val exercisePath = "$path.exercises[$exerciseIndex]"
            text(exercise.name, "$exercisePath.name")
            exercise.sets.forEachIndexed { setIndex, set ->
                val setPath = "$exercisePath.sets[$setIndex]"
                check(set.reps > 0, "$setPath.reps", "Must be positive.")
                check(set.weight == null || (set.weight.isFinite() && set.weight >= 0),
                    "$setPath.weight", "Must be a finite, non-negative number when specified.")
            }
        }
    }
}
