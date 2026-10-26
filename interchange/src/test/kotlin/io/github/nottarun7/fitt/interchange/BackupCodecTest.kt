package io.github.nottarun7.fitt.interchange

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    @Test fun `checksums use standard SHA256 and JSON export is bounded while writing`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256("abc".toByteArray()))
        val original = backupFixture().routines.first().exercises.single()
        val longName = "a".repeat(BackupLimits.TEXT_LENGTH)
        val big = BackupDocument(1, listOf(BackupRoutine("r", "Large", 1,
            (1..1000).map { original.copy(id = "x-$it", name = longName, position = it) })), emptyList())
        rejects { BackupJson.encode(big) }
    }
    @Test fun `all formats preserve every field including unfinished input and empty parents`() {
        val expected = backupFixture().canonical()
        BackupFileFormat.entries.forEach { format ->
            assertEquals(format.name, expected, BackupFiles.decode(BackupFiles.encode(expected, format)).document)
        }
        val restored = BackupFiles.decode(BackupFiles.encode(expected, BackupFileFormat.ZIP)).document
        assertEquals("not valid yet", restored.workouts.first { it.id == "w-active" }.exercises.single().draft!!.value)
        assertEquals(listOf(1, 4, 9), restored.workouts.first { it.id == "w-done" }.exercises.single().sets.map { it.position })
        assertEquals("deleted-routine", restored.workouts.first { it.id == "w-done" }.routineId)
    }

    @Test fun `fixed v2 JSON contract decodes timed metrics assistance and drafts`() {
        val content = """{"format":"fitt.workouts","schemaVersion":2,"exportedAtEpochMillis":10,"routines":[],"workouts":[
            {"id":"w","name":"Hold","routineId":null,"startedAtEpochMillis":1,"completedAtEpochMillis":null,
            "exercises":[{"exercise":{"id":"x","name":"Tuck","position":0,"metric":"SECONDS","targetSets":3,
            "targetValue":20,"catalogueId":null,"perSide":true,"assistance":true},"sets":[{"id":"s","position":0,
            "value":15,"weight":10.5,"unit":"LB","assistance":true}],"draft":{"value":"1.","weight":"",
            "unit":"KG","assistance":false}}]}]}"""
        val restored = BackupJson.decode(content).document.workouts.single().exercises.single()
        assertEquals(BackupMetric.SECONDS, restored.exercise.metric)
        assertTrue(restored.exercise.perSide)
        assertEquals(BackupSet("s", 0, 15, 10.5, WeightUnit.LB, true), restored.sets.single())
        assertEquals("1.", restored.draft!!.value)
    }

    @Test fun `fixed CSV contract preserves empty records`() {
        val header = "record_type,id,parent_id,position,name,catalogue_id,metric,target_sets,target_value,per_side,assistance,updated_at,started_at,completed_at,routine_id,value,weight,unit,draft_value,draft_weight,exported_at,schema_version,format,text_encoding,record_count"
        val metadata = listOf("metadata") + List(19) { "" } + listOf("10", "2", "fitt.workouts", "apostrophe-v1", "1")
        val routine = listOf("routine", "r", "", "", "My routine") + List(6) { "" } + listOf("5") + List(13) { "" }
        val decoded = BackupCsv.decode(header + "\r\n" + metadata.joinToString(",") + "\r\n" + routine.joinToString(",") + "\r\n").document
        assertEquals(listOf(BackupRoutine("r", "My routine", 5, emptyList())), decoded.routines)
        assertTrue(decoded.workouts.isEmpty())
    }

    @Test fun `CSV protects formula cells and reverses quotes commas newlines and Unicode`() {
        val fixture = backupFixture()
        val csv = BackupCsv.encode(fixture)
        assertTrue(csv.contains("'=SUM(1,2)"))
        assertTrue(csv.contains("\"\"शक्ति\"\""))
        assertEquals(fixture.canonical(), BackupCsv.decode("\uFEFF" + csv).document)
        listOf("'literal", "  +formula", "\t=thing", "@name", "-name").forEach { name ->
            val data = fixture.copy(routines = listOf(fixture.routines.first().copy(name = name)))
            assertEquals(name, BackupCsv.decode(BackupCsv.encode(data)).document.routines.single().name)
        }
    }

    @Test fun `JSON rejects unknown missing duplicate and escaped duplicate fields`() {
        val json = BackupJson.encode(backupFixture())
        rejects { BackupJson.decode(json.replaceFirst("\"format\":", "\"extra\":0,\"format\":")) }
        rejects { BackupJson.decode(json.replaceFirst("\"metric\": \"SECONDS\",", "")) }
        rejects { BackupJson.decode(json.replaceFirst("\"schemaVersion\": 5", "\"schemaVersion\":2,\"schemaVersion\":2")) }
        rejects { BackupJson.decode(json.replaceFirst("\"schemaVersion\": 5", "\"schemaVersion\":2,\"schema\\u0056ersion\":2")) }
        rejects { BackupJson.decode(json.replace("\"LB\"", "\"STONE\"")) }
        rejects { BackupJson.decode("[".repeat(40) + "0" + "]".repeat(40)) }
    }

    @Test fun `unsupported schemas and invalid UTF8 are rejected before importing`() {
        rejects { BackupJson.decode(BackupJson.encode(backupFixture()).replace("\"schemaVersion\": 5", "\"schemaVersion\": 99")) }
        rejects { BackupFiles.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
        rejects { BackupFiles.readBounded(ByteArrayInputStream(ByteArray(9)), 8) }
        rejects { BackupFiles.decode(ByteArray(BackupLimits.FILE_BYTES + 1)) }
    }

    @Test fun `legacy v1 migration is explicit stable and collision safe`() {
        val old = WorkoutDocument(10, listOf(
            WorkoutRoutine("a/b", "One", listOf(PlannedExercise("c", "Press", 3, null))),
            WorkoutRoutine("a", "Two", listOf(PlannedExercise("b/c", "Row", 2, 5))),
        ), emptyList())
        val raw = WorkoutJson.encode(old)
        val first = BackupJson.decode(raw)
        assertTrue(first.notices.isNotEmpty())
        assertEquals(first, BackupJson.decode(raw))
        assertEquals(2, first.document.routines.flatMap { it.exercises }.map { it.id }.distinct().size)
        assertEquals(8, first.document.routines.first { it.id == "a/b" }.exercises.single().targetValue)
        val fixture = checkNotNull(javaClass.getResource("/workout-v1.json")).readText()
        assertEquals(1, BackupJson.decode(fixture).document.workouts.size)
    }

    @Test fun `domain validation reports paths for duplicate IDs positions and invalid values`() {
        val data = backupFixture()
        val routine = data.routines.first()
        rejects { BackupJson.encode(data.copy(routines = listOf(routine, routine))) }
        val repeatedRoutine = routine.copy(exercises = listOf(routine.exercises.single(), routine.exercises.single()))
        val repeated = data.copy(routines = listOf(repeatedRoutine))
        rejects { BackupJson.encode(repeated) }
        val workout = data.workouts.first()
        val exercise = workout.exercises.single()
        val broken = exercise.copy(sets = listOf(exercise.sets.first().copy(value = 0, weight = Double.NaN)))
        val issues = data.copy(workouts = listOf(workout.copy(exercises = listOf(broken)))).validateBackup()
        assertTrue(issues.any { it.path.endsWith(".value") })
        assertTrue(issues.any { it.path.endsWith(".weight") })
        val active = data.workouts.first { it.completedAtEpochMillis == null }
        val multipleActive = data.copy(workouts = listOf(active, active.copy(id = "other")))
        rejects { BackupJson.encode(multipleActive) }
        val invalidUnicode = data.copy(routines = listOf(routine.copy(name = "\uD800")))
        rejects { BackupJson.encode(invalidUnicode) }
    }

    @Test fun `CSV rejects orphan rows duplicate metadata invalid quoting and unsafe text`() {
        val csv = BackupCsv.encode(backupFixture())
        rejects { BackupCsv.decode(csv.replace("\"routine_exercise\",\"rx-1\",\"r-1\"", "\"routine_exercise\",\"rx-1\",\"missing\"")) }
        rejects { BackupCsv.decode(csv + csv.lineSequence().drop(1).first() + "\r\n") }
        rejects { BackupCsv.decode(csv.replaceFirst("\"metadata\"", "\"metadata\"junk")) }
        rejects { BackupCsv.decode(csv.replace("'=SUM", "=SUM")) }
        rejects { BackupCsv.decode(csv.replaceFirst("\"true\"", "\"maybe\"")) }
        val simple = BackupCsv.encode(BackupDocument(1, listOf(BackupRoutine("r", "Routine", 1, emptyList())), emptyList()))
        rejects { BackupCsv.decode(simple.lineSequence().take(2).joinToString("\r\n") + "\r\n") }
        val lines = simple.lineSequence().filter { it.isNotEmpty() }.toMutableList()
        val cells = lines[2].split(',').toMutableList()
        cells[17] = "\"KG\"" // A routine row must not smuggle a set-only field.
        lines[2] = cells.joinToString(",")
        rejects { BackupCsv.decode(lines.joinToString("\r\n")) }
    }

    @Test fun `ZIP rejects checksum mismatches missing entries traversal truncated files and oversized expansion`() {
        val encoded = BackupFiles.encode(backupFixture(), BackupFileFormat.ZIP)
        val entries = unzip(encoded)
        rejects { BackupFiles.decode(zip(entries - "backup.csv")) }
        rejects { BackupFiles.decode(zip(entries + ("../outside" to byteArrayOf(1)))) }
        rejects { BackupFiles.decode(encoded.copyOf(encoded.size - 22)) }
        rejects { BackupFiles.decode(zip(entries + ("backup.json" to "{}".toByteArray()))) }
        rejects { BackupFiles.decode(zip(entries + ("backup.json" to ByteArray(BackupLimits.DOCUMENT_BYTES + 1)))) }
    }

    @Test fun `ZIP rejects disagreement even when both payload checksums match`() {
        val entries = unzip(BackupFiles.encode(backupFixture(), BackupFileFormat.ZIP)).toMutableMap()
        val different = BackupCsv.encode(backupFixture().copy(exportedAtEpochMillis = 9999)).toByteArray()
        entries["backup.csv"] = different
        entries["manifest.json"] = """{"format":"fitt.backup.archive","version":1,"jsonSha256":"${sha256(entries.getValue("backup.json"))}","csvSha256":"${sha256(different)}"}""".toByteArray()
        rejects { BackupFiles.decode(zip(entries)) }
    }

    private fun rejects(block: () -> Unit) { assertThrows(BackupException::class.java, block) }
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { input -> while (true) {
            val entry = input.nextEntry ?: break
            put(entry.name, input.readBytes())
        } }
    }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
}
