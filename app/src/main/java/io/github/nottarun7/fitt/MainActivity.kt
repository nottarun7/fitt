package io.github.nottarun7.fitt

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nottarun7.fitt.ui.FittApp
import io.github.nottarun7.fitt.ui.FittTheme
import io.github.nottarun7.fitt.ui.WorkoutViewModel

class MainActivity : ComponentActivity() {
    private var openNutrition by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as FittApplication).workouts
        openNutrition = intent.getBooleanExtra("open_nutrition", false)
        setContent {
            val theme by (application as FittApplication).preferences.theme.collectAsStateWithLifecycle()
            FittTheme(theme) {
                val model: WorkoutViewModel = viewModel(factory = WorkoutViewModel.factory(repository))
                FittApp(model, openNutrition) { openNutrition = false; intent.removeExtra("open_nutrition") }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNutrition = intent.getBooleanExtra("open_nutrition", false)
    }
}
