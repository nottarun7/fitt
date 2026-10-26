package io.github.nottarun7.fitt

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nottarun7.fitt.interchange.ImportOptions
import io.github.nottarun7.fitt.interchange.DuplicatePolicy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionReminderTest {
    @Test fun consentControlsDeliveryAndBackupImportsCannotEnableNotifications() = runBlocking {
        // The test runner uses an isolated application DB; the connected test APK is uninstalled after this run.
        val app = ApplicationProvider.getApplicationContext<FittTestApplication>()
        val reminders = app.reminders
        val notifications = app.getSystemService(NotificationManager::class.java)
        suspend fun awaitNotification(present: Boolean) = withTimeout(5000) {
            // Android's notification service applies posting/cancellation asynchronously.
            while (notifications.activeNotifications.any { it.id == 2301 } != present) delay(50)
        }
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        reminders.enabled(false)
        awaitNotification(false)
        app.getSharedPreferences("nutrition_reminders", 0).edit().remove("last_date").commit()
        try {
            val data = app.backups.export()
            app.backups.apply(app.backups.preview(data.copy(nutrition = data.nutrition.copy(reminderMinute = 0)),
                ImportOptions(duplicates = DuplicatePolicy.USE_IMPORTED)))
            reminders.sync()
            assertFalse(reminders.status.value.optedIn)
            reminders.deliver()
            assertTrue(notifications.activeNotifications.none { it.id == 2301 })
            reminders.enabled(true)
            assertTrue(reminders.status.value.allowed)
            reminders.deliver()
            awaitNotification(true)
            assertEquals(1, notifications.activeNotifications.count { it.id == 2301 })
            val first = notifications.activeNotifications.single { it.id == 2301 }
            assertNotNull(first.notification.contentIntent)
            reminders.deliver()
            assertEquals(first.postTime, notifications.activeNotifications.single { it.id == 2301 }.postTime)
            reminders.enabled(false)
            awaitNotification(false)
            assertTrue(notifications.activeNotifications.none { it.id == 2301 })
        } finally { reminders.enabled(false) }
    }
}
