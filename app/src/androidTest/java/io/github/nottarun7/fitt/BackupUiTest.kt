package io.github.nottarun7.fitt

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.interchange.*
import io.github.nottarun7.fitt.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupUiTest {
    @get:Rule val ui = createComposeRule()
    private val current = BackupDocument(1, listOf(BackupRoutine("r", "Existing routine", 1, emptyList())),
        listOf(BackupWorkout("history", "Existing history", null, 0, 1, emptyList())))
    private val incoming = BackupDocument(1, listOf(BackupRoutine("r", "Imported routine", 2, emptyList())), emptyList())

    @Test fun zipIsTheDefaultAndOtherFormatsStayAvailable() {
        val exports = mutableListOf<BackupFileFormat>()
        val state = mutableStateOf(BackupUiState())
        ui.setContent { FittTheme { BackupContent(state.value, {}, exports::add, {}, {}, {}) } }
        ui.onNodeWithTag("export-JSON").assertDoesNotExist()
        ui.onNodeWithTag("export-CSV").assertDoesNotExist()
        ui.onNodeWithTag("export-ZIP").performClick()
        ui.onNodeWithTag("otherExportFormats").performClick()
        ui.onNodeWithTag("export-JSON").performClick()
        ui.onNodeWithTag("export-CSV").performClick()
        assertEquals(listOf(BackupFileFormat.ZIP, BackupFileFormat.JSON, BackupFileFormat.CSV), exports)
        ui.runOnIdle { state.value = state.value.copy(busy = true) }
        ui.onNodeWithTag("export-ZIP").assertIsNotEnabled()
        ui.onNodeWithTag("export-JSON").assertIsNotEnabled()
        ui.onNodeWithTag("includePhotos").assertIsNotEnabled()
    }

    @Test fun planImportsOnlyOfferMergeAndKeepCompletedHistoryIntact() {
        val plan = PlanJson.decode("""{"format":"fitt.plan","schemaVersion":1,"sourceId":"test-plan","routines":[{"id":"r","name":"Plan routine","exercises":[{"id":"x","name":"Push up","metric":"REPS","sets":3,"target":8}]}]}""")
        val preview = BackupPlanner.plan(current, plan.document)
        assertEquals(current.workouts, preview.desired.workouts)
        val state = BackupUiState(decoded = plan, plan = preview)
        ui.setContent { FittTheme { BackupContent(state, {}, {}, {}, {}, {}) } }
        ui.onNodeWithTag("backupList").performScrollToNode(hasText("How to import"))
        ui.onNodeWithText("Merge with this device").assertExists()
        ui.onNodeWithTag("exactRestore").assertDoesNotExist()
    }

    @Test fun changedDuplicatesNeedAChoiceAndConfirmationBeforeApplying() {
        var applied = 0
        val state = mutableStateOf(BackupUiState(sourceName = "backup.json", decoded = DecodedBackup(incoming),
            plan = BackupPlanner.plan(current, incoming)))
        ui.setContent { FittTheme { BackupContent(state.value, {}, {}, {}, { options ->
            state.value = state.value.copy(options = options, plan = BackupPlanner.plan(current, incoming, options))
        }, { applied++ }) } }
        ui.onNodeWithTag("backupList").performScrollToNode(hasTestTag("applyBackup"))
        ui.onNodeWithTag("applyBackup").assertIsNotEnabled()
        ui.onNodeWithTag("backupList").performScrollToNode(hasTestTag("duplicate-USE_IMPORTED"))
        ui.onNodeWithTag("duplicate-USE_IMPORTED").performClick()
        ui.onNodeWithTag("applyBackup").performScrollTo().performClick()
        assertEquals(0, applied)
        ui.onNodeWithText("Import these records?").assertExists()
        ui.onNodeWithText("Cancel").performClick()
        assertEquals(0, applied)
        ui.onNodeWithTag("applyBackup").performClick()
        ui.onNodeWithTag("confirmBackup").performClick()
        assertEquals(1, applied)
    }

    @Test fun exactRestoreExplainsReplacementAndCanBeCancelled() {
        var applied = 0
        val state = mutableStateOf(BackupUiState(sourceName = "backup.zip", decoded = DecodedBackup(incoming),
            plan = BackupPlanner.plan(current, incoming)))
        ui.setContent { FittTheme { BackupContent(state.value, {}, {}, {}, { options ->
            state.value = state.value.copy(options = options, plan = BackupPlanner.plan(current, incoming, options))
        }, { applied++ }) } }
        ui.onNodeWithTag("backupList").performScrollToNode(hasTestTag("exactRestore"))
        ui.onNodeWithTag("exactRestore").performClick()
        ui.onNodeWithTag("backupList").performScrollToNode(hasTestTag("applyBackup"))
        ui.onNodeWithTag("applyBackup").performClick()
        ui.onNodeWithText("Replace device data?").assertExists()
        ui.onNodeWithText("Replace and restore").assertExists()
        ui.onNodeWithText("Cancel").performClick()
        assertEquals(0, applied)
    }

    @Test fun invalidFilesShowProblemsWithoutAnImportAction() {
        var applied = 0
        ui.setContent { FittTheme { BackupContent(BackupUiState(issues = listOf(ValidationIssue("sets[0].weight", "Invalid load."))),
            {}, {}, {}, {}, { applied++ }) } }
        ui.onNodeWithTag("backupList").performScrollToNode(hasTestTag("backupErrors"))
        ui.onNodeWithText("Operation not completed").assertExists()
        ui.onNodeWithTag("applyBackup").assertDoesNotExist()
        assertEquals(0, applied)
    }
}
