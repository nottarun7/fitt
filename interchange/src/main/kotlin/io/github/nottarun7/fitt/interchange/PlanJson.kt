package io.github.nottarun7.fitt.interchange

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

@Serializable
data class PlanDocument(val sourceId: String, val routines: List<PlanRoutine> = emptyList(),
    val foods: List<PlanFood> = emptyList(), val dietSchedules: List<PlanDietSchedule> = emptyList(),
    @Required val format: String = "fitt.plan", @Required val schemaVersion: Int = 1)
@Serializable
data class PlanRoutine(val id: String, val name: String, val exercises: List<PlanExercise>)
@Serializable
data class PlanExercise(val id: String, val name: String, val metric: BackupMetric, val sets: Int, val target: Int,
    val perSide: Boolean = false, val assistance: Boolean = false)
@Serializable
data class PlanFood(val id: String, val name: String, val servingLabel: String, val calories: Double,
    val protein: Double, val carbs: Double, val fat: Double)
@Serializable
data class PlanDietSchedule(val id: String, val name: String, val days: List<String>, val time: String? = null,
    val notes: String = "", val items: List<PlanFoodItem> = emptyList())
@Serializable
data class PlanFoodItem(val foodId: String, val servings: Double)

/** A small plan-only boundary: no dates, epoch timestamps, positions, logged history, or invented nutrition. */
object PlanJson {
    fun decode(text: String): DecodedBackup {
        if (text.toByteArray(Charsets.UTF_8).size > BackupLimits.DOCUMENT_BYTES) throw BackupException("Plan exceeds the document size limit.")
        guardJson(text)
        val root = try { BackupJson.json.parseToJsonElement(text) as? JsonObject } catch (_: SerializationException) { null }
            ?: throw BackupException("A plan must be a JSON object.")
        // Kotlin's numeric serializers can accept quoted numbers. This public contract does not.
        val strings = setOf("format", "sourceId", "id", "name", "servingLabel", "metric", "notes", "foodId")
        val integers = setOf("schemaVersion", "sets", "target")
        val decimals = setOf("calories", "protein", "carbs", "fat", "servings")
        val booleans = setOf("perSide", "assistance")
        val arrays = setOf("routines", "exercises", "foods", "dietSchedules", "items")
        fun types(objectValue: JsonObject, path: String) {
            objectValue.forEach { (key, value) ->
                val primitive = value as? JsonPrimitive
                val valid = when (key) {
                    in strings -> primitive != null && primitive !is JsonNull && primitive.isString
                    "time" -> value is JsonNull || primitive?.isString == true
                    in integers -> primitive != null && !primitive.isString && primitive.intOrNull != null
                    in decimals -> primitive != null && !primitive.isString && primitive.doubleOrNull?.isFinite() == true
                    in booleans -> primitive != null && !primitive.isString && primitive.booleanOrNull != null
                    "days" -> value is JsonArray && value.all { it is JsonPrimitive && it !is JsonNull && it.isString }
                    in arrays -> value is JsonArray && value.all { it is JsonObject }
                    else -> true // The serializer below rejects unknown fields.
                }
                if (!valid) throw BackupException(listOf(ValidationIssue("$path.$key", "Incorrect JSON type; numbers and booleans must not be quoted.")))
                if (key in arrays) (value as JsonArray).forEachIndexed { index, child -> types(child as JsonObject, "$path.$key[$index]") }
            }
        }
        types(root, "plan")
        val plan = try { BackupJson.json.decodeFromJsonElement<PlanDocument>(root) }
        catch (_: SerializationException) { throw BackupException("Invalid plan fields. Use the fitt.plan v1 template; unknown fields and wrong types are rejected.") }
        catch (_: IllegalArgumentException) { throw BackupException("Invalid plan field value.") }
        fun requirePlan(valid: Boolean, path: String, message: String) { if (!valid) throw BackupException(listOf(ValidationIssue(path, message))) }
        fun key(value: String, path: String) = requirePlan(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")), path, "Use 1–100 letters, digits, dots, underscores, or hyphens; start with a letter or digit.")
        fun ids(values: List<String>, path: String) {
            values.forEach { key(it, path) }
            requirePlan(values.distinct().size == values.size, path, "IDs must be unique within this list.")
        }
        requirePlan(plan.format == "fitt.plan" && plan.schemaVersion == 1, "format", "Expected fitt.plan, schemaVersion 1.")
        key(plan.sourceId, "sourceId")
        requirePlan(plan.routines.isNotEmpty() || plan.foods.isNotEmpty() || plan.dietSchedules.isNotEmpty(), "plan", "Include at least one routine, food, or diet schedule.")
        val count = plan.routines.size.toLong() + plan.routines.sumOf { it.exercises.size.toLong() } + plan.foods.size +
            plan.dietSchedules.size + plan.dietSchedules.sumOf { it.items.size.toLong() }
        requirePlan(count < BackupLimits.RECORDS, "plan", "Too many plan records.")
        ids(plan.routines.map { it.id }, "routines.id"); ids(plan.foods.map { it.id }, "foods.id"); ids(plan.dietSchedules.map { it.id }, "dietSchedules.id")
        fun stable(vararg parts: String) = UUID.nameUUIDFromBytes((listOf("fitt.plan.v1", plan.sourceId) + parts)
            .joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)).toString()
        val foods = plan.foods.map { BackupFood(stable("food", it.id), it.name, it.servingLabel, it.calories, it.protein, it.carbs, it.fat, 0) }
        val lookup = plan.foods.zip(foods).associate { it.first.id to it.second }
        val weekdays = mapOf("MON" to DayOfWeek.MONDAY, "TUE" to DayOfWeek.TUESDAY, "WED" to DayOfWeek.WEDNESDAY,
            "THU" to DayOfWeek.THURSDAY, "FRI" to DayOfWeek.FRIDAY, "SAT" to DayOfWeek.SATURDAY, "SUN" to DayOfWeek.SUNDAY)
        val schedules = plan.dietSchedules.map { row ->
            requirePlan(row.days.isNotEmpty() && row.days.distinct().size == row.days.size && row.days.all { it in weekdays },
                "dietSchedules.${row.id}.days", "Use unique weekdays MON, TUE, WED, THU, FRI, SAT, SUN.")
            val minute = row.time?.let { time ->
                requirePlan(time.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")), "dietSchedules.${row.id}.time", "Use HH:MM (24-hour), or null if unspecified.")
                LocalTime.parse(time).let { it.hour * 60 + it.minute }
            } ?: -1
            val items = row.items.mapIndexed { index, item ->
                val food = lookup[item.foodId] ?: throw BackupException(listOf(ValidationIssue("dietSchedules.${row.id}.items[$index].foodId", "Unknown food ID ${item.foodId}.")))
                BackupPortion(stable("schedule-item", row.id, index.toString()), index, food.id, food.name, food.servingLabel,
                    item.servings, food.calories, food.protein, food.carbs, food.fat)
            }
            BackupDietSchedule(stable("schedule", row.id), row.name, row.days.fold(0) { mask, day -> mask or (1 shl (weekdays.getValue(day).value - 1)) },
                minute, true, row.notes, 0, items)
        }
        val routines = plan.routines.map { row ->
            ids(row.exercises.map { it.id }, "routines.${row.id}.exercises.id")
            requirePlan(row.exercises.isNotEmpty(), "routines.${row.id}.exercises", "Add at least one exercise.")
            BackupRoutine(stable("routine", row.id), row.name, 0, row.exercises.mapIndexed { index, exercise ->
                BackupExercise(stable("exercise", row.id, exercise.id), exercise.name, index, exercise.metric,
                    exercise.sets, exercise.target, null, exercise.perSide, exercise.assistance)
            })
        }
        val document = BackupDocument(0, routines, emptyList(), nutrition = BackupNutrition(foods = foods, schedules = schedules), photoScope = false).requireValid().canonical()
        return DecodedBackup(document, buildList {
            add("Plan import: creates routines, foods, and diet schedules. No workouts or meals are recorded as completed.")
            add("Stable IDs use sourceId '${plan.sourceId}'. Reuse sourceId and item IDs when revising this plan to detect duplicates.")
            if (schedules.any { it.items.isEmpty() }) add("Text-only diet plans contain no calorie estimates. Add foods with verified nutrition before logging them as meals.")
            if (schedules.any { it.timeMinute == -1 }) add("Some diet plans have no specified time; you can set one in Plans.")
        }, isPlan = true)
    }
}
