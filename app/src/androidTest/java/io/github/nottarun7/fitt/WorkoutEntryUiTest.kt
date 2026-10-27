package io.github.nottarun7.fitt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.interchange.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutEntryUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ui.activity.application as FittApplication

    @Before fun seed(): Unit = runBlocking {
        // A misconfigured runner must never clear a real user's database.
        check(app is FittTestApplication)
        withContext(Dispatchers.IO) { app.database.clearAllTables() }
        val exercise = BackupExercise("entry-exercise", "Machine press", 0, BackupMetric.REPS, 3, 8,
            "machine-chest-press", false, false)
        val workout = BackupWorkout("entry-workout", "Entry safety session", null, 100, null,
            listOf(BackupLoggedExercise(exercise, listOf(BackupSet("entry-set", 0, 8, 30.0, WeightUnit.KG, false)), null)))
        app.backups.apply(app.backups.preview(BackupDocument(1000, emptyList(), listOf(workout))))
        Unit
    }

    @After fun cleanup() = runBlocking {
        check(app is FittTestApplication)
        withContext(Dispatchers.IO) { app.database.clearAllTables() }
    }

    private fun openWorkout() {
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("resumeWorkout").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("resumeWorkout").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("workoutList").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun repsAndLoadFocusHideSessionEndActionsAndDoneKeepsTheDraft() {
        openWorkout()
        ui.onNodeWithTag("finishWorkout").assertIsEnabled()
        ui.onNodeWithTag("workoutList").performScrollToNode(hasTestTag("value-entry-exercise"))
        ui.onNodeWithTag("value-entry-exercise").performClick().performTextReplacement("12")
        ui.onNodeWithTag("finishWorkout").assertDoesNotExist()
        ui.onNodeWithContentDescription("Workout options").assertDoesNotExist()
        ui.onNodeWithTag("weight-entry-exercise").performScrollTo().performClick().performTextReplacement("42.")
        ui.onNodeWithTag("finishWorkout").assertDoesNotExist()
        ui.onNodeWithTag("doneEnteringSet").performClick()
        ui.waitUntil(10_000) { ui.onAllNodes(hasTestTag("finishWorkout") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val active = runBlocking { app.database.workouts().active()!! }
        assertEquals(1, active.setCount)
        assertEquals("12", active.orderedExercises.single().draft.value)
        assertEquals("42.", active.orderedExercises.single().draft.weight)
        assertNull(active.session.completedAt)
        ui.onNodeWithTag("confirmFinishWorkout").assertDoesNotExist()
        ui.onNodeWithTag("finishWorkout").performClick()
        ui.onNodeWithTag("unloggedSetWarning").assertTextEquals("Unlogged entries will not be added. Choose Keep training and log the set first.")
        ui.onNodeWithText("Keep training").performClick()
        val kept = runBlocking { app.database.workouts().active()!! }
        assertEquals("12", kept.orderedExercises.single().draft.value)
        assertEquals("42.", kept.orderedExercises.single().draft.weight)
        assertNull(kept.session.completedAt)
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("workoutList").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("workoutList").performScrollToNode(hasTestTag("weight-entry-exercise"))
        ui.onNodeWithTag("weight-entry-exercise").assertTextContains("42.")
        assertNotNull(runBlocking { app.database.workouts().active() })
    }

    @Test fun finishingOutsideEntryStillNeedsExplicitConfirmation() {
        openWorkout()
        ui.onNodeWithTag("finishWorkout").performClick()
        ui.onNodeWithTag("confirmFinishWorkout").assertExists()
        ui.onNodeWithTag("unloggedSetWarning").assertDoesNotExist()
        assertNotNull(runBlocking { app.database.workouts().active() })
        ui.onNodeWithText("Keep training").performClick()
        assertNotNull(runBlocking { app.database.workouts().active() })
        ui.onNodeWithTag("finishWorkout").performClick()
        ui.onNodeWithTag("confirmFinishWorkout").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.workouts().session("entry-workout")?.session?.completedAt != null } }
        assertNull(runBlocking { app.database.workouts().active() })
        assertEquals(1, runBlocking { app.database.workouts().session("entry-workout")!!.setCount })
    }
}
