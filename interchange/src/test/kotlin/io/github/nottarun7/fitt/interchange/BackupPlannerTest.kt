package io.github.nottarun7.fitt.interchange

import org.junit.Assert.*
import org.junit.Test

class BackupPlannerTest {
    private val fixture = backupFixture().canonical()
    private val empty = BackupDocument(0, emptyList(), emptyList())
    private val altered get() = fixture.copy(
        routines = fixture.routines.map { it.copy(name = it.name + " changed") },
        workouts = fixture.workouts.map { it.copy(name = it.name + " changed") },
    )

    @Test fun `fresh restore preserves all IDs values snapshots and drafts`() {
        val plan = BackupPlanner.plan(empty, fixture)
        assertEquals(fixture, plan.desired)
        assertEquals(2, plan.summary.addedRoutines)
        assertEquals(3, plan.summary.addedWorkouts)
        assertTrue(plan.hasChanges)
    }

    @Test fun `identical reimport is idempotent under all duplicate policies`() {
        DuplicatePolicy.entries.forEach { policy ->
            val plan = BackupPlanner.plan(fixture, fixture.copy(exportedAtEpochMillis = 10000), ImportOptions(duplicates = policy))
            assertFalse(plan.hasChanges)
            assertEquals(5, plan.summary.identicalDuplicates)
            assertEquals(5, plan.summary.skippedRecords)
        }
    }

    @Test fun `keep existing skips changed duplicate records in their entirety`() {
        val plan = BackupPlanner.plan(fixture, altered)
        assertEquals(fixture.copy(exportedAtEpochMillis = altered.exportedAtEpochMillis), plan.desired)
        assertEquals(5, plan.summary.changedDuplicates)
        assertFalse(plan.hasChanges)
    }

    @Test fun `use imported replaces complete records without deleting unrelated history`() {
        val extra = BackupWorkout("unrelated", "Unrelated", null, 0, 10, emptyList())
        val current = fixture.copy(workouts = fixture.workouts + extra)
        val plan = BackupPlanner.plan(current, altered, ImportOptions(duplicates = DuplicatePolicy.USE_IMPORTED))
        assertEquals(4, plan.desired.workouts.size)
        assertTrue(plan.desired.workouts.contains(extra))
        assertEquals(2, plan.summary.changedRoutines)
        assertEquals(3, plan.summary.changedWorkouts)
        assertEquals(0, plan.summary.removedWorkouts)
    }

    @Test fun `keep both remaps child IDs and points imported workouts to copied routines`() {
        // Use only finished workouts so both copies can coexist without the active-session decision.
        val current = fixture.copy(workouts = fixture.workouts.filter { it.completedAtEpochMillis != null })
        val incoming = altered.copy(workouts = altered.workouts.filter { it.completedAtEpochMillis != null })
        val plan = BackupPlanner.plan(current, incoming, ImportOptions(duplicates = DuplicatePolicy.KEEP_BOTH))
        assertEquals(4, plan.desired.routines.size)
        assertEquals(4, plan.desired.workouts.size)
        assertTrue(plan.summary.remappedChildIds > 0)
        assertTrue(plan.desired.validateBackup().isEmpty())
        val copiedRoutine = plan.desired.routines.single { it.name == incoming.routines.first { row -> row.id == "r-1" }.name }
        val linked = incoming.workouts.first().copy(id = "linked", routineId = "r-1")
        val linkedPlan = BackupPlanner.plan(current, incoming.copy(workouts = listOf(linked)), ImportOptions(duplicates = DuplicatePolicy.KEEP_BOTH))
        val linkedRoutine = linkedPlan.desired.routines.single { it.name == copiedRoutine.name }
        assertEquals(linkedRoutine.id, linkedPlan.desired.workouts.single { it.id == "linked" }.routineId)
    }

    @Test fun `child collisions with another parent are remapped and repeat imports stay idempotent`() {
        val incoming = fixture.copy(routines = listOf(fixture.routines.first { it.id == "r-1" }.copy(id = "new-routine")), workouts = emptyList())
        val first = BackupPlanner.plan(fixture, incoming)
        assertEquals(1, first.summary.remappedChildIds)
        assertFalse(BackupPlanner.plan(first.desired, incoming).hasChanges)
        assertEquals("rx-1", first.desired.routines.first { it.id == "r-1" }.exercises.single().id)
    }

    @Test fun `two active sessions keep current by default while finished history still imports`() {
        val incoming = fixture.copy(workouts = fixture.workouts.map { it.copy(id = "incoming-${it.id}") })
        val plan = BackupPlanner.plan(fixture, incoming)
        assertTrue(plan.summary.activeConflict)
        assertEquals("w-active", plan.desired.workouts.single { it.completedAtEpochMillis == null }.id)
        assertEquals(2, plan.summary.addedWorkouts)
        assertEquals(0, plan.summary.removedWorkouts)
    }

    @Test fun `explicit use imported active session replaces only the current unfinished session`() {
        val incoming = fixture.copy(routines = emptyList(), workouts = listOf(fixture.workouts.single { it.id == "w-active" }.copy(id = "new-active")))
        val plan = BackupPlanner.plan(fixture, incoming, ImportOptions(activeWorkout = ActiveWorkoutPolicy.USE_IMPORTED))
        assertEquals("new-active", plan.desired.workouts.single { it.completedAtEpochMillis == null }.id)
        assertEquals(1, plan.summary.removedWorkouts)
        assertTrue(plan.desired.workouts.any { it.id == "w-done" })
    }

    @Test fun `exact restore removes unrelated records and keeps original nested identifiers`() {
        val incoming = fixture.copy(routines = fixture.routines.take(1), workouts = fixture.workouts.take(1))
        val plan = BackupPlanner.plan(fixture, incoming, ImportOptions(mode = ImportMode.RESTORE_EXACTLY))
        assertEquals(incoming.canonical(), plan.desired)
        assertEquals(1, plan.summary.removedRoutines)
        assertEquals(2, plan.summary.removedWorkouts)
        assertEquals(0, plan.summary.remappedChildIds)
    }

    @Test fun `different IDs with matching names remain separate records`() {
        val incoming = empty.copy(routines = listOf(fixture.routines.first().copy(id = "another-id")))
        val plan = BackupPlanner.plan(fixture, incoming)
        assertEquals(1, plan.summary.addedRoutines)
        assertEquals(0, plan.summary.changedDuplicates)
    }

    @Test fun `an empty exact restore is explicit and invalid input cannot be planned`() {
        val plan = BackupPlanner.plan(fixture, empty, ImportOptions(mode = ImportMode.RESTORE_EXACTLY))
        assertTrue(plan.hasChanges)
        assertEquals(2, plan.summary.removedRoutines)
        assertEquals(3, plan.summary.removedWorkouts)
        assertThrows(BackupException::class.java) { BackupPlanner.plan(fixture, fixture.copy(schemaVersion = 99)) }
    }
}
