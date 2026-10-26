package io.github.nottarun7.fitt.interchange

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

const val BACKUP_SCHEMA_VERSION = 5

object BackupLimits {
    const val FILE_BYTES = 12 * 1024 * 1024
    const val DOCUMENT_BYTES = 10 * 1024 * 1024
    const val ARCHIVE_BYTES = 24 * 1024 * 1024
    const val RECORDS = 100_000
    const val TEXT_LENGTH = 16_384
    const val JSON_DEPTH = 32
}

@Serializable
data class BackupDocument(
    val exportedAtEpochMillis: Long,
    val routines: List<BackupRoutine>,
    val workouts: List<BackupWorkout>,
    @Required val format: String = WORKOUT_DOCUMENT_FORMAT,
    @Required val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    @Required val nutrition: BackupNutrition = BackupNutrition(),
    @Required val photos: List<BackupPhoto> = emptyList(),
    @Required val photoScope: Boolean = true,
)

@Serializable
data class BackupRoutine(val id: String, val name: String, val updatedAt: Long, val exercises: List<BackupExercise>)

@Serializable
enum class BackupMetric { REPS, SECONDS }

@Serializable
data class BackupExercise(
    val id: String,
    val name: String,
    val position: Int,
    val metric: BackupMetric,
    val targetSets: Int,
    val targetValue: Int,
    val catalogueId: String?,
    val perSide: Boolean,
    val assistance: Boolean,
)

@Serializable
data class BackupWorkout(
    val id: String,
    val name: String,
    val routineId: String?,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val exercises: List<BackupLoggedExercise>,
)

@Serializable
data class BackupLoggedExercise(val exercise: BackupExercise, val sets: List<BackupSet>, val draft: BackupDraft?)

@Serializable
data class BackupSet(
    val id: String,
    val position: Int,
    val value: Int,
    val weight: Double?,
    val unit: WeightUnit,
    val assistance: Boolean,
)

/** Raw unfinished text deliberately need not be a valid logged set. */
@Serializable
data class BackupDraft(val value: String, val weight: String, val unit: WeightUnit, val assistance: Boolean)

class BackupException(val issues: List<ValidationIssue>) : IllegalArgumentException(
    issues.take(3).joinToString("\n") { "${it.path}: ${it.message}" },
) {
    constructor(message: String) : this(listOf(ValidationIssue("file", message)))
}

data class DecodedBackup(val document: BackupDocument, val notices: List<String> = emptyList(), val isPlan: Boolean = false)

fun BackupDocument.canonical(): BackupDocument = copy(
    photos = photos.sortedBy { it.id },
    nutrition = nutrition.canonical(),
    routines = routines.sortedBy { it.id }.map { it.copy(exercises = it.exercises.sortedBy { row -> row.position }) },
    workouts = workouts.sortedBy { it.id }.map { workout -> workout.copy(
        exercises = workout.exercises.sortedBy { it.exercise.position }.map { it.copy(sets = it.sets.sortedBy { set -> set.position }) },
    ) },
)

internal fun BackupDocument.recordCount(): Long = routines.size.toLong() + workouts.size + routines.sumOf { it.exercises.size.toLong() } +
    workouts.sumOf { row -> row.exercises.sumOf { 1L + it.sets.size + if (it.draft != null) 1 else 0 } } + nutrition.recordCount() + photos.size

fun BackupDocument.validateBackup(): List<ValidationIssue> = buildList {
    addAll(nutrition.validateNutrition())
    photos.take(PhotoLimits.COUNT + 1).forEach { addAll(it.validatePhoto()) }
    if (photos.size > PhotoLimits.COUNT) add(ValidationIssue("photos", "At most 200 progress photos are supported."))
    if (photos.map { it.id }.distinct().size != photos.size) add(ValidationIssue("photos", "Duplicate photo IDs."))
    if (!photoScope && photos.isNotEmpty()) add(ValidationIssue("photoScope", "A data-only file cannot contain photo records."))
    if (photos.groupBy { it.sha256 }.values.any { rows -> rows.map { Triple(it.byteCount, it.width, it.height) }.distinct().size > 1 })
        add(ValidationIssue("photos", "Photo records with one hash must have matching sizes and dimensions."))
    fun check(condition: Boolean, path: String, message: String) {
        if (!condition && size < 100) add(ValidationIssue(path, message))
    }
    fun text(value: String, path: String, nonblank: Boolean = true, limit: Int = BackupLimits.TEXT_LENGTH) {
        check(!nonblank || value.isNotBlank(), path, "Must not be blank.")
        check(value.length <= limit, path, "Text exceeds $limit characters.")
        var valid = true
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char == '\u0000' || char.isLowSurrogate()) valid = false
            if (char.isHighSurrogate()) {
                if (index >= value.length || !value[index].isLowSurrogate()) valid = false else index++
            }
        }
        check(valid, path, "Contains invalid Unicode or a null character.")
    }
    fun id(value: String, path: String) = text(value, path, limit = 200)
    fun uniqueIds(values: List<String>, path: String) {
        val seen = HashSet<String>()
        values.forEachIndexed { index, value ->
            id(value, "$path[$index].id")
            check(seen.add(value), "$path[$index].id", "Duplicate ID in this table.")
        }
    }
    fun positions(values: List<Int>, path: String) {
        val seen = HashSet<Int>()
        values.forEachIndexed { index, value ->
            check(value >= 0, "$path[$index].position", "Must not be negative.")
            check(seen.add(value), "$path[$index].position", "Duplicate position within this parent.")
        }
    }
    fun exercise(row: BackupExercise, path: String) {
        text(row.name, "$path.name")
        row.catalogueId?.let { id(it, "$path.catalogueId") }
        check(row.targetSets > 0, "$path.targetSets", "Must be positive.")
        check(row.targetValue > 0, "$path.targetValue", "Must be positive.")
    }
    check(format == WORKOUT_DOCUMENT_FORMAT, "format", "Not a Fitt workout backup.")
    check(schemaVersion == BACKUP_SCHEMA_VERSION, "schemaVersion", "Unsupported backup version.")
    check(exportedAtEpochMillis >= 0, "exportedAtEpochMillis", "Must not be negative.")
    val count = recordCount()
    check(count <= BackupLimits.RECORDS, "records", "Backup exceeds ${BackupLimits.RECORDS} records.")
    uniqueIds(routines.map { it.id }, "routines")
    uniqueIds(routines.flatMap { it.exercises }.map { it.id }, "routineExercises")
    uniqueIds(workouts.map { it.id }, "workouts")
    uniqueIds(workouts.flatMap { it.exercises }.map { it.exercise.id }, "workoutExercises")
    uniqueIds(workouts.flatMap { it.exercises }.flatMap { it.sets }.map { it.id }, "sets")
    check(workouts.count { it.completedAtEpochMillis == null } <= 1, "workouts", "Only one unfinished workout is allowed.")
    routines.forEachIndexed { index, row ->
        val path = "routines[$index]"
        text(row.name, "$path.name")
        check(row.updatedAt >= 0, "$path.updatedAt", "Must not be negative.")
        positions(row.exercises.map { it.position }, "$path.exercises")
        row.exercises.forEachIndexed { child, value -> exercise(value, "$path.exercises[$child]") }
    }
    workouts.forEachIndexed { index, row ->
        val path = "workouts[$index]"
        text(row.name, "$path.name")
        row.routineId?.let { id(it, "$path.routineId") }
        check(row.startedAtEpochMillis >= 0, "$path.startedAtEpochMillis", "Must not be negative.")
        check(row.completedAtEpochMillis == null || row.completedAtEpochMillis >= row.startedAtEpochMillis,
            "$path.completedAtEpochMillis", "Completion must not precede the start.")
        positions(row.exercises.map { it.exercise.position }, "$path.exercises")
        row.exercises.forEachIndexed { child, logged ->
            val childPath = "$path.exercises[$child]"
            exercise(logged.exercise, childPath)
            positions(logged.sets.map { it.position }, "$childPath.sets")
            logged.sets.forEachIndexed { setIndex, set ->
                check(set.value > 0, "$childPath.sets[$setIndex].value", "Reps or seconds must be positive.")
                check(set.weight == null || (set.weight.isFinite() && set.weight >= 0),
                    "$childPath.sets[$setIndex].weight", "Load must be finite and non-negative.")
            }
            logged.draft?.let {
                check(row.completedAtEpochMillis == null, "$childPath.draft", "Finished workouts cannot contain unfinished input.")
                text(it.value, "$childPath.draft.value", nonblank = false)
                text(it.weight, "$childPath.draft.weight", nonblank = false)
            }
        }
    }
}

fun BackupDocument.requireValid(): BackupDocument {
    val problems = validateBackup()
    if (problems.isNotEmpty()) throw BackupException(problems)
    return this
}
