package io.github.nottarun7.fitt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.data.portable
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class NutritionUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ui.activity.application as FittApplication
    @Before fun reset() { app.database.clearAllTables() }
    private fun openNutrition() {
        ui.onNodeWithText("Nutrition").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("nutrition-Foods").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun editorControl(tag: String) = editorNode(hasTestTag(tag))
    private fun hideKeyboard() {
        ui.runOnUiThread {
            WindowCompat.getInsetsController(ui.activity.window, ui.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        ui.waitUntil(10_000) {
            ui.runOnIdle {
                ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true
            }
        }
        ui.waitForIdle()
    }
    private fun editorNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("nutritionEditor").fetchSemanticsNodes().isNotEmpty() }
        hideKeyboard()
        // Lazy forms can dispose an off-screen field when the keyboard changes the viewport.
        // Ask the stable container to compose and reveal it before querying the field itself.
        ui.onNodeWithTag("nutritionEditor").performScrollToNode(matcher)
        return ui.onNode(matcher)
    }
    private fun createFood(): String {
        ui.onNodeWithTag("nutrition-Foods").performClick()
        ui.onNodeWithTag("newFood").performClick()
        editorControl("nutritionName").performTextInput("Offline bowl")
        editorControl("foodServing").performTextInput("1 bowl")
        for ((tag, value) in listOf("foodCalories" to "400", "foodProtein" to "20", "foodCarbs" to "50", "foodFat" to "10")) {
            editorControl(tag).performTextInput(value)
        }
        ui.onNodeWithTag("saveNutrition").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.nutrition().foods().isNotEmpty() } }
        return runBlocking { app.database.nutrition().foods().single().id }
    }
    @Test fun successSnackbarDoesNotReplayAfterRecreationOrLeavingNutrition() {
        val success = "Reminder time saved."
        openNutrition()
        ui.onNodeWithTag("nutrition-Reminders").performClick()
        ui.onNodeWithTag("reminderTime").performTextReplacement("19:45")
        ui.onNodeWithTag("saveReminderTime").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(success).fetchSemanticsNodes().isNotEmpty() }
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("reminderTime").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        ui.onNodeWithText(success).assertDoesNotExist()
        assertEquals(1185, runBlocking { app.database.nutrition().settings()!!.reminderMinute })

        ui.onNodeWithTag("reminderTime").performTextReplacement("20:15")
        ui.onNodeWithTag("saveReminderTime").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(success).fetchSemanticsNodes().isNotEmpty() }
        mainTab("Train").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("trainList").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        mainTab("Nutrition").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("reminderTime").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        ui.onNodeWithText(success).assertDoesNotExist()
        assertEquals(1215, runBlocking { app.database.nutrition().settings()!!.reminderMinute })
    }

    @Test fun validationErrorSurvivesRecreationAndLeavingUntilDismissed() {
        val error = "Use a valid 24-hour time: HH:MM."
        openNutrition()
        val settingsBefore = runBlocking { app.database.nutrition().settings() }
        ui.onNodeWithTag("nutrition-Reminders").performClick()
        ui.onNodeWithTag("reminderTime").performTextReplacement("25:00")
        ui.onNodeWithTag("saveReminderTime").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Dismiss").assertExists()
        mainTab("Train").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("trainList").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        mainTab("Nutrition").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Dismiss").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(error).fetchSemanticsNodes().isEmpty() }
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("reminderTime").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        ui.onNodeWithText(error).assertDoesNotExist()
        assertEquals(settingsBefore, runBlocking { app.database.nutrition().settings() })
    }

    private fun mainTab(label: String): SemanticsNodeInteraction {
        hideKeyboard()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("mainNavigation").fetchSemanticsNodes().isNotEmpty() }
        return ui.onNode((hasText(label) or hasContentDescription(label)) and hasAnyAncestor(hasTestTag("mainNavigation")))
    }

    @Test fun customFoodMealDraftRecoveryAndPastTotalsSurviveFoodEditing() {
        openNutrition(); val food = createFood()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("newFood").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nutrition-Diary").performClick()
        ui.onNodeWithTag("newMeal").performClick()
        editorControl("nutritionName").performTextReplacement("Offline lunch")
        editorControl("addMealFood").performClick()
        ui.onNodeWithTag("pickFood-$food").performClick()
        val portion = runBlocking { app.database.nutrition().foods().single().portable() }
        ui.onNodeWithTag("nutritionEditor").performScrollToNode(hasText("Servings"))
        ui.onNode(hasText("Servings") and hasSetTextAction()).performTextReplacement("1.5")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("nutritionEditor").fetchSemanticsNodes().isNotEmpty() }
        editorControl("nutritionName").assertTextContains("Offline lunch")
        ui.onNodeWithTag("saveNutrition").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.nutrition().meals().size == 1 } }
        assertEquals(600.0, runBlocking { app.database.nutrition().meals().single().portable().items.single().quantity * portion.calories }, 0.0)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("nutrition-Foods").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nutrition-Foods").performClick()
        ui.onNodeWithTag("nutritionList").performScrollToNode(hasText("Edit food"))
        ui.onNodeWithText("Edit food").performClick()
        editorControl("foodCalories").performTextReplacement("999")
        ui.onNodeWithTag("saveNutrition").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("newFood").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nutrition-Diary").performClick()
        ui.onAllNodesWithText("600 kcal").onFirst().assertExists()
    }
    @Test fun weeklyPlanCanBeLoggedAndRemindersStartOffWithEditableTime() {
        openNutrition(); val food = createFood()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("newFood").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nutrition-Plans").performClick()
        ui.onNodeWithTag("newDietSchedule").performClick()
        editorControl("nutritionName").performTextReplacement("Lunch plan")
        editorControl("addMealFood").performClick()
        ui.onNodeWithTag("pickFood-$food").performClick()
        ui.onNodeWithTag("saveNutrition").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.nutrition().schedules().isNotEmpty() } }
        val id = runBlocking { app.database.nutrition().schedules().single().schedule.id }
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("nutrition-Diary").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nutrition-Diary").performClick()
        ui.onNodeWithTag("nutritionList").performScrollToNode(hasTestTag("logPlan-$id"))
        ui.onNodeWithTag("logPlan-$id").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.nutrition().meals().isNotEmpty() } }
        ui.onNodeWithTag("nutrition-Reminders").performClick()
        ui.onNodeWithTag("enableNutritionReminder").assertIsOff()
        ui.onNodeWithContentDescription("Enable daily nutrition reminder").assertIsOff()
        ui.onNodeWithTag("reminderTime").performTextReplacement("19:45")
        ui.onNodeWithTag("saveReminderTime").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.nutrition().settings()?.reminderMinute == 1185 } }
        ui.onNodeWithTag("enableNutritionReminder").assertIsOff()
    }

    @Test fun nestedFoodCancelAndSaveKeepMealDraftThroughRecreationAndPreviewTotals() {
        openNutrition()
        ui.onNodeWithTag("newMeal").performClick()
        editorControl("nutritionName").performTextReplacement("Breakfast")
        editorControl("mealDate").performTextReplacement("2026-10-27")
        editorControl("nutritionNotes").performTextInput("Before training")
        editorControl("addMealFood").performClick()
        ui.onNodeWithTag("foodPickerEmpty").assertExists()
        ui.onNodeWithTag("foodPickerSearch").performTextInput("Yogurt")
        ui.onNodeWithTag("foodPickerSearch").performImeAction()
        ui.onNodeWithTag("createFoodFromPicker").performClick()
        editorControl("nutritionName").assertTextContains("Yogurt")
        editorControl("foodServing").performTextInput("1 cup")
        ui.activityRule.scenario.recreate()
        editorControl("nutritionName").assertTextContains("Yogurt")
        editorControl("foodServing").assertTextContains("1 cup")
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Discard changes").performClick()
        editorControl("nutritionName").assertTextContains("Breakfast")
        editorControl("mealDate").assertTextContains("2026-10-27")
        editorControl("nutritionNotes").assertTextContains("Before training")
        assertTrue(runBlocking { app.database.nutrition().foods().isEmpty() })

        editorControl("addMealFood").performClick()
        ui.onNodeWithTag("foodPickerSearch").performTextReplacement("Yogurt")
        ui.onNodeWithTag("foodPickerSearch").performImeAction()
        ui.onNodeWithTag("createFoodFromPicker").performClick()
        editorControl("foodServing").performTextInput("1 cup")
        for ((tag, value) in listOf("foodCalories" to "200", "foodProtein" to "10", "foodCarbs" to "25", "foodFat" to "5")) {
            editorControl(tag).performTextInput(value)
        }
        ui.activityRule.scenario.recreate()
        editorControl("foodFat").assertTextContains("5")
        ui.onNodeWithTag("saveNutrition").assertTextContains("Save & add").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Meal details").fetchSemanticsNodes().isNotEmpty() }
        editorControl("nutritionName").assertTextContains("Breakfast")
        editorControl("mealDate").assertTextContains("2026-10-27")
        editorControl("nutritionNotes").assertTextContains("Before training")
        val servings = hasText("Servings") and hasSetTextAction()
        editorNode(servings).performTextReplacement("1.5")
        editorControl("nutritionDraftTotals").assert(hasAnyDescendant(hasText("300 kcal")))
            .assert(hasAnyDescendant(hasText("Protein 15 g · Carbs 37.5 g · Fat 7.5 g")))
        for (invalid in listOf("", "NaN", "0")) {
            editorNode(servings).performTextReplacement(invalid)
            editorControl("nutritionDraftTotalsInvalid").assertExists()
            ui.onNodeWithTag("nutritionDraftTotals").assertDoesNotExist()
        }
        editorNode(servings).performTextReplacement("1.5")
        ui.onNodeWithTag("saveNutrition").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.nutrition().meals().size == 1 } }
        val meal = runBlocking { app.database.nutrition().meals().single().portable() }
        assertEquals("Breakfast", meal.name)
        assertEquals("2026-10-27", meal.date)
        assertEquals("Before training", meal.notes)
        assertEquals(1.5, meal.items.single().quantity, 0.0)
        assertEquals("Yogurt", meal.items.single().name)
        assertEquals(1, runBlocking { app.database.nutrition().foods().size })
    }

    @Test fun nestedFoodCreationKeepsScheduleAndExistingPortions() {
        openNutrition()
        val firstFood = createFood()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("newFood").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nutrition-Plans").performClick()
        ui.onNodeWithTag("newDietSchedule").performClick()
        editorControl("nutritionName").performTextReplacement("Packed lunch")
        editorControl("scheduleTime").performTextReplacement("07:30")
        editorNode(hasText("Mon")).performClick()
        editorNode(hasContentDescription("Schedule active")).performClick()
        editorControl("nutritionNotes").performTextInput("Pack the night before")
        editorControl("addMealFood").performClick()
        ui.onNodeWithTag("pickFood-$firstFood").performClick()
        editorNode(hasText("Servings") and hasSetTextAction()).performTextReplacement("2")
        editorControl("addMealFood").performClick()
        ui.onNodeWithTag("foodPickerSearch").performTextInput("Banana")
        ui.onNodeWithTag("foodPickerSearch").performImeAction()
        ui.onNodeWithTag("foodPickerEmpty").assertTextContains("No foods found.", substring = true)
        ui.onNodeWithTag("clearFoodSearch").performClick()
        ui.onNodeWithTag("pickFood-$firstFood").assertExists()
        ui.onNodeWithTag("foodPickerSearch").performTextInput("Banana")
        ui.onNodeWithTag("foodPickerSearch").performImeAction()
        ui.onNodeWithTag("createFoodFromPicker").performClick()
        editorControl("nutritionName").assertTextContains("Banana")
        editorControl("foodServing").performTextInput("1 piece")
        for ((tag, value) in listOf("foodCalories" to "100", "foodProtein" to "1", "foodCarbs" to "25", "foodFat" to "0")) {
            editorControl(tag).performTextInput(value)
        }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("saveNutrition").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Diet schedule").fetchSemanticsNodes().isNotEmpty() }
        editorControl("nutritionName").assertTextContains("Packed lunch")
        editorControl("scheduleTime").assertTextContains("07:30")
        editorControl("nutritionNotes").assertTextContains("Pack the night before")
        editorNode(hasContentDescription("Schedule active")).assertIsOff()
        editorControl("nutritionDraftTotals").assert(hasAnyDescendant(hasText("900 kcal")))
        ui.onNodeWithTag("saveNutrition").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.nutrition().schedules().size == 1 } }
        val schedule = runBlocking { app.database.nutrition().schedules().single().portable() }
        assertEquals("Packed lunch", schedule.name)
        assertEquals("Pack the night before", schedule.notes)
        assertEquals(450, schedule.timeMinute)
        assertEquals(126, schedule.daysOfWeek)
        assertFalse(schedule.enabled)
        assertEquals(2, schedule.items.size)
        assertEquals(2.0, schedule.items.first().quantity, 0.0)
        assertEquals(400.0, schedule.items.first().calories, 0.0)
    }

    @Test fun diaryCalendarNavigationSetsMealDateWithoutTyping() {
        openNutrition()
        val yesterday = LocalDate.now().minusDays(1)
        ui.onNodeWithTag("diaryPrevious").performClick()
        ui.onNodeWithTag("diaryDate").assertTextContains(yesterday.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))
            .performClick()
        ui.onNodeWithTag("diaryCalendar").assertExists()
        ui.onNodeWithTag("cancelDiaryDate").performClick()
        ui.onNodeWithTag("newMeal").performClick()
        editorControl("mealDate").assertTextContains(yesterday.toString())
        ui.onNodeWithContentDescription("Choose Date from calendar").performClick()
        ui.onNodeWithTag("datePickerDialog").assertExists()
        ui.onNodeWithTag("confirmDate").performClick()
        editorControl("mealDate").assertTextContains(yesterday.toString())
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Discard changes").performClick()
        ui.onNodeWithTag("diaryToday").performClick()
        ui.onNodeWithTag("diaryToday").assertIsNotEnabled()
        ui.onNodeWithTag("diaryNext").performClick()
        ui.onNodeWithTag("diaryToday").assertIsEnabled().performClick()
        ui.onNodeWithTag("diaryDate").performClick()
        ui.onNodeWithTag("confirmDiaryDate").performClick()
        ui.onNodeWithTag("newMeal").performClick()
        editorControl("mealDate").assertTextContains(LocalDate.now().toString())
    }

    @Test fun foodFieldsAdvanceWithImeAndDoneClearsFocus() {
        openNutrition()
        ui.onNodeWithTag("nutrition-Foods").performClick()
        ui.onNodeWithTag("newFood").performClick()
        ui.onNodeWithTag("nutritionName").performClick().performTextInput("Quick snack")
        ui.onNodeWithTag("nutritionName").performImeAction()
        val values = listOf("foodServing" to "1 portion", "foodCalories" to "150", "foodProtein" to "5", "foodCarbs" to "20", "foodFat" to "5")
        for ((tag, value) in values) {
            ui.waitUntil(10_000) { ui.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag(tag).assertIsFocused().performTextInput(value)
            ui.onNodeWithTag(tag).performImeAction()
        }
        ui.onNodeWithTag("foodFat").assertIsNotFocused()
        ui.onNodeWithTag("saveNutrition").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.nutrition().foods().size == 1 } }
        assertEquals("Quick snack", runBlocking { app.database.nutrition().foods().single().name })
    }
}
