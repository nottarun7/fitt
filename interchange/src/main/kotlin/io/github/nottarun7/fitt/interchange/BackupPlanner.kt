package io.github.nottarun7.fitt.interchange

import java.util.UUID

enum class ImportMode { MERGE, RESTORE_EXACTLY }
enum class DuplicatePolicy { KEEP_EXISTING, USE_IMPORTED, KEEP_BOTH }
enum class ActiveWorkoutPolicy { KEEP_CURRENT, USE_IMPORTED }

data class ImportOptions(
    val mode: ImportMode = ImportMode.MERGE,
    val duplicates: DuplicatePolicy = DuplicatePolicy.KEEP_EXISTING,
    val activeWorkout: ActiveWorkoutPolicy = ActiveWorkoutPolicy.KEEP_CURRENT,
)

data class RecordChanges(val added: Int = 0, val changed: Int = 0, val removed: Int = 0)

data class ImportSummary(
    val addedRoutines: Int, val changedRoutines: Int, val removedRoutines: Int,
    val addedWorkouts: Int, val changedWorkouts: Int, val removedWorkouts: Int,
    val identicalDuplicates: Int, val changedDuplicates: Int, val skippedRecords: Int,
    val remappedChildIds: Int, val activeConflict: Boolean,
    val foods: RecordChanges = RecordChanges(), val meals: RecordChanges = RecordChanges(),
    val schedules: RecordChanges = RecordChanges(), val reminderTimeChanged: Boolean = false,
    val photos: RecordChanges = RecordChanges(),
)

data class BackupDuplicate(val kind: String, val id: String, val currentName: String, val incomingName: String,
                           val currentCount: Int, val incomingCount: Int)

data class BackupPlan(
    val baselineFingerprint: String,
    val desired: BackupDocument,
    val options: ImportOptions,
    val summary: ImportSummary,
    val duplicates: List<BackupDuplicate> = emptyList(),
) {
    val hasChanges: Boolean = baselineFingerprint != BackupJson.fingerprint(desired)
}

/** Matches whole routines/workouts by ID. Titles alone never cause unrelated records to merge. */
object BackupPlanner {
    fun plan(current: BackupDocument, imported: BackupDocument, options: ImportOptions = ImportOptions(),
             newId: () -> String = { UUID.randomUUID().toString() }): BackupPlan {
        val before = current.requireValid().canonical()
        val incoming = imported.requireValid().canonical()
        val oldRoutines = before.routines.associateBy { it.id }
        val oldWorkouts = before.workouts.associateBy { it.id }
        var identical = 0
        var conflicts = 0
        var usedIncoming = 0
        var remapped = 0
        var activeConflict = false
        val duplicateDetails = ArrayList<BackupDuplicate>()

        fun fresh(reserved: MutableSet<String>): String {
            repeat(1000) {
                val id = newId()
                if (id.isNotBlank() && id.length <= 200 && reserved.add(id)) return id
            }
            throw BackupException("Could not allocate a unique ID for imported records.")
        }

        var routines: List<BackupRoutine>
        var workouts: List<BackupWorkout>
        if (options.mode == ImportMode.RESTORE_EXACTLY) {
            routines = incoming.routines
            workouts = incoming.workouts
            usedIncoming = routines.size + workouts.size
        } else {
            val selectedRoutines = before.routines.associateBy { it.id }.toMutableMap()
            val selectedWorkouts = before.workouts.associateBy { it.id }.toMutableMap()
            val incomingRoutineIds = HashSet<String>()
            val incomingWorkoutIds = HashSet<String>()
            val routineMapping = HashMap<String, String>()
            val reservedRoutines = (oldRoutines.keys + incoming.routines.map { it.id }).toMutableSet()
            val reservedWorkouts = (oldWorkouts.keys + incoming.workouts.map { it.id }).toMutableSet()
            incoming.routines.forEach { row ->
                val existing = oldRoutines[row.id]
                when {
                    existing == null -> { selectedRoutines[row.id] = row; incomingRoutineIds.add(row.id) }
                    sameRoutine(existing, row) -> identical++
                    else -> {
                        conflicts++
                        duplicateDetails.add(BackupDuplicate("routine", row.id, existing.name, row.name, existing.exercises.size, row.exercises.size))
                        when (options.duplicates) {
                            DuplicatePolicy.KEEP_EXISTING -> Unit
                            DuplicatePolicy.USE_IMPORTED -> { selectedRoutines[row.id] = row; incomingRoutineIds.add(row.id) }
                            DuplicatePolicy.KEEP_BOTH -> {
                                val copyId = fresh(reservedRoutines)
                                selectedRoutines[copyId] = row.copy(id = copyId)
                                incomingRoutineIds.add(copyId)
                                routineMapping[row.id] = copyId
                            }
                        }
                    }
                }
            }
            incoming.workouts.forEach { original ->
                val row = original.copy(routineId = routineMapping[original.routineId] ?: original.routineId)
                val existing = oldWorkouts[row.id]
                when {
                    existing == null -> { selectedWorkouts[row.id] = row; incomingWorkoutIds.add(row.id) }
                    sameWorkout(existing, row) -> identical++
                    else -> {
                        conflicts++
                        duplicateDetails.add(BackupDuplicate("workout", row.id, existing.name, row.name,
                            existing.exercises.sumOf { it.sets.size }, row.exercises.sumOf { it.sets.size }))
                        when (options.duplicates) {
                            DuplicatePolicy.KEEP_EXISTING -> Unit
                            DuplicatePolicy.USE_IMPORTED -> { selectedWorkouts[row.id] = row; incomingWorkoutIds.add(row.id) }
                            DuplicatePolicy.KEEP_BOTH -> {
                                val copyId = fresh(reservedWorkouts)
                                selectedWorkouts[copyId] = row.copy(id = copyId)
                                incomingWorkoutIds.add(copyId)
                            }
                        }
                    }
                }
            }
            val active = selectedWorkouts.values.filter { it.completedAtEpochMillis == null }
            if (active.size > 1) {
                activeConflict = true
                val local = active.singleOrNull { it.id !in incomingWorkoutIds }
                val importedActive = active.singleOrNull { it.id in incomingWorkoutIds }
                if (local == null || importedActive == null) throw BackupException("Conflicting unfinished workouts require an explicit choice.")
                val remove = if (options.activeWorkout == ActiveWorkoutPolicy.KEEP_CURRENT) importedActive.id else local.id
                selectedWorkouts.remove(remove)
                incomingWorkoutIds.remove(remove)
            }
            usedIncoming = incomingRoutineIds.size + incomingWorkoutIds.size

            // Kept records own their IDs. Remap imported child collisions instead of overwriting another parent.
            val routineChildren = selectedRoutines.filterKeys { it !in incomingRoutineIds }.values.flatMap { it.exercises }.map { it.id }.toMutableSet()
            val workoutChildren = selectedWorkouts.filterKeys { it !in incomingWorkoutIds }.values.flatMap { it.exercises }.map { it.exercise.id }.toMutableSet()
            val setIds = selectedWorkouts.filterKeys { it !in incomingWorkoutIds }.values.flatMap { it.exercises }.flatMap { it.sets }.map { it.id }.toMutableSet()
            fun child(id: String, used: MutableSet<String>): String {
                if (used.add(id)) return id
                remapped++
                return fresh(used)
            }
            routines = selectedRoutines.values.map { row -> if (row.id !in incomingRoutineIds) row else row.copy(
                exercises = row.exercises.map { it.copy(id = child(it.id, routineChildren)) },
            ) }
            workouts = selectedWorkouts.values.map { row -> if (row.id !in incomingWorkoutIds) row else row.copy(
                exercises = row.exercises.map { logged -> logged.copy(
                    exercise = logged.exercise.copy(id = child(logged.exercise.id, workoutChildren)),
                    sets = logged.sets.map { it.copy(id = child(it.id, setIds)) },
                ) },
            ) }
        }
        val nutrition = if (options.mode == ImportMode.RESTORE_EXACTLY) {
            usedIncoming += incoming.nutrition.foods.size + incoming.nutrition.meals.size + incoming.nutrition.schedules.size
            incoming.nutrition
        } else {
            val foodMapping = HashMap<String, String>()
            fun <T> merge(old: List<T>, rows: List<T>, kind: String, id: (T) -> String, name: (T) -> String,
                count: (T) -> Int, same: (T, T) -> Boolean, copyId: (T, String) -> T, remapItems: (T, MutableSet<String>) -> T): List<T> {
                val existing = old.associateBy(id)
                val selected = existing.toMutableMap()
                val incomingIds = HashSet<String>()
                val reserved = (existing.keys + rows.map(id)).toMutableSet()
                rows.forEach { row ->
                    val key = id(row); val currentRow = existing[key]
                    when {
                        currentRow == null -> { selected[key] = row; incomingIds.add(key) }
                        same(currentRow, row) -> identical++
                        else -> {
                            conflicts++
                            duplicateDetails.add(BackupDuplicate(kind, key, name(currentRow), name(row), count(currentRow), count(row)))
                            when (options.duplicates) {
                                DuplicatePolicy.KEEP_EXISTING -> Unit
                                DuplicatePolicy.USE_IMPORTED -> { selected[key] = row; incomingIds.add(key) }
                                DuplicatePolicy.KEEP_BOTH -> {
                                    val copied = fresh(reserved)
                                    selected[copied] = copyId(row, copied); incomingIds.add(copied)
                                    if (kind == "food") foodMapping[key] = copied
                                }
                            }
                        }
                    }
                }
                usedIncoming += incomingIds.size
                val usedChildren = HashSet<String>()
                // Register kept child IDs before assigning IDs to imported graphs.
                selected.filterKeys { it !in incomingIds }.values.forEach { remapItems(it, usedChildren) }
                return selected.map { (key, value) -> if (key in incomingIds) remapItems(value, usedChildren) else value }
            }
            fun portions(items: List<BackupPortion>, used: MutableSet<String>): List<BackupPortion> = items.map { item ->
                val key = if (used.add(item.id)) item.id else { remapped++; fresh(used) }
                item.copy(id = key)
            }
            fun refs(items: List<BackupPortion>) = items.map { it.copy(foodId = foodMapping[it.foodId] ?: it.foodId) }
            val foods = merge(before.nutrition.foods, incoming.nutrition.foods, "food", { it.id }, { it.name }, { 1 },
                { a, b -> a == b }, { row, id -> row.copy(id = id) }, { row, _ -> row })
            val meals = merge(before.nutrition.meals, incoming.nutrition.meals.map { it.copy(items = refs(it.items)) }, "meal",
                { it.id }, { it.name }, { it.items.size },
                { a, b -> a.copy(items = a.items.map { it.copy(id = "") }) == b.copy(items = b.items.map { it.copy(id = "") }) },
                { row, id -> row.copy(id = id) }, { row, used -> row.copy(items = portions(row.items, used)) })
            val schedules = merge(before.nutrition.schedules, incoming.nutrition.schedules.map { it.copy(items = refs(it.items)) }, "diet schedule",
                { it.id }, { it.name }, { it.items.size },
                { a, b -> a.copy(items = a.items.map { it.copy(id = "") }) == b.copy(items = b.items.map { it.copy(id = "") }) },
                { row, id -> row.copy(id = id) }, { row, used -> row.copy(items = portions(row.items, used)) })
            BackupNutrition(foods, meals, schedules,
                if (options.duplicates == DuplicatePolicy.USE_IMPORTED) incoming.nutrition.reminderMinute else before.nutrition.reminderMinute)
        }
        val photos = if (!incoming.photoScope) before.photos else if (options.mode == ImportMode.RESTORE_EXACTLY) {
            usedIncoming += incoming.photos.size
            incoming.photos
        } else {
            val selected = before.photos.associateBy { it.id }.toMutableMap()
            val reserved = (selected.keys + incoming.photos.map { it.id }).toMutableSet()
            incoming.photos.forEach { row ->
                val old = selected[row.id]
                when {
                    old == null -> { selected[row.id] = row; usedIncoming++ }
                    old == row -> identical++
                    else -> {
                        conflicts++
                        duplicateDetails.add(BackupDuplicate("photo", row.id, "${old.date} ${old.caption}", "${row.date} ${row.caption}", 1, 1))
                        when (options.duplicates) {
                            DuplicatePolicy.KEEP_EXISTING -> Unit
                            DuplicatePolicy.USE_IMPORTED -> { selected[row.id] = row; usedIncoming++ }
                            DuplicatePolicy.KEEP_BOTH -> { val id = fresh(reserved); selected[id] = row.copy(id = id); usedIncoming++ }
                        }
                    }
                }
            }
            selected.values.toList()
        }
        val desired = incoming.copy(routines = routines, workouts = workouts, nutrition = nutrition, photos = photos, photoScope = true).requireValid().canonical()
        val newRoutines = desired.routines.associateBy { it.id }
        val newWorkouts = desired.workouts.associateBy { it.id }
        fun <T> changes(old: List<T>, rows: List<T>, id: (T) -> String): RecordChanges {
            val oldMap = old.associateBy(id); val newMap = rows.associateBy(id)
            return RecordChanges(newMap.keys.count { it !in oldMap }, newMap.count { (key, row) -> key in oldMap && oldMap[key] != row }, oldMap.keys.count { it !in newMap })
        }
        return BackupPlan(BackupJson.fingerprint(before), desired, options, ImportSummary(
            addedRoutines = newRoutines.keys.count { it !in oldRoutines },
            changedRoutines = newRoutines.count { (id, row) -> id in oldRoutines && oldRoutines[id] != row },
            removedRoutines = oldRoutines.keys.count { it !in newRoutines },
            addedWorkouts = newWorkouts.keys.count { it !in oldWorkouts },
            changedWorkouts = newWorkouts.count { (id, row) -> id in oldWorkouts && oldWorkouts[id] != row },
            removedWorkouts = oldWorkouts.keys.count { it !in newWorkouts },
            identicalDuplicates = identical, changedDuplicates = conflicts,
            skippedRecords = incoming.routines.size + incoming.workouts.size + incoming.nutrition.foods.size + incoming.nutrition.meals.size + incoming.nutrition.schedules.size + incoming.photos.size - usedIncoming,
            remappedChildIds = remapped, activeConflict = activeConflict,
            foods = changes(before.nutrition.foods, nutrition.foods) { it.id },
            meals = changes(before.nutrition.meals, nutrition.meals) { it.id },
            schedules = changes(before.nutrition.schedules, nutrition.schedules) { it.id },
            reminderTimeChanged = before.nutrition.reminderMinute != nutrition.reminderMinute,
            photos = changes(before.photos, photos) { it.id },
        ), duplicateDetails)
    }

    private fun sameRoutine(first: BackupRoutine, second: BackupRoutine): Boolean =
        first.copy(exercises = first.exercises.map { it.copy(id = "") }) ==
            second.copy(exercises = second.exercises.map { it.copy(id = "") })

    private fun sameWorkout(first: BackupWorkout, second: BackupWorkout): Boolean {
        fun clean(row: BackupWorkout) = row.copy(exercises = row.exercises.map { it.copy(
            exercise = it.exercise.copy(id = ""), sets = it.sets.map { set -> set.copy(id = "") },
        ) })
        return clean(first) == clean(second)
    }
}
