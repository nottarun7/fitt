package io.github.nottarun7.fitt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import io.github.nottarun7.fitt.data.*
import io.github.nottarun7.fitt.interchange.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NutritionRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = mutableListOf<String>()
    private lateinit var db: WorkoutDatabase
    private lateinit var repo: NutritionRepository
    private lateinit var backup: BackupStore
    @get:Rule val migration = MigrationTestHelper(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        WorkoutDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())
    private val food = BackupFood("food", "Recipe bowl", "1 bowl", 400.0, 20.0, 50.0, 10.0, 1)
    @Before fun before() { db = fresh(); repo = NutritionRepository(db) { 10 }; backup = BackupStore(db) { 100 } }
    @After fun after() { db.close(); files.forEach { context.deleteDatabase(it) } }
    private fun fresh(): WorkoutDatabase {
        val file = "nutrition-${UUID.randomUUID()}.db"; files.add(file)
        return WorkoutDatabase.open(context, file)
    }
    private suspend fun seed() {
        repo.saveFood(food)
        repo.saveMeal(BackupMeal("meal", "2026-10-25", "Lunch", "Spicy", 1, listOf(food.portion().copy(id = "meal-item", quantity = 1.5))))
        repo.saveSchedule(BackupDietSchedule("plan", "Weekday lunch", 31, 780, true, "Prep ahead", 1,
            listOf(food.portion().copy(id = "plan-item", quantity = 0.75))))
        repo.reminderTime(1110)
    }
    @Test fun foodEditsAndDeletionKeepMealSnapshotsAcrossReopen() = runBlocking {
        seed(); repo.saveFood(food.copy(calories = 999.0)); repo.deleteFood(food.id)
        val file = files.first(); db.close(); db = WorkoutDatabase.open(context, file)
        val snapshot = BackupStore(db) { 100 }.export()
        assertTrue(snapshot.nutrition.foods.isEmpty())
        assertEquals(600.0, snapshot.nutrition.meals.single().items.nutritionTotals().calories, 0.0)
        assertEquals("food", snapshot.nutrition.meals.single().items.single().foodId)
        assertEquals(1110, snapshot.nutrition.reminderMinute)
    }
    @Test fun schedulesLogIndependentMealsAndPausedPlansRejectLogging() = runBlocking {
        seed(); val id = repo.logSchedule("plan", LocalDate.parse("2026-10-26"))
        val before = backup.export().nutrition
        val logged = before.meals.single { it.id == id }
        assertEquals("2026-10-26", logged.date); assertEquals(300.0, logged.items.nutritionTotals().calories, 0.0)
        assertNotEquals("plan-item", logged.items.single().id)
        repo.saveSchedule(before.schedules.single().copy(enabled = false, items = listOf(food.portion().copy(calories = 0.0))))
        reject { repo.logSchedule("plan", LocalDate.now()) }
        repo.deleteSchedule("plan")
        assertEquals(logged, backup.export().nutrition.meals.single { it.id == id })
    }
    @Test fun invalidNutritionDoesNotWriteRows() = runBlocking {
        reject { repo.saveFood(food.copy(calories = Double.NaN)) }
        reject { repo.saveFood(food.copy(name = " ")) }
        reject { repo.saveMeal(BackupMeal("m", "2026-02-30", "Meal", "", 1, listOf(food.portion()))) }
        reject { repo.saveMeal(BackupMeal("m", "2026-10-25", "Meal", "", 1, emptyList())) }
        reject { repo.saveSchedule(BackupDietSchedule("p", "Plan", 0, 780, true, "", 1, listOf(food.portion()))) }
        reject { repo.reminderTime(1440) }
        assertEquals(BackupNutrition(), backup.export().nutrition)
    }
    @Test fun replacingAMealWithACollidingItemRollsBackTheOriginal() = runBlocking {
        seed(); val original = backup.export()
        val first = original.nutrition.meals.single()
        repo.saveMeal(first.copy(id = "second", items = first.items.map { it.copy(id = "other-item") }))
        val before = backup.export()
        var failed = false
        try { repo.saveMeal(first.copy(name = "Should roll back", items = first.items.map { it.copy(id = "other-item") })) }
        catch (_: Exception) { failed = true }
        assertTrue(failed); assertEquals(before, backup.export())
    }
    @Test fun allBackupFormatsRestoreWorkoutAndNutritionDataTogether() = runBlocking {
        seed(); WorkoutRepository(db) { 10 }.saveRoutine(RoutineDraft(name = "Keep workouts too"))
        val expected = backup.export()
        for (format in BackupFileFormat.entries) {
            val other = fresh()
            try {
                val target = BackupStore(other) { 100 }
                target.apply(target.preview(BackupFiles.decode(BackupFiles.encode(expected, format)).document, ImportOptions(mode = ImportMode.RESTORE_EXACTLY)))
                assertEquals(format.name, expected, target.export())
                assertFalse(target.preview(expected).hasChanges)
            } finally { other.close() }
        }
    }
    @Test fun nutritionFailureRollsBackWorkoutAndNutritionChangesInTheSameImport() = runBlocking {
        seed(); WorkoutRepository(db) { 10 }.saveRoutine(RoutineDraft(name = "Before"))
        val before = backup.export()
        val incoming = before.copy(routines = before.routines.map { it.copy(name = "Changed") },
            nutrition = before.nutrition.copy(meals = before.nutrition.meals.map { it.copy(name = "Changed lunch") }))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_nutrition BEFORE INSERT ON meal_items BEGIN SELECT RAISE(ABORT, 'fail_nutrition'); END")
        var failed = false
        try { backup.apply(backup.preview(incoming, ImportOptions(mode = ImportMode.RESTORE_EXACTLY))) } catch (_: Exception) { failed = true }
        assertTrue(failed); assertEquals(before, backup.export())
    }
    @Test fun exactEmptyRestoreClearsNutritionAndStaleNutritionPreviewCannotApply() = runBlocking {
        seed(); val empty = BackupDocument(100, emptyList(), emptyList())
        val preview = backup.preview(empty, ImportOptions(mode = ImportMode.RESTORE_EXACTLY))
        repo.saveFood(food.copy(name = "Changed after preview"))
        var stale = false
        try { backup.apply(preview) } catch (_: StaleBackupPreviewException) { stale = true }
        assertTrue(stale)
        backup.apply(backup.preview(empty, ImportOptions(mode = ImportMode.RESTORE_EXACTLY)))
        assertEquals(empty, backup.export())
        for (table in listOf("foods", "meals", "meal_items", "diet_schedules", "schedule_items")) {
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }
    @Test fun migrationPreservesEveryWorkoutTableAndCreatesValidatedNutritionTables() = runBlocking {
        val file = "nutrition-migration-${UUID.randomUUID()}.db"; files.add(file)
        migration.createDatabase(file, 1).apply {
            execSQL("INSERT INTO routines VALUES ('r', 'Existing routine', 1)")
            execSQL("INSERT INTO routine_exercises VALUES ('rx', 'r', NULL, 'Press', 'REPS', 0, 3, 8, 0, 0)")
            execSQL("INSERT INTO sessions VALUES ('w', 'Existing active', 'r', 1, NULL, 1)")
            execSQL("INSERT INTO session_exercises VALUES ('wx', 'w', NULL, 'Press', 'REPS', 0, 3, 8, 0, 0)")
            execSQL("INSERT INTO logged_sets VALUES ('s', 'wx', 0, 8, 42.5, 'LB', 1)")
            execSQL("INSERT INTO set_drafts VALUES ('wx', '9', '4.', 'KG', 0)")
            close()
        }
        migration.runMigrationsAndValidate(file, 2, true, WorkoutDatabase.MIGRATION_1_2).close()
        val upgraded = WorkoutDatabase.open(context, file)
        try {
            val data = BackupStore(upgraded) { 100 }.export()
            assertEquals("Existing routine", data.routines.single().name)
            val exercise = data.workouts.single().exercises.single()
            assertEquals(42.5, exercise.sets.single().weight!!, 0.0)
            assertEquals("4.", exercise.draft!!.weight)
            assertEquals(BackupNutrition(), data.nutrition)
        } finally { upgraded.close() }
    }
    private suspend fun reject(block: suspend () -> Unit) {
        var failed = false
        try { block() } catch (_: IllegalArgumentException) { failed = true }
        assertTrue("Expected validation failure", failed)
    }

    @Test fun planImportStoresTemplatesAndTextPlansRequireNutritionBeforeLogging() = runBlocking {
        val decoded = PlanJson.decode(context.assets.open("fitt-plan-example.json").bufferedReader().use { it.readText() })
        assertTrue(decoded.isPlan)
        repo.saveFood(food); repo.reminderTime(1185)
        backup.apply(backup.preview(decoded.document, ImportOptions(mode = ImportMode.RESTORE_EXACTLY, duplicates = DuplicatePolicy.USE_IMPORTED), planOnly = true))
        val first = backup.export()
        assertEquals(1, first.routines.size); assertTrue(first.workouts.isEmpty()); assertTrue(first.nutrition.meals.isEmpty())
        assertEquals(1185, first.nutrition.reminderMinute); assertEquals(food.id, first.nutrition.foods.single().id)
        val schedule = first.nutrition.schedules.single()
        assertEquals(-1, schedule.timeMinute); assertTrue(schedule.items.isEmpty())
        reject { repo.logSchedule(schedule.id, LocalDate.now()) }
        assertTrue(backup.export().nutrition.meals.isEmpty())
        for (format in BackupFileFormat.entries) assertEquals(first, BackupFiles.decode(BackupFiles.encode(first, format)).document)
        repo.saveSchedule(schedule.copy(items = listOf(food.portion()), timeMinute = 780))
        repo.logSchedule(schedule.id, LocalDate.now())
        assertEquals(400.0, backup.export().nutrition.meals.single().items.nutritionTotals().calories, 0.0)
    }
}
