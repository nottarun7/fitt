package io.github.nottarun7.fitt

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.ui.*
import io.github.nottarun7.fitt.updates.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class UpdateUiTest {
    @get:Rule val ui = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<FittApplication>()
    private val hash = "a".repeat(64)
    private val manifest = UpdateManifest(1, app.packageName, 10, "0.2.0", 26, "v0.2.0", "Changes", listOf(UpdateAsset("fitt.apk", hash, hash)))
    @Before fun before() { app.getSharedPreferences("app-updates", 0).edit().clear().commit() }
    @After fun after() { app.getSharedPreferences("app-updates", 0).edit().clear().commit() }
    @Test fun automaticChecksAreThrottledAndOffSettingPreventsNetworkRequests() {
        ui.setContent { FittTheme {} }
        var requests = 0
        val source = object : UpdateSource { override suspend fun fetch(): UpdateManifest { requests++; return manifest } }
        val model = UpdateViewModel(app, source, { hash }, 2, 36) { 100_000_000 }
        ui.runOnIdle { model.automatic(false); model.check() }; ui.runOnIdle { assertEquals(0, requests); model.automatic(true); model.check() }
        ui.waitUntil { !model.state.value.checking }
        ui.runOnIdle { model.check(); assertEquals(1, requests); model.check(manual = true) }
        ui.waitUntil { !model.state.value.checking }; assertEquals(2, requests)
    }
    @Test fun promptSupportsLaterSkipAndExplicitUserDownload() {
        var downloaded = 0; var skipped = 0; var later = 0
        val update = UpdatePolicy.available(manifest, 2, 36, hash)!!
        ui.setContent { FittTheme { UpdatePrompt(update, { later++ }, { skipped++ }, { downloaded++ }) } }
        ui.onNodeWithText("Fitt 0.2.0 is available").assertExists()
        ui.onNodeWithText("Later").performClick(); ui.onNodeWithText("Skip version").performClick()
        assertEquals(0, downloaded); assertEquals(1, later); assertEquals(1, skipped)
        ui.onNodeWithTag("downloadUpdate").performClick(); assertEquals(1, downloaded)
    }
    @Test fun skippingVersionSuppressesAutomaticPromptButManualCheckStillShowsItAndOfflineIsHarmless() {
        ui.setContent { FittTheme {} }
        var failing = false; var now = 100_000_000L
        val source = object : UpdateSource { override suspend fun fetch(): UpdateManifest { if (failing) throw IOException(); return manifest } }
        val model = UpdateViewModel(app, source, { hash }, 2, 36) { now }
        ui.runOnIdle { model.check() }; ui.waitUntil { !model.state.value.checking }; assertTrue(model.state.value.showPrompt)
        ui.runOnIdle { model.skip(); now += 86_400_000; model.check() }; ui.waitUntil { !model.state.value.checking }; assertFalse(model.state.value.showPrompt)
        ui.runOnIdle { model.check(manual = true) }; ui.waitUntil { !model.state.value.checking }; assertTrue(model.state.value.showPrompt)
        ui.runOnIdle { model.later(); failing = true; now += 86_400_000; model.check() }; ui.waitUntil { !model.state.value.checking }; assertNull(model.state.value.message)
        ui.runOnIdle { model.check(manual = true) }; ui.waitUntil { !model.state.value.checking }
        assertTrue(model.state.value.message!!.contains("offline")); assertNotNull(model.state.value.available)
    }
}
