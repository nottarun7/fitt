package io.github.nottarun7.fitt.interchange

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID
import java.io.ByteArrayOutputStream
import kotlinx.serialization.ExperimentalSerializationApi

object BackupJson {
    internal val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    @OptIn(ExperimentalSerializationApi::class)
    fun encode(document: BackupDocument): String {
        val output = object : ByteArrayOutputStream() {
            override fun write(value: Int) {
                if (size() >= BackupLimits.DOCUMENT_BYTES) throw BackupException("Backup exceeds the 10 MiB document limit.")
                super.write(value)
            }
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                if (size().toLong() + length > BackupLimits.DOCUMENT_BYTES) throw BackupException("Backup exceeds the 10 MiB document limit.")
                super.write(buffer, offset, length)
            }
        }
        json.encodeToStream(document.requireValid().canonical(), output)
        return output.toString("UTF-8")
    }

    fun decode(content: String): DecodedBackup {
        if (content.toByteArray(Charsets.UTF_8).size > BackupLimits.DOCUMENT_BYTES) throw BackupException("Backup exceeds the 10 MiB document limit.")
        val text = content.removePrefix("\uFEFF")
        guardJson(text)
        val root = try { json.parseToJsonElement(text) as? JsonObject } catch (_: SerializationException) { null }
            ?: throw BackupException("Expected a valid JSON object.")
        val format = root["format"] as? JsonPrimitive
        if (format?.isString == true && format.content == "fitt.plan") return PlanJson.decode(text)
        if (format?.isString != true || format.content != WORKOUT_DOCUMENT_FORMAT) throw BackupException("Not a Fitt workout backup.")
        val field = root["schemaVersion"] as? JsonPrimitive
        val version = field?.takeUnless { it.isString }?.intOrNull ?: throw BackupException("An integer schemaVersion is required.")
        if (version == 1) {
            val legacy = WorkoutJson.decode(text)
            if (legacy !is WorkoutReadResult.Success) throw BackupException("The legacy v1 document is invalid.")
            return DecodedBackup(migrate(legacy.document).requireValid().canonical(), listOf(
                "Converted a legacy v1 file. It did not store timed metrics, assistance, catalogue links, input drafts, or workout targets. Missing targets use defaults.",
            ))
        }
        if (version !in 2..BACKUP_SCHEMA_VERSION) throw BackupException("Unsupported backup version $version. No records were imported.")
        if (version == 2 && "nutrition" in root) throw BackupException("Nutrition is not a v2 field.")
        if (version < 5 && ("photos" in root || "photoScope" in root)) throw BackupException("Photo fields require backup version 5.")
        var upgraded = if (version == 2) JsonObject(root + ("nutrition" to json.encodeToJsonElement(BackupNutrition()))) else root
        if (version < 5) upgraded = JsonObject(upgraded + mapOf("photos" to JsonArray(emptyList()), "photoScope" to JsonPrimitive(false)))
        upgraded = JsonObject(upgraded + ("schemaVersion" to JsonPrimitive(BACKUP_SCHEMA_VERSION)))
        val document = try { json.decodeFromJsonElement<BackupDocument>(upgraded) }
        catch (_: SerializationException) { throw BackupException("Missing, unknown, or incorrectly typed backup fields.") }
        catch (_: IllegalArgumentException) { throw BackupException("Invalid backup field value.") }
        return DecodedBackup(document.requireValid().canonical(), when (version) {
            2 -> listOf("Converted a v2 workout backup. It contains no nutrition data.")
            3, 4 -> listOf("Converted a v$version workout and nutrition backup. Existing progress photos stay saved.")
            else -> emptyList()
        })
    }

    fun fingerprint(document: BackupDocument): String = sha256(encode(document.copy(exportedAtEpochMillis = 0, photoScope = true)).toByteArray(Charsets.UTF_8))

    private fun migrate(old: WorkoutDocument): BackupDocument {
        fun stable(vararg parts: String) = UUID.nameUUIDFromBytes(parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)).toString()
        return BackupDocument(old.exportedAtEpochMillis,
            old.routines.map { routine -> BackupRoutine(routine.id, routine.name, old.exportedAtEpochMillis,
                routine.exercises.mapIndexed { index, row -> BackupExercise(
                    stable("v1", "routine", routine.id, row.id), row.name, index, BackupMetric.REPS, row.targetSets,
                    row.targetReps ?: 8, null, false, false,
                ) }) },
            old.workouts.map { workout -> BackupWorkout(workout.id, workout.name, workout.routineId,
                workout.startedAtEpochMillis, workout.completedAtEpochMillis,
                workout.exercises.mapIndexed { index, row -> BackupLoggedExercise(
                    BackupExercise(stable("v1", "workout", workout.id, row.id), row.name, index, BackupMetric.REPS,
                        row.sets.size.coerceAtLeast(1), row.sets.firstOrNull()?.reps ?: 8, null, false, false),
                    row.sets.mapIndexed { setIndex, set -> BackupSet(stable("v1", "set", workout.id, row.id, setIndex.toString()),
                        setIndex, set.reps, set.weight, set.weightUnit, false) }, null,
                ) }) },
            photoScope = false,
        )
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Bound nesting before the JSON parser runs, and reject duplicate (including escaped) keys. */
internal fun guardJson(text: String) {
    var index = 0
    fun fail(): Nothing = throw BackupException("Malformed JSON, repeated keys, or excessive nesting.")
    fun whitespace() { while (index < text.length && text[index] in " \t\r\n") index++ }
    fun string(): String {
        if (index >= text.length || text[index] != '"') fail()
        val start = index++
        while (index < text.length) {
            when (text[index++]) {
                '\\' -> { if (index >= text.length) fail(); index++ }
                '"' -> return text.substring(start, index)
            }
        }
        fail()
    }
    fun value(depth: Int) {
        if (depth > BackupLimits.JSON_DEPTH) fail()
        whitespace()
        if (index >= text.length) fail()
        when (text[index]) {
            '{' -> {
                index++; whitespace()
                val keys = HashSet<String>()
                if (index < text.length && text[index] == '}') { index++; return }
                while (true) {
                    whitespace()
                    val token = string()
                    val key = try { BackupJson.json.decodeFromString<String>(token) } catch (_: SerializationException) { fail() }
                    if (!keys.add(key)) fail()
                    whitespace(); if (index >= text.length || text[index++] != ':') fail()
                    value(depth + 1); whitespace()
                    if (index >= text.length) fail()
                    val separator = text[index++]
                    if (separator == '}') break
                    if (separator != ',') fail()
                }
            }
            '[' -> {
                index++; whitespace()
                if (index < text.length && text[index] == ']') { index++; return }
                while (true) {
                    value(depth + 1); whitespace()
                    if (index >= text.length) fail()
                    val separator = text[index++]
                    if (separator == ']') break
                    if (separator != ',') fail()
                }
            }
            '"' -> { string() }
            else -> {
                val start = index
                while (index < text.length && text[index] !in ",]} \t\r\n") index++
                if (index == start) fail()
            }
        }
    }
    value(0); whitespace()
    if (index != text.length) fail()
}
