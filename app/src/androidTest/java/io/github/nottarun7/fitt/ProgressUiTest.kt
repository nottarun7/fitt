package io.github.nottarun7.fitt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.interchange.*
import io.github.nottarun7.fitt.progress.*
import io.github.nottarun7.fitt.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ProgressUiTest {
    @get:Rule val ui = createComposeRule()
    private val day = LocalDate.parse("2026-10-26")
    @Test fun emptyChartsExplainHowToStartWithoutInventingData() {
        val report = ProgressAnalytics.report(emptyList(), emptyList(), day, 7, ZoneId.of("UTC"))
        ui.setContent { FittTheme { ProgressCharts(report, false) } }
        ui.onNodeWithText("No completed workouts in this range").assertExists()
        ui.onNodeWithTag("chart-Daily workouts").assertDoesNotExist()
    }
    @Test fun nutritionChartHasExactAccessibleValuesAndUnloggedDayGaps() {
        val portion = BackupPortion("p", 0, null, "Bowl", "1 serving", 1.5, 400.0, 20.0, 50.0, 10.0)
        val meal = BackupMeal("m", day.toString(), "Lunch", "", 1, listOf(portion))
        val report = ProgressAnalytics.report(emptyList(), listOf(meal), day, 7, ZoneId.of("UTC"))
        ui.setContent { FittTheme { ProgressCharts(report, true) } }
        ui.onNodeWithTag("chart-Calories").assertExists()
        ui.onNodeWithText("600 kcal average · 1 logged day").assertExists()
        ui.onNodeWithText("View values").performScrollTo().performClick()
        ui.onNodeWithTag("progressCharts").performScrollToNode(hasText("600 kcal"))
        ui.onNodeWithText("600 kcal").assertExists()
        ui.onAllNodesWithText("No log").onFirst().assertExists()
    }
    @Test fun privatePhotoGuideExplainsSharedCopiesAndBackupChoice() {
        var dismissed = false
        ui.setContent { FittTheme { PhotoGuide { dismissed = true } } }
        ui.onNodeWithText("Your photos, on your device").assertExists()
        ui.onNodeWithText("3. Optional phone album", substring = true).performScrollTo().assertExists()
        ui.onNodeWithText("4. Back up photos", substring = true).performScrollTo().assertExists()
        ui.onNodeWithTag("photoGuideDone").performClick(); assertTrue(dismissed)
    }
}
