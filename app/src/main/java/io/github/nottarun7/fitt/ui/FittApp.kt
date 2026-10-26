package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nottarun7.fitt.FittApplication
import android.net.Uri
import io.github.nottarun7.fitt.health.*

@Composable
fun FittApp(model: WorkoutViewModel, openNutrition: Boolean = false, onNutritionOpened: () -> Unit = {}) {
    val application = LocalContext.current.applicationContext as FittApplication
    val updates: io.github.nottarun7.fitt.updates.UpdateViewModel = viewModel(factory = io.github.nottarun7.fitt.updates.UpdateViewModel.factory(application))
    UpdatePromptHost(updates, application.automaticUpdateChecksAllowed)
    val navigation = rememberNavController()
    val backStack by navigation.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val overview by model.overview.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(model) { model.messages.collect { snackbars.showSnackbar(it) } }
    val roots = listOf("train", "catalogue", "history", "nutrition", "progress")
    LaunchedEffect(openNutrition) {
        if (openNutrition) { navigation.navigate("nutrition") { launchSingleTop = true }; onNutritionOpened() }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = {
            if (route in roots) NavigationBar {
                roots.forEachIndexed { index, target ->
                    val label = listOf("Train", "Exercises", "History", "Nutrition", "Progress")[index]
                    val icon = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.DateRange, Icons.Default.Favorite, Icons.Default.Star)[index]
                    NavigationBarItem(selected = route == target, onClick = {
                        navigation.navigate(target) {
                            popUpTo(navigation.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    }, icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
        },
    ) { insets ->
        NavHost(navigation, "train", Modifier.padding(insets)) {
            composable("train") {
                TrainScreen(overview, busy,
                    onNew = { model.beginEditor(null); navigation.navigate("routine/new") },
                    onEdit = { model.beginEditor(it); navigation.navigate("routine/${Uri.encode("edit:${it.routine.id}")}") },
                    onDelete = model::deleteRoutine,
                    onStart = { model.startWorkout(it) { navigation.navigate("workout") { launchSingleTop = true } } },
                    onResume = { navigation.navigate("workout") { launchSingleTop = true } },
                    onBackup = { navigation.navigate("backup") { launchSingleTop = true } },
                    onSettings = { navigation.navigate("settings") { launchSingleTop = true } })
            }
            composable("routine/{id}") { entry ->
                val key = entry.arguments?.getString("id") ?: "new"
                val editor by model.editor.collectAsStateWithLifecycle()
                LaunchedEffect(key, overview.loading) { if (!overview.loading) model.ensureEditor(key, overview.routines) }
                RoutineEditorScreen(editor, model, busy, onBack = { navigation.popBackStack() })
            }
            composable("workout") {
                val saving by model.saving.collectAsStateWithLifecycle()
                WorkoutScreen(overview.active, overview.loading, model, busy, saving,
                    onBack = { navigation.popBackStack() },
                    onFinished = { id -> navigation.navigate("history/${Uri.encode(id)}") { popUpTo("train") } })
            }
            composable("catalogue") { CatalogueScreen() }
            composable("settings") {
                val app = LocalContext.current.applicationContext as FittApplication
                SettingsScreen(app.preferences, { navigation.popBackStack() }, { navigation.navigate("health") }, { navigation.navigate("import-guide") }, updates)
            }
            composable("health") {
                val app = LocalContext.current.applicationContext as FittApplication
                val health: HealthViewModel = viewModel(factory = HealthViewModel.factory(AndroidHealthGateway(app), app.backups, HealthVersionStore(app)))
                HealthScreen(health) { navigation.popBackStack() }
            }
            composable("import-guide") { ImportGuideScreen { navigation.popBackStack() } }
            composable("progress") {
                val app = LocalContext.current.applicationContext as FittApplication
                val progress: ProgressViewModel = viewModel(factory = ProgressViewModel.factory(app))
                ProgressScreen(progress) { navigation.navigate("backup") }
            }
            composable("nutrition") {
                val app = LocalContext.current.applicationContext as FittApplication
                val nutrition: NutritionViewModel = viewModel(factory = NutritionViewModel.factory(app.nutrition, app.reminders))
                NutritionScreen(nutrition)
            }
            composable("backup") {
                val app = LocalContext.current.applicationContext as FittApplication
                val backups: BackupViewModel = viewModel(factory = BackupViewModel.factory(app.backups, app.contentResolver, app.photos, app.cacheDir))
                BackupScreen(backups, onBack = { navigation.popBackStack() }, onGuide = { navigation.navigate("import-guide") })
            }
            composable("history") { HistoryScreen(overview, onOpen = { navigation.navigate("history/${Uri.encode(it)}") }) }
            composable("history/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val flow = remember(id) { model.session(id) }
                val session by flow.collectAsStateWithLifecycle(initialValue = null)
                HistoryDetailScreen(session, onBack = { navigation.popBackStack() })
            }
        }
    }
}
