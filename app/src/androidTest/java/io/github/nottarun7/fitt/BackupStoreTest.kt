package io.github.nottarun7.fitt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.data.*
import io.github.nottarun7.fitt.interchange.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = mutableListOf<String>()
    private lateinit var database: WorkoutDatabase
    private lateinit var repository: WorkoutRepository
    private lateinit var backups: BackupStore
    private var time = 1000L

    @Before fun setUp() {
        database = freshDatabase()
        repository = WorkoutRepository(database) { time }
        backups = BackupStore(database) { 9000L }
    }
    @After fun tearDown() { database.close(); files.forEach { context.deleteDatabase(it) } }
    private fun freshDatabase(): WorkoutDatabase {
        val name = "backup-test-${UUID.randomUUID()}.db"
        files.add(name)
        return WorkoutDatabase.open(context, name)
    }

    private suspend fun seed(): BackupDocument {
        val routine = RoutineDraft(name = "Routine, \"A\" शक्ति", exercises = listOf(
            RoutineExerciseDraft(choice = ExerciseChoice("machine-chest-press", "Chest press")),
            RoutineExerciseDraft(choice = ExerciseChoice("l-sit", "L-sit", TrackingMetric.SECONDS, true, true), targetValue = "20"),
        ))
        repository.saveRoutine(routine)
        repository.saveRoutine(RoutineDraft(name = "Empty routine"))
        val finished = repository.startWorkout(routine.id)
        val first = database.workouts().session(finished)!!.orderedExercises
        repository.logSet(first[0].exercise.id, SetInput("8", "42.5"))
        repository.logSet(first[1].exercise.id, SetInput("20", "0", "LB", true))
        time = 2000L
        repository.finishWorkout(finished)
        time = 3000L
        val active = repository.startWorkout(routine.id)
        val row = database.workouts().session(active)!!.orderedExercises.first()
        repository.logSet(row.exercise.id, SetInput("5", "20", "LB", true))
        repository.saveDraft(row.exercise.id, SetInput("not ready", "12.", "KG", false))
        return backups.export()
    }

    @Test fun allFormatsRestoreEveryStoredFieldAndSurviveDatabaseReopen() = runBlocking {
        val source = seed()
        BackupFileFormat.entries.forEach { format ->
            val decoded = BackupFiles.decode(BackupFiles.encode(source, format)).document
            val target = freshDatabase()
            val name = files.last()
            try {
                val store = BackupStore(target) { 9000L }
                store.apply(store.preview(decoded, ImportOptions(mode = ImportMode.RESTORE_EXACTLY)))
                assertEquals(source, store.export())
            } finally { target.close() }
            val reopened = WorkoutDatabase.open(context, name)
            try {
                assertEquals(source, BackupStore(reopened) { 9000L }.export())
                assertEquals("not ready", reopened.workouts().active()!!.orderedExercises.first().draft.value)
            } finally { reopened.close() }
        }
    }

    @Test fun repeatedIdenticalImportsNeverDuplicateRowsUnderAnyPolicy() = runBlocking {
        val source = seed()
        DuplicatePolicy.entries.forEach { policy ->
            val plan = backups.preview(source, ImportOptions(duplicates = policy))
            assertFalse(plan.hasChanges)
            backups.apply(plan)
            assertEquals(source, backups.export())
        }
    }

    @Test fun historyFromDeletedRoutinesRestoresWithoutRecreatingOrLosingTheSourceReference() = runBlocking {
        val source = seed()
        val originalId = source.workouts.first().routineId!!
        repository.deleteRoutine(originalId)
        val orphanedHistory = backups.export()
        val file = BackupFiles.encode(orphanedHistory, BackupFileFormat.ZIP)
        backups.apply(backups.preview(BackupDocument(9000, emptyList(), emptyList()), ImportOptions(mode = ImportMode.RESTORE_EXACTLY)))
        backups.apply(backups.preview(BackupFiles.decode(file).document))
        assertEquals(orphanedHistory, backups.export())
        assertNull(database.workouts().routine(originalId))
        assertEquals(originalId, database.workouts().allSessions().first().session.routineId)
    }

    @Test fun keepAndReplacePoliciesApplyWholeRecordsWithoutRewritingSnapshots() = runBlocking {
        val source = seed()
        val incoming = source.copy(routines = source.routines.map { it.copy(name = it.name + " renamed") },
            workouts = source.workouts.map { it.copy(name = it.name + " imported") })
        backups.apply(backups.preview(incoming))
        assertEquals(source, backups.export())
        backups.apply(backups.preview(incoming, ImportOptions(duplicates = DuplicatePolicy.USE_IMPORTED)))
        assertEquals(incoming, backups.export())
        assertEquals("Chest press", database.workouts().allSessions().first().orderedExercises.first().exercise.name)
    }

    @Test fun keepBothCreatesIndependentGraphsAndRewiresCopiedRoutineReferences() = runBlocking {
        val source = seed()
        val routine = source.routines.single { it.exercises.isNotEmpty() }
        val finished = source.workouts.single { it.completedAtEpochMillis != null }
        val incoming = source.copy(routines = listOf(routine.copy(name = "Imported routine")), workouts = listOf(finished.copy(name = "Imported workout")))
        val result = backups.apply(backups.preview(incoming, ImportOptions(duplicates = DuplicatePolicy.KEEP_BOTH)))
        assertEquals(1, result.addedRoutines)
        assertEquals(1, result.addedWorkouts)
        val restored = backups.export()
        val copiedRoutine = restored.routines.single { it.name == "Imported routine" }
        val copiedWorkout = restored.workouts.single { it.name == "Imported workout" }
        assertNotEquals(routine.id, copiedRoutine.id)
        assertEquals(copiedRoutine.id, copiedWorkout.routineId)
        assertTrue(copiedWorkout.exercises.flatMap { it.sets }.map { it.id }.intersect(finished.exercises.flatMap { it.sets }.map { it.id }.toSet()).isEmpty())
        assertTrue(restored.routines.contains(routine))
        assertTrue(restored.workouts.contains(finished))
    }

    @Test fun stalePreviewsDoNotOverwriteWorkSavedAfterPreview() = runBlocking {
        val source = seed()
        val incoming = source.copy(routines = emptyList(), workouts = emptyList())
        val plan = backups.preview(incoming, ImportOptions(mode = ImportMode.RESTORE_EXACTLY))
        repository.saveRoutine(RoutineDraft(name = "Saved after preview"))
        val actual = backups.export()
        var failure: Exception? = null
        try { backups.apply(plan) } catch (error: Exception) { failure = error }
        assertTrue(failure is StaleBackupPreviewException)
        assertEquals(actual, backups.export())
    }

    @Test fun databaseFailureRollsBackDeletedAndInsertedParentsAndChildren() = runBlocking {
        val source = seed()
        val incoming = source.copy(routines = source.routines.map { it.copy(name = "Replaced ${it.name}") },
            workouts = source.workouts.map { it.copy(name = "Replaced ${it.name}") })
        val plan = backups.preview(incoming, ImportOptions(mode = ImportMode.RESTORE_EXACTLY))
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER forced_backup_failure BEFORE INSERT ON logged_sets
            BEGIN SELECT RAISE(ABORT, 'forced_backup_failure'); END
        """.trimIndent())
        var failure: Exception? = null
        try { backups.apply(plan) } catch (error: Exception) { failure = error }
        assertNotNull(failure)
        assertTrue(failure.toString().contains("forced_backup_failure"))
        assertEquals(source, backups.export())
    }

    @Test fun unfinishedWorkoutsRequireTheSelectedConflictPolicy() = runBlocking {
        val source = seed()
        val active = source.workouts.single { it.completedAtEpochMillis == null }
        val incoming = source.copy(routines = emptyList(), workouts = listOf(active.copy(id = "imported-active")))
        val keep = backups.preview(incoming)
        assertTrue(keep.summary.activeConflict)
        backups.apply(keep)
        assertEquals(active.id, database.workouts().active()!!.session.id)
        val use = backups.preview(incoming, ImportOptions(activeWorkout = ActiveWorkoutPolicy.USE_IMPORTED))
        backups.apply(use)
        assertEquals("imported-active", database.workouts().active()!!.session.id)
        assertEquals(1, repository.history.first().size)
    }

    @Test fun exactEmptyRestoreClearsAllWorkoutRowsWithoutDroppingTheSchema() = runBlocking {
        seed()
        val empty = BackupDocument(9000, emptyList(), emptyList())
        val plan = backups.preview(empty, ImportOptions(mode = ImportMode.RESTORE_EXACTLY))
        assertEquals(2, plan.summary.removedRoutines)
        assertEquals(2, plan.summary.removedWorkouts)
        backups.apply(plan)
        assertEquals(empty, backups.export())
        listOf("routines", "routine_exercises", "sessions", "session_exercises", "logged_sets", "set_drafts").forEach { table ->
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }
}
