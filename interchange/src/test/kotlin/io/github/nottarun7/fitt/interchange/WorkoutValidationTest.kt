package io.github.nottarun7.fitt.interchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutValidationTest {
    private val routine = WorkoutRoutine("routine", "Strength", listOf(PlannedExercise("squat", "Squat", 3, 5)))
    private val exercise = LoggedExercise("logged-squat", "Squat", listOf(LoggedSet(5, 42.5)))
    private val workout = WorkoutSession("workout", "Strength", 1000, listOf(exercise), routine.id, 2000)
    private val document = WorkoutDocument(3000, listOf(routine), listOf(workout))

    @Test
    fun `valid routine and workout have no issues`() {
        assertTrue(document.validate().isEmpty())
    }

    @Test
    fun `routine edits and deletion do not invalidate historical snapshots`() {
        assertTrue(document.copy(routines = emptyList()).validate().isEmpty())
        val renamed = document.copy(routines = listOf(routine.copy(name = "Renamed", exercises = emptyList())))
        assertTrue(renamed.validate().isEmpty())
        assertEquals("Squat", renamed.workouts.single().exercises.single().name)
    }

    @Test
    fun `draft routines and unfinished workouts can be empty`() {
        val draft = document.copy(
            routines = listOf(routine.copy(exercises = emptyList())),
            workouts = listOf(workout.copy(exercises = emptyList(), completedAtEpochMillis = null, routineId = null)),
        )
        assertTrue(draft.validate().isEmpty())
    }

    @Test
    fun `duplicate routine and workout IDs are rejected`() {
        val issues = document.copy(routines = listOf(routine, routine), workouts = listOf(workout, workout)).validate()
        assertEquals(setOf("routines[1].id", "workouts[1].id"), issues.map { it.path }.toSet())
    }

    @Test
    fun `exercise IDs must be unique within their parent`() {
        val issues = document.copy(
            routines = listOf(routine.copy(exercises = listOf(routine.exercises.single(), routine.exercises.single()))),
            workouts = listOf(workout.copy(exercises = listOf(exercise, exercise))),
        ).validate()
        assertEquals(setOf("routines[0].exercises[1].id", "workouts[0].exercises[1].id"), issues.map { it.path }.toSet())
    }

    @Test
    fun `different sessions may reuse an exercise ID`() {
        assertTrue(document.copy(workouts = listOf(workout, workout.copy(id = "workout-2"))).validate().isEmpty())
    }

    @Test
    fun `blank identities and names are rejected`() {
        val issues = document.copy(
            routines = listOf(routine.copy(id = " ", name = "\t")),
            workouts = listOf(workout.copy(name = "", routineId = " ", exercises = listOf(exercise.copy(name = "")))),
        ).validate()
        assertEquals(5, issues.size)
    }

    @Test
    fun `targets must be positive when provided`() {
        val invalid = routine.exercises.single().copy(targetSets = 0, targetReps = -1)
        val issues = document.copy(routines = listOf(routine.copy(exercises = listOf(invalid)))).validate()
        assertEquals(setOf("routines[0].exercises[0].targetSets", "routines[0].exercises[0].targetReps"), issues.map { it.path }.toSet())
        assertTrue(document.copy(routines = listOf(routine.copy(
            exercises = listOf(routine.exercises.single().copy(targetReps = null)),
        ))).validate().isEmpty())
    }

    @Test
    fun `timestamps must be non negative and completion must follow start`() {
        assertFalse(document.copy(exportedAtEpochMillis = -1).validate().isEmpty())
        assertFalse(document.copy(workouts = listOf(workout.copy(startedAtEpochMillis = -1))).validate().isEmpty())
        val issues = document.copy(workouts = listOf(workout.copy(completedAtEpochMillis = 999))).validate()
        assertEquals(listOf("workouts[0].completedAtEpochMillis"), issues.map { it.path })
        assertTrue(document.copy(workouts = listOf(workout.copy(completedAtEpochMillis = 1000))).validate().isEmpty())
    }

    @Test
    fun `zero or negative reps are rejected`() {
        listOf(0, -1).forEach { reps ->
            assertFalse(withSet(LoggedSet(reps)).validate().isEmpty())
        }
    }

    @Test
    fun `negative infinite and NaN weights are rejected`() {
        listOf(-0.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { weight ->
            val issues = withSet(LoggedSet(5, weight)).validate()
            assertEquals(listOf("workouts[0].exercises[0].sets[0].weight"), issues.map { it.path })
        }
    }

    @Test
    fun `null and zero weights are valid without a unit conversion`() {
        listOf(LoggedSet(5), LoggedSet(5, 0.0), LoggedSet(5, 100.0, WeightUnit.LB)).forEach { set ->
            assertTrue(withSet(set).validate().isEmpty())
        }
    }

    private fun withSet(set: LoggedSet): WorkoutDocument = document.copy(
        workouts = listOf(workout.copy(exercises = listOf(exercise.copy(sets = listOf(set))))),
    )
}
