package io.github.nottarun7.fitt.data

import androidx.room.withTransaction
import io.github.nottarun7.fitt.interchange.*
import io.github.nottarun7.fitt.progress.portable
import io.github.nottarun7.fitt.progress.entity

class StaleBackupPreviewException : IllegalStateException("Your workout data changed. Review an updated preview before importing.")

/** Portable snapshots and atomic metadata imports; PhotoLibrary stages and verifies image files. */
class BackupStore(private val database: WorkoutDatabase, private val photoExists: (BackupPhoto) -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = database.workouts()

    suspend fun export(): BackupDocument = database.withTransaction { snapshot(clock()).requireValid().canonical() }

    suspend fun preview(incoming: BackupDocument, options: ImportOptions = ImportOptions(), planOnly: Boolean = false): BackupPlan =
        database.withTransaction {
            val current = snapshot(0)
            if (planOnly) {
                require(incoming.workouts.isEmpty() && incoming.nutrition.meals.isEmpty() && incoming.photos.isEmpty()) { "Plan imports cannot contain completed activity or photos." }
                BackupPlanner.plan(current, incoming.copy(nutrition = incoming.nutrition.copy(reminderMinute = current.nutrition.reminderMinute), photoScope = false),
                    options.copy(mode = ImportMode.MERGE))
            } else BackupPlanner.plan(current, incoming, options)
        }

    suspend fun apply(plan: BackupPlan): ImportSummary = database.withTransaction {
        val desired = plan.desired.requireValid().canonical()
        val current = snapshot(0).requireValid().canonical()
        if (BackupJson.fingerprint(current) != plan.baselineFingerprint) throw StaleBackupPreviewException()
        val oldPhotos = database.photos().all().associateBy { it.id }
        val newPhotos = desired.photos.associateBy { it.id }
        val photosToWrite = desired.photos.filter { oldPhotos[it.id]?.portable() != it }
        if (photosToWrite.any { !photoExists(it) }) throw BackupException("A required photo is missing or damaged. Select the complete ZIP backup with its image files.")
        val oldRoutines = current.routines.associateBy { it.id }
        val oldWorkouts = current.workouts.associateBy { it.id }
        val newRoutines = desired.routines.associateBy { it.id }
        val newWorkouts = desired.workouts.associateBy { it.id }
        val routinesToWrite = desired.routines.filter { oldRoutines[it.id] != it }
        val workoutsToWrite = desired.workouts.filter { oldWorkouts[it.id] != it }
        val routinesToDelete = oldRoutines.keys.filter { it !in newRoutines || oldRoutines[it] != newRoutines[it] }
        val workoutsToDelete = oldWorkouts.keys.filter { it !in newWorkouts || oldWorkouts[it] != newWorkouts[it] }
        // Delete all affected parents before inserting their graphs, including active-slot changes.
        workoutsToDelete.chunked(500).forEach { dao.deleteSessions(it) }
        routinesToDelete.chunked(500).forEach { dao.deleteRoutines(it) }
        dao.insertRoutines(routinesToWrite.map { RoutineEntity(it.id, it.name, it.updatedAt) })
        routinesToWrite.forEach { routine -> dao.insertRoutineExercises(routine.exercises.map { row ->
            RoutineExerciseEntity(row.id, routine.id, row.catalogueId, row.name, row.metric.name, row.position,
                row.targetSets, row.targetValue, row.perSide, row.assistance)
        }) }
        workoutsToWrite.forEach { workout ->
            dao.insertSession(SessionEntity(workout.id, workout.name, workout.routineId, workout.startedAtEpochMillis,
                workout.completedAtEpochMillis, if (workout.completedAtEpochMillis == null) 1 else null))
            dao.insertSessionExercises(workout.exercises.map { logged ->
                val row = logged.exercise
                SessionExerciseEntity(row.id, workout.id, row.catalogueId, row.name, row.metric.name, row.position,
                    row.targetSets, row.targetValue, row.perSide, row.assistance)
            })
            workout.exercises.forEach { logged ->
                dao.insertSets(logged.sets.map { SetEntity(it.id, logged.exercise.id, it.position, it.value, it.weight, it.unit.name, it.assistance) })
                logged.draft?.let { dao.insertDrafts(listOf(SetDraftEntity(logged.exercise.id, it.value, it.weight, it.unit.name, it.assistance))) }
            }
        }
        val nutrition = database.nutrition()
        val old = current.nutrition; val next = desired.nutrition
        fun <T> affected(before: List<T>, after: List<T>, id: (T) -> String): Pair<List<String>, List<T>> {
            val beforeMap = before.associateBy(id); val afterMap = after.associateBy(id)
            return beforeMap.keys.filter { it !in afterMap || beforeMap[it] != afterMap[it] } to after.filter { beforeMap[id(it)] != it }
        }
        val (foodDeletes, foodWrites) = affected(old.foods, next.foods) { it.id }
        val (mealDeletes, mealWrites) = affected(old.meals, next.meals) { it.id }
        val (scheduleDeletes, scheduleWrites) = affected(old.schedules, next.schedules) { it.id }
        foodDeletes.chunked(500).forEach { nutrition.deleteFoods(it) }
        mealDeletes.chunked(500).forEach { nutrition.deleteMeals(it) }
        scheduleDeletes.chunked(500).forEach { nutrition.deleteSchedules(it) }
        foodWrites.forEach { nutrition.saveFood(it.entity()) }
        mealWrites.forEach { meal ->
            nutrition.saveMeal(MealEntity(meal.id, meal.date, meal.name, meal.notes, meal.updatedAt))
            nutrition.insertMealItems(meal.items.map { MealItemEntity(meal.id, it.columns()) })
        }
        scheduleWrites.forEach { schedule ->
            nutrition.saveSchedule(DietScheduleEntity(schedule.id, schedule.name, schedule.daysOfWeek,
                schedule.timeMinute, schedule.enabled, schedule.notes, schedule.updatedAt))
            nutrition.insertScheduleItems(schedule.items.map { ScheduleItemEntity(schedule.id, it.columns()) })
        }
        if (old.reminderMinute != next.reminderMinute) nutrition.saveSettings(NutritionSettingsEntity(reminderMinute = next.reminderMinute))
        oldPhotos.keys.filter { it !in newPhotos }.chunked(500).forEach { database.photos().delete(it) }
        photosToWrite.forEach { photo ->
            val gallery = oldPhotos[photo.id]?.takeIf { it.sha256 == photo.sha256 }?.galleryUri
            database.photos().save(photo.entity(gallery))
        }
        plan.summary
    }

    private suspend fun snapshot(exportedAt: Long): BackupDocument = BackupDocument(exportedAt,
        dao.allRoutines().map { row -> BackupRoutine(row.routine.id, row.routine.name, row.routine.updatedAt,
            row.orderedExercises.map { BackupExercise(it.id, it.name, it.position, BackupMetric.valueOf(it.metric),
                it.targetSets, it.targetValue, it.catalogueId, it.perSide, it.assistance) }) },
        dao.allSessions().map { row -> BackupWorkout(row.session.id, row.session.name, row.session.routineId,
            row.session.startedAt, row.session.completedAt, row.orderedExercises.map { logged ->
                val exercise = logged.exercise
                BackupLoggedExercise(BackupExercise(exercise.id, exercise.name, exercise.position, BackupMetric.valueOf(exercise.metric),
                    exercise.targetSets, exercise.targetValue, exercise.catalogueId, exercise.perSide, exercise.assistance),
                    logged.orderedSets.map { BackupSet(it.id, it.position, it.value, it.weight, WeightUnit.valueOf(it.unit), it.assistance) },
                    logged.drafts.singleOrNull()?.let { BackupDraft(it.value, it.weight, WeightUnit.valueOf(it.unit), it.assistance) })
            }) },
        nutrition = BackupNutrition(database.nutrition().foods().map { it.portable() }, database.nutrition().meals().map { it.portable() },
            database.nutrition().schedules().map { it.portable() }, database.nutrition().settings()?.reminderMinute ?: 1200),
        photos = database.photos().all().map { it.portable() },
    )
}
