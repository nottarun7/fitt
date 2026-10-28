package io.github.nottarun7.fitt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.progress.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoEditingUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ui.activity.application as FittApplication
    private lateinit var photo: PhotoEntity
    private lateinit var originalBytes: ByteArray

    @Before fun seed() = runBlocking {
        check(app is FittTestApplication)
        app.getSharedPreferences("progress-device", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("guide", true).commit()
        withContext(Dispatchers.IO) {
            app.database.clearAllTables()
            val source = File.createTempFile("photo-editor-ui-", ".jpg", app.cacheDir)
            val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
            try {
                source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                photo = app.photos.add(Uri.fromFile(source)).photo
                app.photos.edit(photo.id, "2024-05-12", "Original caption")
                originalBytes = app.photos.file(photo.sha256).readBytes()
            } finally { bitmap.recycle(); source.delete() }
        }
    }

    @After fun cleanup() = runBlocking {
        check(app is FittTestApplication)
        withContext(Dispatchers.IO) {
            if (::photo.isInitialized) app.photos.delete(photo.id)
            app.database.clearAllTables()
        }
        app.getSharedPreferences("progress-device", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }

    private fun timeline() {
        ui.onNodeWithText("Progress", substring = false).performClick()
        ui.onNodeWithTag("progress-Photos").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("photoList").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openPhoto() {
        ui.onNodeWithTag("photoList").performScrollToNode(hasTestTag("openPhoto-${photo.id}"))
        ui.onNodeWithTag("openPhoto-${photo.id}").performClick()
        ui.onNodeWithTag("photoDetails").assertExists()
    }

    @Test fun emptyTimelineShowsSetupWithoutARedundantExpandButton() {
        runBlocking { withContext(Dispatchers.IO) { app.photos.delete(photo.id) } }
        timeline()
        ui.onNodeWithTag("galleryOptions").assertDoesNotExist()
        ui.onNodeWithTag("photoList").performScrollToNode(hasTestTag("galleryCopies"))
        ui.onNodeWithTag("galleryCopies").assertIsOff()
        ui.onNodeWithTag("photoGuide").performScrollTo().assertIsDisplayed()
    }

    @Test fun populatedTimelineKeepsCopyConsentAndGuideInGalleryOptions() {
        timeline()
        ui.onNodeWithTag("galleryCopies").assertDoesNotExist()
        ui.onNodeWithTag("galleryOptions").performClick()
        ui.onNodeWithTag("galleryCopies").assertIsOff().performClick()
        ui.onNodeWithText("Create phone-gallery copies?").assertExists()
        ui.onNodeWithText("Keep private only").performClick()
        ui.onNodeWithTag("galleryCopies").assertIsOff()
        ui.onNodeWithTag("photoGuide").performScrollTo().performClick()
        ui.onNodeWithTag("photoBackupTip").assertIsDisplayed()
        ui.onNodeWithTag("photoGuideDone").performClick()
        assertFalse(app.getSharedPreferences("progress-device", Context.MODE_PRIVATE).getBoolean("copies", false))
        assertArrayEquals(originalBytes, app.photos.file(photo.sha256).readBytes())
    }

    @Test fun photoDraftSurvivesRecreationAndBackConfirmationThenSavesOnlyMetadata() {
        timeline(); openPhoto()
        ui.onNodeWithTag("progress-Photos").assertDoesNotExist()
        ui.onNodeWithTag("savePhoto").assertIsNotEnabled()
        ui.onNodeWithTag("photoCaption").performScrollTo().performTextReplacement("After training")
        ui.onNodeWithTag("photoDate").performScrollTo().performTextReplacement("2024-05-13")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("photoDetails").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("photoCaption").performScrollTo().assertTextContains("After training")
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Discard photo detail changes?").assertExists()
        ui.onNodeWithText("Keep editing").performClick()
        assertEquals("Original caption", runBlocking { app.database.photos().photo(photo.id)!!.caption })
        ui.onNodeWithTag("savePhoto").performClick()
        ui.waitUntil(10_000) {
            runBlocking { app.database.photos().photo(photo.id)?.let { it.caption == "After training" && it.date == "2024-05-13" } == true } &&
                ui.onAllNodes(hasTestTag("photoDate") and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                ui.onAllNodes(hasTestTag("savePhoto") and isEnabled()).fetchSemanticsNodes().isEmpty()
        }
        val saved = runBlocking { app.database.photos().photo(photo.id)!! }
        assertEquals("2024-05-13", saved.date)
        assertEquals(photo.id, saved.id)
        assertEquals(photo.sha256, saved.sha256)
        assertEquals(photo.galleryUri, saved.galleryUri)
        assertArrayEquals(originalBytes, app.photos.file(photo.sha256).readBytes())
        ui.onNodeWithTag("savePhoto").assertIsNotEnabled()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithTag("photoList").assertExists()
        ui.onNodeWithText("Discard photo detail changes?").assertDoesNotExist()
    }
}
