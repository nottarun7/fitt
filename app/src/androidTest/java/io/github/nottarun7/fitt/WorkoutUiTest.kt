package io.github.nottarun7.fitt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import io.github.nottarun7.fitt.interchange.*
import androidx.lifecycle.ViewModelProvider
import io.github.nottarun7.fitt.ui.WorkoutViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun backupsEntryOpensTheFileWorkflow() {
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Backups").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Backups").performClick()
        ui.onNodeWithText("Backups & files").assertExists()
        ui.onNodeWithTag("export-ZIP").assertExists()
        ui.onNodeWithTag("otherExportFormats").performClick()
        ui.onNodeWithTag("export-JSON").assertExists()
        ui.onNodeWithTag("export-CSV").assertExists()
    }

    @Test fun typingHidesMainNavigationAndDoneKeepsNutritionDraftUnsaved() {
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("mainNavigation").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("mainNavigation").assertIsDisplayed()
        val app = ui.activity.application as FittApplication
        val foodsBefore = runBlocking { app.database.nutrition().foods() }
        ui.onNodeWithText("Nutrition", substring = false).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("nutrition-Foods").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nutrition-Foods").performClick()
        ui.onNodeWithTag("newFood").performClick()
        ui.onNodeWithTag("nutritionEditor").performScrollToNode(hasTestTag("foodFat"))
        ui.onNodeWithTag("foodFat").performClick().performTextReplacement("10")
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("mainNavigation").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("mainNavigation").assertDoesNotExist()
        ui.onNodeWithTag("foodFat").performImeAction()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("mainNavigation").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("mainNavigation").assertIsDisplayed()
        ui.onNodeWithTag("nutritionEditor").assertExists()
        ui.onNodeWithTag("foodFat").assertTextContains("10")
        ui.onNodeWithTag("foodFat").assertIsNotFocused()
        assertEquals(foodsBefore, runBlocking { app.database.nutrition().foods() })
    }

    @Test fun importedPathAndReservedIdsRemainEditableAndHistoryCanOpen() {
        val routineId = "path/new%2F /?शक्ति"
        val workoutId = "history/part%2F#one"
        val app = ui.activity.application as FittApplication
        runBlocking {
            val imported = BackupDocument(1000,
                listOf(BackupRoutine(routineId, "Imported path routine", 1000, emptyList()),
                    BackupRoutine("new", "Imported reserved ID", 1000, emptyList())),
                listOf(BackupWorkout(workoutId, "Path history", null, 0, 1, emptyList())))
            app.backups.apply(app.backups.preview(imported))
        }
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("newRoutine").fetchSemanticsNodes().isNotEmpty() }
        val model = ViewModelProvider(ui.activity)[WorkoutViewModel::class.java]
        ui.waitUntil(10_000) { model.overview.value.routines.any { it.routine.id == routineId } }
        ui.onNodeWithTag("trainList").performScrollToNode(hasTestTag("routine-$routineId"))
        ui.onNode(hasText("Edit routine") and hasAnyAncestor(hasTestTag("routine-$routineId"))).performClick()
        ui.onNodeWithTag("routineName").assertTextContains("Imported path routine")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("routineName").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("routineName").assertTextContains("Imported path routine")
        val recreatedModel = ViewModelProvider(ui.activity)[WorkoutViewModel::class.java]
        ui.waitUntil(10_000) { !recreatedModel.overview.value.loading }
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("History").performClick()
        ui.onNodeWithTag("historyList").performScrollToNode(hasTestTag("history-$workoutId"))
        ui.onNodeWithTag("history-$workoutId").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("historyDetail").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Path history").assertExists()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Train").performClick()
        ui.onNodeWithTag("trainList").performScrollToNode(hasTestTag("routine-new"))
        ui.onNode(hasText("Edit routine") and hasAnyAncestor(hasTestTag("routine-new"))).performClick()
        ui.onNodeWithText("Edit routine").assertExists()
        ui.onNodeWithTag("routineName").assertTextContains("Imported reserved ID")
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithTag("newRoutine").performClick()
        ui.onNodeWithTag("routineName").performTextInput("Separate new routine")
        ui.onNodeWithTag("saveRoutine").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("newRoutine").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Imported reserved ID", runBlocking { app.database.workouts().routine("new")!!.routine.name })
    }

    @Test fun createRoutineLogCorrectRecoverAndFinishWorkout() {
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("newRoutine").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("newRoutine").performClick()
        ui.onNodeWithTag("routineName").performTextInput("MVP test routine")
        ui.onNodeWithTag("addRoutineExercise").performScrollTo().performClick()
        ui.onNodeWithTag("exerciseSearch").performTextInput("machine chest press")
        ui.onNodeWithTag("exercise-machine-chest-press").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("exerciseSearch").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("routineName").assertIsNotFocused()
        ui.onNodeWithTag("addRoutineExercise").assertIsDisplayed()
        ui.onNodeWithTag("saveRoutine").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("newRoutine").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("trainList").performScrollToNode(hasText("Start workout"))
        ui.onNodeWithText("Start workout").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("In progress").fetchSemanticsNodes().isNotEmpty() }
        val database = (ui.activity.application as FittApplication).database
        val exercise = runBlocking { database.workouts().active()!!.orderedExercises.first().exercise }
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("workoutList").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("workoutList").performScrollToNode(hasTestTag("value-${exercise.id}"))
        ui.onNodeWithTag("value-${exercise.id}").performTextInput("8")
        ui.onNodeWithTag("weight-${exercise.id}").performTextInput("42.5")
        ui.onNodeWithTag("logSet-${exercise.id}").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("8 reps · 42.5 kg").fetchSemanticsNodes().isNotEmpty() }
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("8 reps · 42.5 kg").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("8 reps · 42.5 kg").assertExists()
        val set = runBlocking { database.workouts().active()!!.exercises.first().sets.single() }
        ui.onNodeWithTag("editSet-${set.id}").performScrollTo().performClick()
        ui.onNodeWithTag("value-edit").performTextReplacement("10")
        ui.onNodeWithTag("saveSetChanges").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("10 reps · 42.5 kg").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(10_000) { ui.onAllNodes(hasTestTag("finishWorkout") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("finishWorkout").performClick()
        ui.onNodeWithTag("confirmFinishWorkout").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Workout details").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("1.  10 reps · 42.5 kg").assertExists()
        assertNull(runBlocking { database.workouts().active() })
    }

    @Test fun catalogueSearchShowsTimedSkillsAndTheirProgressionFamilies() {
        ui.onNodeWithText("Exercises").performClick()
        ui.onNodeWithTag("exerciseSearch").performTextInput("frontlever")
        ui.runOnUiThread {
            WindowCompat.getInsetsController(ui.activity.window, ui.activity.window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        ui.waitUntil(10_000) {
            ui.runOnIdle { ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true }
        }
        ui.waitForIdle()
        ui.onNodeWithTag("catalogueList").performScrollToNode(hasTestTag("exercise-front-lever"))
        ui.onNodeWithTag("exercise-front-lever").performClick()
        ui.onNodeWithText("Front lever progressions").assertExists()
        ui.onAllNodesWithText("Seconds").onFirst().assertExists()
        ui.onAllNodesWithText("Tuck front lever").onLast().assertExists()
    }
}
