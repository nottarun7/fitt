package io.github.nottarun7.fitt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.data.portable
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ui.activity.application as FittApplication
    @Before fun reset() { app.database.clearAllTables() }
    private fun openNutrition() {
        ui.onNodeWithText("Nutrition").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("nutrition-Foods").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun createFood(): String {
        ui.onNodeWithTag("nutrition-Foods").performClick()
        ui.onNodeWithTag("newFood").performClick()
        ui.onNodeWithTag("nutritionName").performTextInput("Offline bowl")
        ui.onNodeWithTag("foodServing").performTextInput("1 bowl")
        for ((tag, value) in listOf("foodCalories" to "400", "foodProtein" to "20", "foodCarbs" to "50", "foodFat" to "10")) {
            ui.onNodeWithTag(tag).performScrollTo().performTextInput(value)
        }
        ui.onNodeWithTag("saveNutrition").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.nutrition().foods().isNotEmpty() } }
        return runBlocking { app.database.nutrition().foods().single().id }
    }
    @Test fun customFoodMealDraftRecoveryAndPastTotalsSurviveFoodEditing() {
        openNutrition(); val food = createFood()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("newFood").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nutrition-Diary").performClick()
        ui.onNodeWithTag("newMeal").performClick()
        ui.onNodeWithTag("nutritionName").performTextReplacement("Offline lunch")
        ui.onNodeWithTag("addMealFood").performScrollTo().performClick()
        ui.onNodeWithTag("pickFood-$food").performClick()
        val portion = runBlocking { app.database.nutrition().foods().single().portable() }
        ui.onNodeWithTag("nutritionEditor").performScrollToNode(hasText("Servings"))
        ui.onNode(hasText("Servings") and hasSetTextAction()).performTextReplacement("1.5")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("nutritionEditor").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nutritionName").performScrollTo().assertTextContains("Offline lunch")
        ui.onNodeWithTag("saveNutrition").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.nutrition().meals().size == 1 } }
        assertEquals(600.0, runBlocking { app.database.nutrition().meals().single().portable().items.single().quantity * portion.calories }, 0.0)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("nutrition-Foods").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nutrition-Foods").performClick()
        ui.onNodeWithTag("nutritionList").performScrollToNode(hasText("Edit food"))
        ui.onNodeWithText("Edit food").performClick()
        ui.onNodeWithTag("foodCalories").performScrollTo().performTextReplacement("999")
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
        ui.onNodeWithTag("nutritionName").performTextReplacement("Lunch plan")
        ui.onNodeWithTag("addMealFood").performScrollTo().performClick()
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
}
