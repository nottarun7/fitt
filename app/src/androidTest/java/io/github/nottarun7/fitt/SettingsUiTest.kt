package io.github.nottarun7.fitt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.data.ThemeMode
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ui.activity.application as FittApplication
    @After fun cleanup() { app.preferences.theme(ThemeMode.SYSTEM) }
    private fun settings() {
        ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Settings").performClick()
    }
    @Test fun darkAndLightChoicesApplyAndPersistThroughRecreation() {
        settings()
        ui.onNodeWithTag("theme-DARK").performClick()
        ui.onNodeWithTag("theme-DARK").assertIsSelected()
        assertEquals(ThemeMode.DARK, app.preferences.theme.value)
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("theme-DARK").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("theme-DARK").assertIsSelected()
        ui.onNodeWithTag("theme-LIGHT").performClick()
        ui.onNodeWithTag("theme-LIGHT").assertIsSelected()
        assertEquals(ThemeMode.LIGHT, app.preferences.theme.value)
    }
    @Test fun settingsAndBackupsOpenTheBundledImportGuide() {
        settings()
        ui.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("openImportGuide"))
        ui.onNodeWithTag("openImportGuide").performClick()
        ui.waitUntil(10_000) { ui.onAllNodes(hasTestTag("copyImportPrompt") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("copyImportPrompt").assertIsEnabled()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Backups").performClick()
        ui.onNodeWithTag("backupList").performScrollToNode(hasTestTag("backupImportGuide"))
        ui.onNodeWithTag("backupImportGuide").performClick()
        ui.onNodeWithTag("copyImportPrompt").assertExists()
    }
    @Test fun healthConnectEntryShowsAvailabilityAndDataUsage() {
        settings()
        ui.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("openHealthConnect"))
        ui.onNodeWithTag("openHealthConnect").performClick()
        ui.onNodeWithTag("healthList").assertExists()
        ui.onNodeWithText("How Fitt uses health data").assertExists()
    }
}
