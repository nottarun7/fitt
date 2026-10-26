package io.github.nottarun7.fitt.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import io.github.nottarun7.fitt.FittApplication
import io.github.nottarun7.fitt.MainActivity
import io.github.nottarun7.fitt.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

data class ReminderStatus(val optedIn: Boolean = false, val allowed: Boolean = false, val error: String? = null)

/** Consent is a device preference and is deliberately never imported from a portable backup. */
class NutritionReminders(private val app: FittApplication) {
    private val preferences = app.getSharedPreferences("nutrition_reminders", Context.MODE_PRIVATE)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()
    private val mutableStatus = MutableStateFlow(ReminderStatus())
    val status = mutableStatus.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watching: Job? = null
    private fun pending() = PendingIntent.getBroadcast(app, 2301,
        Intent(app, NutritionAlarmReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun refresh() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Daily nutrition reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "An optional daily prompt to review your meals"
        })
        val permission = Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val allowed = permission && notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        mutableStatus.value = mutableStatus.value.copy(optedIn = preferences.getBoolean("enabled", false), allowed = allowed)
    }
    fun start() {
        if (watching != null) return
        watching = scope.launch {
            app.database.nutrition().observeSettings().map { it?.reminderMinute ?: 1200 }.distinctUntilChanged()
                .catch { mutableStatus.update { it.copy(error = "Could not read reminder settings. Reopen Fitt to retry.") } }
                .collect { sync() }
        }
    }
    suspend fun enabled(value: Boolean) = lock.withLock {
        refresh()
        require(!value || status.value.allowed) { "Allow nutrition notifications in Android settings first." }
        check(preferences.edit().putBoolean("enabled", value).commit()) { "Could not save your reminder choice." }
        scheduleLocked()
    }
    suspend fun sync() {
        try { lock.withLock { scheduleLocked() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableStatus.update { it.copy(error = "Could not read reminder settings. Reopen Fitt to retry.") } }
    }
    private suspend fun scheduleLocked() {
        refresh(); alarms.cancel(pending())
        if (!status.value.optedIn || !status.value.allowed) { notifications.cancel(2301); mutableStatus.update { it.copy(error = null) }; return }
        val minute = app.database.nutrition().settings()?.reminderMinute ?: 1200
        try {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                nextReminderInstant(Instant.now(), minute, ZoneId.systemDefault()).toEpochMilli(), pending())
            mutableStatus.update { it.copy(error = null) }
        } catch (_: RuntimeException) {
            mutableStatus.update { it.copy(error = "Android could not schedule the reminder. Toggle it off and on to retry.") }
        }
    }
    suspend fun deliver() = lock.withLock {
        refresh()
        if (status.value.optedIn && status.value.allowed) {
            val today = java.time.LocalDate.now().toString()
            val minute = app.database.nutrition().settings()?.reminderMinute ?: 1200
            val local = java.time.LocalTime.now()
            // Ignore an old pending broadcast after the time was changed or the clock moved back.
            if (local.hour * 60 + local.minute >= minute && preferences.getString("last_date", "") != today) {
                val intent = Intent(app, MainActivity::class.java).putExtra("open_nutrition", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val content = PendingIntent.getActivity(app, 2301, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    notifications.notify(2301, Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_nutrition)
                        .setContentTitle("A moment for your meals")
                        .setContentText("Log your food or review your meal plan in Fitt.")
                        .setContentIntent(content).setAutoCancel(true).build())
                    preferences.edit().putString("last_date", today).apply()
                } catch (_: SecurityException) { refresh() }
            }
        }
        scheduleLocked()
    }
    companion object {
        const val CHANNEL = "nutrition_daily"
        const val ACTION = "io.github.nottarun7.fitt.NUTRITION_REMINDER"
    }
}

class NutritionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NutritionReminders.ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { (context.applicationContext as FittApplication).reminders.deliver() }
            catch (_: Exception) { /* Retry scheduling when the app next opens. */ }
            finally { pending.finish() }
        }
    }
}

class NutritionClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { (context.applicationContext as FittApplication).reminders.sync() }
            finally { pending.finish() }
        }
    }
}
