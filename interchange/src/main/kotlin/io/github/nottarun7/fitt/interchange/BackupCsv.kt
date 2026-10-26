package io.github.nottarun7.fitt.interchange

/** A normalized, reversible Fitt CSV: one metadata row and one row per stored record. */
object BackupCsv {
    private val legacyColumns = listOf("record_type", "id", "parent_id", "position", "name", "catalogue_id", "metric",
        "target_sets", "target_value", "per_side", "assistance", "updated_at", "started_at", "completed_at",
        "routine_id", "value", "weight", "unit", "draft_value", "draft_weight", "exported_at", "schema_version", "format", "text_encoding", "record_count")
    private val nutritionColumns = legacyColumns + listOf("serving_label", "calories", "protein", "carbs", "fat", "quantity", "date", "time_minute", "weekdays", "enabled", "notes", "food_id")
    private val columns = nutritionColumns + listOf("photo_scope", "sha256", "byte_count", "pixel_width", "pixel_height")
    private val stringColumns = setOf("id", "parent_id", "name", "catalogue_id", "routine_id", "draft_value", "draft_weight", "serving_label", "notes", "food_id")
    private const val ENCODING = "apostrophe-v1"

    fun encode(document: BackupDocument): String {
        val data = document.requireValid().canonical()
        val result = StringBuilder(columns.joinToString(",") + "\r\n")
        fun row(kind: String, vararg values: Pair<String, Any?>) {
            val cells = values.toMap() + ("record_type" to kind)
            result.append(columns.joinToString(",") { name ->
                var value = cells[name]?.toString().orEmpty()
                if (name in stringColumns) value = escapeText(value)
                "\"${value.replace("\"", "\"\"")}\""
            }).append("\r\n")
            if (result.length > BackupLimits.DOCUMENT_BYTES) throw BackupException("CSV exceeds the 10 MiB document limit.")
        }
        fun exercise(kind: String, parent: String, value: BackupExercise) = row(kind,
            "id" to value.id, "parent_id" to parent, "position" to value.position, "name" to value.name,
            "catalogue_id" to value.catalogueId, "metric" to value.metric, "target_sets" to value.targetSets,
            "target_value" to value.targetValue, "per_side" to value.perSide, "assistance" to value.assistance)
        row("metadata", "exported_at" to data.exportedAtEpochMillis, "schema_version" to data.schemaVersion,
            "format" to data.format, "text_encoding" to ENCODING, "record_count" to data.recordCount(), "photo_scope" to data.photoScope)
        data.routines.forEach { routine ->
            row("routine", "id" to routine.id, "name" to routine.name, "updated_at" to routine.updatedAt)
            routine.exercises.forEach { exercise("routine_exercise", routine.id, it) }
        }
        data.workouts.forEach { workout ->
            row("workout", "id" to workout.id, "name" to workout.name, "routine_id" to workout.routineId,
                "started_at" to workout.startedAtEpochMillis, "completed_at" to workout.completedAtEpochMillis)
            workout.exercises.forEach { logged ->
                exercise("workout_exercise", workout.id, logged.exercise)
                logged.sets.forEach { set -> row("set", "id" to set.id, "parent_id" to logged.exercise.id,
                    "position" to set.position, "value" to set.value, "weight" to set.weight, "unit" to set.unit,
                    "assistance" to set.assistance) }
                logged.draft?.let { draft -> row("draft", "id" to logged.exercise.id, "parent_id" to logged.exercise.id,
                    "draft_value" to draft.value, "draft_weight" to draft.weight, "unit" to draft.unit, "assistance" to draft.assistance) }
            }
        }
        fun portion(kind: String, parent: String, item: BackupPortion) = row(kind, "id" to item.id,
            "parent_id" to parent, "position" to item.position, "food_id" to item.foodId, "name" to item.name,
            "serving_label" to item.servingLabel, "quantity" to item.quantity, "calories" to item.calories,
            "protein" to item.protein, "carbs" to item.carbs, "fat" to item.fat)
        row("nutrition_settings", "time_minute" to data.nutrition.reminderMinute)
        data.nutrition.foods.forEach { food -> row("food", "id" to food.id, "name" to food.name,
            "serving_label" to food.servingLabel, "calories" to food.calories, "protein" to food.protein,
            "carbs" to food.carbs, "fat" to food.fat, "updated_at" to food.updatedAt) }
        data.nutrition.meals.forEach { meal ->
            row("meal", "id" to meal.id, "name" to meal.name, "date" to meal.date, "notes" to meal.notes, "updated_at" to meal.updatedAt)
            meal.items.forEach { portion("meal_item", meal.id, it) }
        }
        data.nutrition.schedules.forEach { schedule ->
            row("diet_schedule", "id" to schedule.id, "name" to schedule.name, "weekdays" to schedule.daysOfWeek,
                "time_minute" to schedule.timeMinute, "enabled" to schedule.enabled, "notes" to schedule.notes, "updated_at" to schedule.updatedAt)
            schedule.items.forEach { portion("schedule_item", schedule.id, it) }
        }
        data.photos.forEach { photo -> row("photo", "id" to photo.id, "date" to photo.date, "notes" to photo.caption,
            "updated_at" to photo.addedAt, "sha256" to photo.sha256, "byte_count" to photo.byteCount, "pixel_width" to photo.width, "pixel_height" to photo.height) }
        return result.toString().also {
            if (it.toByteArray(Charsets.UTF_8).size > BackupLimits.DOCUMENT_BYTES) throw BackupException("CSV exceeds the 10 MiB document limit.")
        }
    }

    fun decode(content: String): DecodedBackup {
        if (content.toByteArray(Charsets.UTF_8).size > BackupLimits.DOCUMENT_BYTES) throw BackupException("CSV exceeds the 10 MiB document limit.")
        val rows = parse(content.removePrefix("\uFEFF"))
        val header = rows.firstOrNull() ?: throw BackupException("CSV is empty.")
        val legacy = header.size == legacyColumns.size && header.toSet() == legacyColumns.toSet()
        val nutritionOnly = header.size == nutritionColumns.size && header.toSet() == nutritionColumns.toSet()
        val current = header.size == columns.size && header.toSet() == columns.toSet()
        if (!legacy && !nutritionOnly && !current) throw BackupException("Expected Fitt CSV columns. Generic CSV files are not supported.")
        val indices = header.withIndex().associate { it.value to it.index }
        val mapped = rows.drop(1).mapIndexed { index, cells ->
            if (cells.size != header.size) throw BackupException("CSV row ${index + 2} has the wrong number of columns.")
            CsvRow(indices, cells)
        }
        if (mapped.size > BackupLimits.RECORDS + 1) throw BackupException("Too many CSV records.")
        val metadata = mapped.filter { it["record_type"] == "metadata" }.singleOrNull()
            ?: throw BackupException("CSV requires exactly one metadata row.")
        if (metadata["format"] != WORKOUT_DOCUMENT_FORMAT || metadata["text_encoding"] != ENCODING) throw BackupException("Unrecognized Fitt CSV format or text encoding.")
        if (metadata["schema_version"] !in (if (legacy) setOf("2") else if (nutritionOnly) setOf("3", "4") else setOf("5"))) throw BackupException("Unsupported CSV schema version ${metadata["schema_version"]}.")
        fun allowed(row: Map<String, String>, vararg names: String) {
            val fields = names.toSet() + "record_type"
            if (row.any { it.key !in fields && it.value.isNotEmpty() }) throw BackupException("Unexpected populated field in a ${row["record_type"]} row.")
        }
        allowed(metadata, "exported_at", "schema_version", "format", "text_encoding", "record_count", "photo_scope")
        if (metadata["record_count"]?.toLongOrNull() != (mapped.size - 1).toLong()) throw BackupException("CSV record count does not match. The file may be incomplete.")
        fun text(row: Map<String, String>, name: String): String = unescapeText(row.getValue(name))
        fun optional(row: Map<String, String>, name: String) = text(row, name).takeIf { it.isNotEmpty() }
        fun integer(row: Map<String, String>, name: String) = row.getValue(name).toIntOrNull() ?: throw BackupException("Invalid integer in CSV $name.")
        fun long(row: Map<String, String>, name: String) = row.getValue(name).toLongOrNull() ?: throw BackupException("Invalid timestamp in CSV $name.")
        fun bool(row: Map<String, String>, name: String) = when (row[name]) {
            "true" -> true; "false" -> false; else -> throw BackupException("Invalid boolean in CSV $name.")
        }
        fun unit(row: Map<String, String>) = try { WeightUnit.valueOf(row.getValue("unit")) }
            catch (_: IllegalArgumentException) { throw BackupException("Unknown CSV weight unit.") }
        fun definition(row: Map<String, String>): BackupExercise {
            allowed(row, "id", "parent_id", "position", "name", "catalogue_id", "metric", "target_sets", "target_value", "per_side", "assistance")
            val metric = try { BackupMetric.valueOf(row.getValue("metric")) }
                catch (_: IllegalArgumentException) { throw BackupException("Unknown CSV tracking metric.") }
            return BackupExercise(text(row, "id"), text(row, "name"), integer(row, "position"), metric,
                integer(row, "target_sets"), integer(row, "target_value"), optional(row, "catalogue_id"), bool(row, "per_side"), bool(row, "assistance"))
        }
        val nutritionKinds = setOf("nutrition_settings", "food", "meal", "meal_item", "diet_schedule", "schedule_item")
        val kinds = setOf("metadata", "routine", "routine_exercise", "workout", "workout_exercise", "set", "draft") +
            (if (legacy) emptySet() else nutritionKinds) + (if (current) setOf("photo") else emptySet())
        if (mapped.any { it["record_type"] !in kinds }) throw BackupException("Unknown CSV record type.")
        fun kind(name: String) = mapped.filter { it["record_type"] == name }
        fun checkUnique(rowsOfKind: List<Map<String, String>>) {
            if (rowsOfKind.map { text(it, "id") }.distinct().size != rowsOfKind.size) throw BackupException("Duplicate CSV record IDs.")
        }
        kinds.filter { it != "metadata" && it != "nutrition_settings" }.forEach { checkUnique(kind(it)) }
        val routines = kind("routine")
        val routineExercises = kind("routine_exercise")
        val workouts = kind("workout")
        val workoutExercises = kind("workout_exercise")
        val sets = kind("set")
        val drafts = kind("draft")
        fun parents(children: List<Map<String, String>>, parents: List<Map<String, String>>) {
            val ids = parents.map { text(it, "id") }.toSet()
            if (children.any { text(it, "parent_id") !in ids }) throw BackupException("CSV contains an orphaned child record.")
        }
        parents(routineExercises, routines); parents(workoutExercises, workouts); parents(sets, workoutExercises); parents(drafts, workoutExercises)
        val routineChildren = routineExercises.groupBy { text(it, "parent_id") }
        val workoutChildren = workoutExercises.groupBy { text(it, "parent_id") }
        val setChildren = sets.groupBy { text(it, "parent_id") }
        val draftChildren = drafts.associateBy { text(it, "parent_id") }
        if (draftChildren.size != drafts.size || drafts.any { text(it, "id") != text(it, "parent_id") }) throw BackupException("Duplicate or invalid CSV draft record.")
        fun decimal(row: Map<String, String>, key: String) = row.getValue(key).toDoubleOrNull() ?: throw BackupException("Invalid CSV $key.")
        fun portions(children: List<Map<String, String>>, roots: List<Map<String, String>>): Map<String, List<BackupPortion>> {
            parents(children, roots)
            return children.groupBy { text(it, "parent_id") }.mapValues { (_, rows) -> rows.map { item ->
                allowed(item, "id", "parent_id", "position", "food_id", "name", "serving_label", "quantity", "calories", "protein", "carbs", "fat")
                BackupPortion(text(item, "id"), integer(item, "position"), optional(item, "food_id"), text(item, "name"), text(item, "serving_label"),
                    decimal(item, "quantity"), decimal(item, "calories"), decimal(item, "protein"), decimal(item, "carbs"), decimal(item, "fat"))
            } }
        }
        val nutrition = if (legacy) BackupNutrition() else {
            val settings = kind("nutrition_settings").singleOrNull() ?: throw BackupException("CSV requires one nutrition settings row.")
            allowed(settings, "time_minute")
            val mealRows = kind("meal"); val scheduleRows = kind("diet_schedule")
            val mealItems = portions(kind("meal_item"), mealRows); val scheduleItems = portions(kind("schedule_item"), scheduleRows)
            BackupNutrition(kind("food").map { food ->
                allowed(food, "id", "name", "serving_label", "calories", "protein", "carbs", "fat", "updated_at")
                BackupFood(text(food, "id"), text(food, "name"), text(food, "serving_label"), decimal(food, "calories"),
                    decimal(food, "protein"), decimal(food, "carbs"), decimal(food, "fat"), long(food, "updated_at"))
            }, mealRows.map { meal ->
                allowed(meal, "id", "name", "date", "notes", "updated_at")
                BackupMeal(text(meal, "id"), meal.getValue("date"), text(meal, "name"), text(meal, "notes"), long(meal, "updated_at"), mealItems[text(meal, "id")].orEmpty())
            }, scheduleRows.map { schedule ->
                allowed(schedule, "id", "name", "weekdays", "time_minute", "enabled", "notes", "updated_at")
                BackupDietSchedule(text(schedule, "id"), text(schedule, "name"), integer(schedule, "weekdays"), integer(schedule, "time_minute"),
                    bool(schedule, "enabled"), text(schedule, "notes"), long(schedule, "updated_at"), scheduleItems[text(schedule, "id")].orEmpty())
            }, integer(settings, "time_minute"))
        }
        val document = BackupDocument(long(metadata, "exported_at"), routines.map { routine ->
            allowed(routine, "id", "name", "updated_at")
            val id = text(routine, "id")
            BackupRoutine(id, text(routine, "name"), long(routine, "updated_at"), routineChildren[id].orEmpty().map(::definition))
        }, workouts.map { workout ->
            allowed(workout, "id", "name", "routine_id", "started_at", "completed_at")
            val id = text(workout, "id")
            BackupWorkout(id, text(workout, "name"), optional(workout, "routine_id"), long(workout, "started_at"),
                workout.getValue("completed_at").takeIf { it.isNotEmpty() }?.let { long(workout, "completed_at") },
                workoutChildren[id].orEmpty().map { exercise ->
                    val childId = text(exercise, "id")
                    BackupLoggedExercise(definition(exercise), setChildren[childId].orEmpty().map { set ->
                        allowed(set, "id", "parent_id", "position", "value", "weight", "unit", "assistance")
                        val load = set.getValue("weight").takeIf { it.isNotEmpty() }?.let {
                            it.toDoubleOrNull() ?: throw BackupException("Invalid CSV load.")
                        }
                        BackupSet(text(set, "id"), integer(set, "position"), integer(set, "value"), load, unit(set), bool(set, "assistance"))
                    }, draftChildren[childId]?.let { draft ->
                        allowed(draft, "id", "parent_id", "draft_value", "draft_weight", "unit", "assistance")
                        BackupDraft(text(draft, "draft_value"), text(draft, "draft_weight"), unit(draft), bool(draft, "assistance"))
                    })
                })
        }, nutrition = nutrition, photos = kind("photo").map { photo ->
            allowed(photo, "id", "date", "notes", "updated_at", "sha256", "byte_count", "pixel_width", "pixel_height")
            BackupPhoto(text(photo, "id"), photo.getValue("date"), text(photo, "notes"), long(photo, "updated_at"),
                photo.getValue("sha256"), long(photo, "byte_count"), integer(photo, "pixel_width"), integer(photo, "pixel_height"))
        }, photoScope = if (current) bool(metadata, "photo_scope") else false)
        return DecodedBackup(document.requireValid().canonical(), if (legacy) listOf("Converted a v2 workout CSV. It contains no nutrition data.") else emptyList())
    }

    private fun dangerous(value: String): Boolean = value.startsWith("'") || value.firstOrNull() in listOf('\t', '\r', '\n') ||
        value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')
    private fun escapeText(value: String) = if (dangerous(value)) "'$value" else value
    private fun unescapeText(value: String): String {
        if (value.startsWith("'")) {
            val restored = value.drop(1)
            if (!dangerous(restored)) throw BackupException("Invalid escaped CSV text. Preserve the Fitt text encoding when editing.")
            return restored
        }
        if (dangerous(value)) throw BackupException("Unsafe unescaped CSV text. Use Fitt's export format.")
        return value
    }

    /** Share the header index instead of allocating 25 hash-map entries for every row. */
    private class CsvRow(private val columnIndices: Map<String, Int>, private val cells: List<String>) : AbstractMap<String, String>() {
        override fun get(key: String): String? = columnIndices[key]?.let { cells[it] }
        override val entries: Set<Map.Entry<String, String>> get() = object : AbstractSet<Map.Entry<String, String>>() {
            override val size: Int get() = columnIndices.size
            override fun iterator(): Iterator<Map.Entry<String, String>> {
                val headerIterator = columnIndices.entries.iterator()
                return object : Iterator<Map.Entry<String, String>> {
                    override fun hasNext() = headerIterator.hasNext()
                    override fun next(): Map.Entry<String, String> {
                        val (key, index) = headerIterator.next()
                        return java.util.AbstractMap.SimpleImmutableEntry(key, cells[index])
                    }
                }
            }
        }
    }

    private fun parse(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var index = 0
        var quoted = false
        var closed = false
        var started = false
        fun cell() { row.add(field.toString()); field.setLength(0); closed = false; started = false }
        fun line() {
            cell(); rows.add(row); row = ArrayList()
            if (rows.size > BackupLimits.RECORDS + 2) throw BackupException("Too many CSV records.")
        }
        while (index < text.length) {
            val char = text[index++]
            if (quoted) {
                if (char == '"') {
                    if (index < text.length && text[index] == '"') { field.append('"'); index++ }
                    else { quoted = false; closed = true }
                } else field.append(char)
            } else when (char) {
                '"' -> { if (started || closed || field.isNotEmpty()) throw BackupException("Malformed CSV quoting."); quoted = true; started = true }
                ',' -> cell()
                '\r', '\n' -> { if (char == '\r' && index < text.length && text[index] == '\n') index++; line() }
                else -> { if (closed) throw BackupException("Unexpected text after a closing CSV quote."); field.append(char); started = true }
            }
            if (field.length > BackupLimits.TEXT_LENGTH * 2) throw BackupException("CSV field is too long.")
            if (row.size > columns.size) throw BackupException("Too many CSV columns.")
        }
        if (quoted) throw BackupException("Unclosed CSV quote.")
        if (started || closed || field.isNotEmpty() || row.isNotEmpty()) line()
        return rows
    }
}
