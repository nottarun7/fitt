package io.github.nottarun7.fitt.interchange

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

sealed interface WorkoutReadResult {
    data class Success(val document: WorkoutDocument) : WorkoutReadResult
    data class InvalidJson(val message: String) : WorkoutReadResult
    data class InvalidDocument(val issues: List<ValidationIssue>) : WorkoutReadResult
    data class UnsupportedVersion(val version: Int) : WorkoutReadResult
}

/** Pure serialization boundary; importing here never writes to a database. */
object WorkoutJson {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        // Reject unknown fields instead of silently discarding data on the next export.
        ignoreUnknownKeys = false
    }

    fun encode(document: WorkoutDocument): String {
        val issues = document.validate()
        require(issues.isEmpty()) { "Invalid workout document: $issues" }
        return json.encodeToString(document)
    }

    fun decode(content: String): WorkoutReadResult {
        val root = try {
            json.parseToJsonElement(content) as? JsonObject
                ?: return WorkoutReadResult.InvalidJson("Expected a JSON object.")
        } catch (_: SerializationException) {
            return WorkoutReadResult.InvalidJson("Malformed JSON.")
        }

        val format = root["format"] as? JsonPrimitive
        if (format?.isString != true || format.content != WORKOUT_DOCUMENT_FORMAT) {
            return WorkoutReadResult.InvalidDocument(
                listOf(ValidationIssue("format", "Not a Fitt workout document.")),
            )
        }
        val versionField = root["schemaVersion"] as? JsonPrimitive
        val version = versionField?.takeUnless { it.isString }?.intOrNull
            ?: return WorkoutReadResult.InvalidJson("An integer schemaVersion is required.")
        // Inspect the version before decoding fields a future schema could have changed.
        if (version != WORKOUT_SCHEMA_VERSION) return WorkoutReadResult.UnsupportedVersion(version)

        val document = try {
            json.decodeFromJsonElement<WorkoutDocument>(root)
        } catch (_: SerializationException) {
            return WorkoutReadResult.InvalidJson("Missing, unknown, or invalid workout fields.")
        } catch (_: IllegalArgumentException) {
            return WorkoutReadResult.InvalidJson("Invalid workout field value.")
        }
        val issues = document.validate()
        return if (issues.isEmpty()) WorkoutReadResult.Success(document)
        else WorkoutReadResult.InvalidDocument(issues)
    }
}
