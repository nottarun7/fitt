package io.github.nottarun7.fitt

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.runner.AndroidJUnitRunner
import io.github.nottarun7.fitt.data.WorkoutDatabase

/** UI tests never open the user's workout database. */
class FittTestApplication : FittApplication() {
    override val automaticUpdateChecksAllowed = false
    override val scheduleReminders = false
    override val database by lazy { Room.inMemoryDatabaseBuilder(this, WorkoutDatabase::class.java).build() }
}

class FittTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, FittTestApplication::class.java.name, context)
}
