package io.github.nottarun7.fitt.interchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutJsonTest {
    private fun fixture(): String = checkNotNull(javaClass.getResource("/workout-v1.json")).readText()

    private fun sample(): WorkoutDocument =
        (WorkoutJson.decode(fixture()) as WorkoutReadResult.Success).document

    @Test
    fun `v1 fixture preserves identifiers units timestamps and bodyweight sets`() {
        val document = sample()
        assertEquals("routine-1", document.routines.single().id)
        assertEquals(1000L, document.workouts.single().startedAtEpochMillis)
        assertEquals(1800L, document.workouts.single().completedAtEpochMillis)
        assertEquals(
            listOf(LoggedSet(5, 42.5), LoggedSet(5, 95.0, WeightUnit.LB), LoggedSet(8), LoggedSet(8, 0.0)),
            document.workouts.single().exercises.single().sets,
        )
    }

    @Test
    fun `v1 document survives an export import round trip`() {
        val original = sample()
        assertEquals(WorkoutReadResult.Success(original), WorkoutJson.decode(WorkoutJson.encode(original)))
    }

    @Test
    fun `empty library survives a round trip with an explicit schema header`() {
        val empty = WorkoutDocument(0, emptyList(), emptyList())
        val encoded = WorkoutJson.encode(empty)
        assertTrue(encoded.contains("\"format\": \"fitt.workouts\""))
        assertTrue(encoded.contains("\"schemaVersion\": 1"))
        assertEquals(WorkoutReadResult.Success(empty), WorkoutJson.decode(encoded))
    }

    @Test
    fun `unicode names survive a round trip`() {
        val document = sample().copy(routines = listOf(sample().routines.single().copy(name = "शक्ति 🏋️")))
        assertEquals(WorkoutReadResult.Success(document), WorkoutJson.decode(WorkoutJson.encode(document)))
    }

    @Test
    fun `malformed or non object input is rejected`() {
        listOf("", "{", "[]", "null", "42", "\"hello\"").forEach { input ->
            assertTrue(input, WorkoutJson.decode(input) is WorkoutReadResult.InvalidJson)
        }
    }

    @Test
    fun `foreign or missing format is rejected`() {
        listOf("{}", fixture().replace("fitt.workouts", "other.app")).forEach { input ->
            assertTrue(WorkoutJson.decode(input) is WorkoutReadResult.InvalidDocument)
        }
    }

    @Test
    fun `missing or non integer schema version is rejected`() {
        listOf("\"1\"", "1.5", "null", "true").forEach { value ->
            assertTrue(WorkoutJson.decode(fixture().replace("\"schemaVersion\": 1", "\"schemaVersion\": $value"))
                is WorkoutReadResult.InvalidJson)
        }
        assertTrue(WorkoutJson.decode(fixture().replace("\"schemaVersion\": 1,", ""))
            is WorkoutReadResult.InvalidJson)
    }

    @Test
    fun `future schema is identified before decoding its changed fields`() {
        assertEquals(
            WorkoutReadResult.UnsupportedVersion(2),
            WorkoutJson.decode("""{"format":"fitt.workouts","schemaVersion":2,"futureData":{}}"""),
        )
    }

    @Test
    fun `older schema is rejected until an explicit migration exists`() {
        assertEquals(
            WorkoutReadResult.UnsupportedVersion(0),
            WorkoutJson.decode(fixture().replace("\"schemaVersion\": 1", "\"schemaVersion\": 0")),
        )
    }

    @Test
    fun `missing collections are rejected instead of defaulting to empty`() {
        assertTrue(WorkoutJson.decode(
            """{"format":"fitt.workouts","schemaVersion":1,"exportedAtEpochMillis":0}""",
        ) is WorkoutReadResult.InvalidJson)
    }

    @Test
    fun `unknown fields and units are rejected instead of losing data`() {
        listOf(
            fixture().replace("\"targetSets\": 3", "\"futureField\": 1, \"targetSets\": 3"),
            fixture().replace("\"KG\"", "\"STONE\""),
            fixture().replace("\"reps\": 5", "\"reps\": null"),
        ).forEach { input ->
            assertTrue(WorkoutJson.decode(input) is WorkoutReadResult.InvalidJson)
        }
    }

    @Test
    fun `import requires an explicit weight unit instead of assuming kilograms`() {
        val missingUnits = fixture().replace(", \"weightUnit\": \"KG\"", "")
        assertTrue(WorkoutJson.decode(missingUnits) is WorkoutReadResult.InvalidJson)
    }

    @Test
    fun `domain errors report a path without partially accepting a document`() {
        val result = WorkoutJson.decode(fixture().replace("\"reps\": 5", "\"reps\": -1"))
        assertTrue(result is WorkoutReadResult.InvalidDocument)
        val issues = (result as WorkoutReadResult.InvalidDocument).issues
        assertEquals("workouts[0].exercises[0].sets[0].reps", issues.first().path)
        assertEquals(2, issues.size)
    }

    @Test
    fun `invalid in memory documents cannot be exported`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutJson.encode(sample().copy(exportedAtEpochMillis = -1))
        }
    }
}
