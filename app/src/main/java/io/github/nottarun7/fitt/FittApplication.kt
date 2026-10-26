package io.github.nottarun7.fitt

import android.app.Application
import io.github.nottarun7.fitt.data.WorkoutDatabase
import io.github.nottarun7.fitt.data.WorkoutRepository
import io.github.nottarun7.fitt.data.BackupStore
import io.github.nottarun7.fitt.data.NutritionRepository

open class FittApplication : Application() {
    open val automaticUpdateChecksAllowed = true
    open val database by lazy { WorkoutDatabase.open(this) }
    val workouts by lazy { WorkoutRepository(database) }
    val photos by lazy { io.github.nottarun7.fitt.progress.PhotoLibrary(this, database) }
    val backups by lazy { BackupStore(database, photoExists = photos::valid) }
    val nutrition by lazy { NutritionRepository(database) }
    val reminders by lazy { io.github.nottarun7.fitt.reminders.NutritionReminders(this) }
    val preferences by lazy { io.github.nottarun7.fitt.data.AppPreferences(this) }
    protected open val scheduleReminders = true
    override fun onCreate() {
        super.onCreate()
        if (scheduleReminders) reminders.start()
    }
}
