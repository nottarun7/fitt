package io.github.nottarun7.fitt.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nottarun7.fitt.FittApplication
import io.github.nottarun7.fitt.ui.FittTheme

class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by (application as FittApplication).preferences.theme.collectAsStateWithLifecycle()
            FittTheme(mode) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Fitt & your health data", style = MaterialTheme.typography.headlineMedium)
                        Text("Health Connect is optional. Workouts, nutrition, reminders, and file backups work without it.")
                        Text("Exercise write access lets you share completed workout sessions. Nutrition write access lets you share calories and macros from logged meals. You review and start each share. Planned meals and routines are not recorded as completed activity.")
                        Text("Meal dates use midnight in your current device time zone as a placeholder because Fitt does not store meal clock times. Workouts share duration/title as strength training; detailed sets remain in Fitt.")
                        Text("Step read access shows today's aggregate on the Health Connect screen. Fitt does not read your heart rate, routes, sleep, or medical records, and does not read health data in the background.")
                        Text("Fitt uses Internet access only to check GitHub for app updates. Checks send the app version and normal connection metadata, never your workouts, meals or photos. Automatic checks can be disabled in Settings. There is no account, ads or analytics. Local data and portable files stay under your control. Other apps you allow in Health Connect may read shared records under their own policies.")
                        Text("You can revoke access in Health Connect. Removing Fitt's shared data there does not remove local entries. Deleting local Fitt entries does not automatically delete shared copies. Theme, health permissions, and sharing version counters are device settings, excluded from backups.")
                        Button({ finish() }) { Text("Done") }
                    }
                }
            }
        }
    }
}
