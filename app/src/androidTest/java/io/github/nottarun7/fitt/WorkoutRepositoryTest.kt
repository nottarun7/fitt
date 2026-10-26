package io.github.nottarun7.fitt

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nottarun7.fitt.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = "test-${UUID.randomUUID()}.db"
    private lateinit var database: WorkoutDatabase
    private lateinit var repository: WorkoutRepository
    private var now = 1000L
    @get:Rule val schema = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WorkoutDatabase::class.java)

    @Before fun setUp() { reopen() }
    private fun reopen() {
        if (::database.isInitialized) database.close()
        database = WorkoutDatabase.open(context, file)
        repository = WorkoutRepository(database) { now }
    }
    @After fun tearDown() { database.close(); context.deleteDatabase(file) }

    private fun routine(name: String = "Strength A") = RoutineDraft(name = name, exercises = listOf(
        RoutineExerciseDraft(choice = ExerciseChoice("machine-chest-press", "Chest press")),
        RoutineExerciseDraft(choice = ExerciseChoice("l-sit", "L-sit", TrackingMetric.SECONDS), targetValue = "15"),
    ))

    @Test fun routineEditsPreserveOrderAndValidationIsAtomic() = runBlocking {
        val draft = routine()
        repository.saveRoutine(draft)
        repository.saveRoutine(draft.copy(name = "Renamed", exercises = draft.exercises.reversed()))
        val saved = database.workouts().routine(draft.id)!!
        assertEquals(listOf("L-sit", "Chest press"), saved.orderedExercises.map { it.name })
        rejects { repository.saveRoutine(draft.copy(name = "", exercises = emptyList())) }
        assertEquals("Renamed", database.workouts().routine(draft.id)!!.routine.name)
        assertEquals(2, database.workouts().routine(draft.id)!!.exercises.size)
    }

    @Test fun sessionSnapshotsSurviveRoutineEditsAndDeletion() = runBlocking {
        val draft = routine()
        repository.saveRoutine(draft)
        val id = repository.startWorkout(draft.id)
        repository.saveRoutine(draft.copy(name = "Changed", exercises = emptyList()))
        repository.deleteRoutine(draft.id)
        val recovered = database.workouts().session(id)!!
        assertEquals("Strength A", recovered.session.name)
        assertEquals(listOf("Chest press", "L-sit"), recovered.orderedExercises.map { it.exercise.name })
        assertNull(database.workouts().routine(draft.id))
    }

    @Test fun closingAndReopeningDatabaseRecoversSetsAndUnfinishedInput() = runBlocking {
        val draft = routine()
        repository.saveRoutine(draft)
        val id = repository.startWorkout(draft.id)
        val exercise = database.workouts().session(id)!!.orderedExercises.first().exercise
        repository.logSet(exercise.id, SetInput("8", "42.5", "LB", true))
        repository.saveDraft(exercise.id, SetInput("not-yet-valid", "12.", "KG", false))
        reopen()
        val recovered = repository.active.first()!!
        assertEquals(id, recovered.session.id)
        val row = recovered.orderedExercises.first()
        assertEquals(42.5, row.sets.single().weight!!, 0.0)
        assertEquals("LB", row.sets.single().unit)
        assertTrue(row.sets.single().assistance)
        assertEquals("not-yet-valid", row.draft.value)
        assertEquals("12.", row.draft.weight)
    }

    @Test fun concurrentStartRequestsResumeOneActiveWorkout() = runBlocking {
        val ids = coroutineScope { (1..8).map { async { repository.startWorkout() } }.awaitAll() }
        assertEquals(1, ids.distinct().size)
        assertEquals(ids.first(), repository.active.first()!!.session.id)
    }

    @Test fun finishingPublishesHistoryClearsDraftsAndAllowsNextWorkout() = runBlocking {
        val draft = routine()
        repository.saveRoutine(draft)
        val id = repository.startWorkout(draft.id)
        val exercise = database.workouts().session(id)!!.orderedExercises.last().exercise
        repository.logSet(exercise.id, SetInput("20"))
        now = 5000L
        repository.finishWorkout(id)
        assertNull(repository.active.first())
        val finished = repository.history.first().single()
        assertEquals(5000L, finished.session.completedAt)
        assertEquals(1, finished.setCount)
        assertEquals(1, finished.exerciseCount)
        assertTrue(database.workouts().session(id)!!.exercises.all { it.drafts.isEmpty() })
        rejects { repository.logSet(exercise.id, SetInput("30")) }
        assertNotEquals(id, repository.startWorkout())
    }

    @Test fun emptyWorkoutsCannotFinishAndUnknownRoutinesDoNotBecomeFreestyle() = runBlocking {
        rejects { repository.startWorkout("missing-routine") }
        assertNull(repository.active.first())
        val id = repository.startWorkout()
        rejects { repository.finishWorkout(id) }
        assertNotNull(repository.active.first())
    }

    @Test fun loggedSetsCanBeCorrectedDeletedAndAppendedInOrder() = runBlocking {
        val id = repository.startWorkout()
        repository.addExercise(id, ExerciseChoice(null, "Custom row"))
        val exercise = database.workouts().session(id)!!.exercises.single().exercise
        repository.logSet(exercise.id, SetInput("5", "0"))
        repository.logSet(exercise.id, SetInput("6", "25", "LB"))
        var sets = database.workouts().session(id)!!.exercises.single().orderedSets
        repository.editSet(sets.first().id, SetInput("8", "10.5", "KG", true))
        repository.deleteSet(sets.last().id)
        repository.logSet(exercise.id, SetInput("10"))
        sets = database.workouts().session(id)!!.exercises.single().orderedSets
        assertEquals(listOf(8, 10), sets.map { it.value })
        assertEquals(10.5, sets.first().weight!!, 0.0)
        assertTrue(sets.first().assistance)
        assertNull(sets.last().weight)
        assertEquals(2, sets.map { it.position }.distinct().size)
    }

    @Test fun invalidSetsDoNotWriteAnyRows() = runBlocking {
        val id = repository.startWorkout()
        repository.addExercise(id, ExerciseChoice(null, "Bodyweight movement"))
        val exercise = database.workouts().session(id)!!.exercises.single().exercise
        rejects { repository.logSet(exercise.id, SetInput("0", "10")) }
        rejects { repository.logSet(exercise.id, SetInput("5", "NaN")) }
        assertEquals(0, database.workouts().session(id)!!.setCount)
    }

    @Test fun discardingCascadesSetsAndDraftsButKeepsTheRoutine() = runBlocking {
        val draft = routine()
        repository.saveRoutine(draft)
        val id = repository.startWorkout(draft.id)
        val exercise = database.workouts().session(id)!!.exercises.first().exercise
        repository.logSet(exercise.id, SetInput("8", "25"))
        repository.discardWorkout(id)
        assertNull(database.workouts().session(id))
        assertNull(database.workouts().exercise(exercise.id))
        assertNotNull(database.workouts().routine(draft.id))
        assertTrue(repository.history.first().isEmpty())
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM logged_sets").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM set_drafts").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    @Test fun exportedVersionOneSchemaOpensWithoutResettingExistingRows() = runBlocking {
        database.close()
        schema.createDatabase(file, 1).apply {
            execSQL("INSERT INTO routines(id, name, updatedAt) VALUES ('existing', 'Keep this routine', 1000)")
            close()
        }
        reopen()
        assertEquals("Keep this routine", database.workouts().routine("existing")!!.routine.name)
    }

    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected validation to reject this operation") }
        catch (_: IllegalArgumentException) { /* Expected; state assertions follow. */ }
    }
}
