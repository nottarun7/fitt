package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
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
    val navigation = rememberNavController()
    val backStack by navigation.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    UpdatePromptHost(updates, application.automaticUpdateChecksAllowed,
        presentationAllowed = route != "workout" && WindowInsets.ime.getBottom(LocalDensity.current) == 0)
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
            if (route in roots) Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                roots.forEachIndexed { index, target ->
                    val label = listOf("Train", "Exercises", "History", "Nutrition", "Progress")[index]
                    val icon = listOf(FittIcons.Training, FittIcons.Exercises, FittIcons.History, FittIcons.Nutrition, FittIcons.Progress)[index]
                    NavigationBarItem(selected = route == target, onClick = {
                        navigation.navigate(target) {
                            popUpTo(navigation.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    }, icon = { Icon(icon, null) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
        NavHost(navigation, "train", Modifier.widthIn(max = 900.dp).fillMaxSize()) {
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
}
