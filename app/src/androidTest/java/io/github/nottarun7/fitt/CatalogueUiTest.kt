package io.github.nottarun7.fitt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.catalogue.ExerciseCatalogue
import io.github.nottarun7.fitt.data.ExerciseChoice
import io.github.nottarun7.fitt.ui.CatalogueScreen
import io.github.nottarun7.fitt.ui.ExercisePicker
import io.github.nottarun7.fitt.ui.FittTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogueUiTest {
    @get:Rule val ui = createComposeRule()
    private val press get() = ExerciseCatalogue.find("machine-chest-press")!!

    private fun searchPress() {
        ui.onNodeWithTag("exerciseSearch").performTextInput("machine chest press")
        ui.onNodeWithTag("catalogueList").performScrollToNode(hasTestTag("exercise-machine-chest-press"))
    }

    @Test fun pickerRowChoosesImmediatelyWithoutOpeningDetails() {
        val chosen = mutableListOf<ExerciseChoice>()
        ui.setContent { FittTheme { ExercisePicker(onDismiss = {}, onChoose = { chosen += it }) } }
        searchPress()
        ui.onNodeWithTag("exercise-machine-chest-press").performClick()
        ui.runOnIdle { assertEquals(listOf(press.choice()), chosen) }
        ui.onNodeWithContentDescription("Close exercise details").assertDoesNotExist()
        ui.onNodeWithTag("chooseExercise").assertDoesNotExist()
    }

    @Test fun optionalDetailsDoesNotChooseUntilAddIsTapped() {
        val chosen = mutableListOf<ExerciseChoice>()
        ui.setContent { FittTheme { ExercisePicker(onDismiss = {}, onChoose = { chosen += it }) } }
        searchPress()
        ui.onNodeWithTag("exerciseDetails-machine-chest-press").performClick()
        ui.onNodeWithContentDescription("Close exercise details").assertExists()
        ui.runOnIdle { assertTrue(chosen.isEmpty()) }
        ui.onNodeWithTag("chooseExercise").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(listOf(press.choice()), chosen) }
        ui.onNodeWithContentDescription("Close exercise details").assertDoesNotExist()
        ui.onNodeWithTag("chooseExercise").assertDoesNotExist()
    }

    @Test fun standaloneLibraryRowOpensDetailsAndMovementGuideWithoutAddAction() {
        ui.setContent { FittTheme { CatalogueScreen() } }
        searchPress()
        ui.onNodeWithTag("exerciseDetails-machine-chest-press").assertDoesNotExist()
        ui.onNodeWithTag("exercise-machine-chest-press").performClick()
        ui.onNodeWithContentDescription("Close exercise details").assertExists()
        ui.onNodeWithTag("chooseExercise").assertDoesNotExist()
        ui.onNodeWithText("Open movement guide · ACE").performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("Close exercise details").performClick()
        ui.onNodeWithText("Exercise library").assertExists()
        ui.onNodeWithContentDescription("Close exercise details").assertDoesNotExist()
    }
}
