package io.github.nottarun7.fitt.interchange

import org.junit.Assert.*
import org.junit.Test

class PlanJsonTest {
    private fun example() = checkNotNull(javaClass.getResource("/fitt-plan-example.json")).readText()
    private val labelled = """{"format":"fitt.plan","schemaVersion":1,"sourceId":"recipe","routines":[],"foods":[
        {"id":"f","name":"Label food","servingLabel":"1 serving","calories":200,"protein":10,"carbs":25,"fat":6}],
        "dietSchedules":[{"id":"lunch","name":"Lunch","days":["MON","FRI"],"time":"13:30","notes":"","items":[{"foodId":"f","servings":1.5}]}]}"""
    @Test fun `bundled guide example imports as templates with unknown nutrition and time preserved`() {
        val result = BackupFiles.decode(example().toByteArray())
        assertTrue(result.isPlan)
        assertEquals(1, result.document.routines.size)
        assertEquals(BackupMetric.SECONDS, result.document.routines.single().exercises.last().metric)
        val schedule = result.document.nutrition.schedules.single()
        assertEquals(127, schedule.daysOfWeek); assertEquals(-1, schedule.timeMinute)
        assertTrue(schedule.items.isEmpty()); assertTrue(schedule.notes.isNotBlank())
        assertTrue(result.document.nutrition.foods.isEmpty()); assertTrue(result.document.nutrition.meals.isEmpty()); assertTrue(result.document.workouts.isEmpty())
    }
    @Test fun `guide JSON blocks are executable examples`() {
        val guide = checkNotNull(javaClass.getResource("/import-guide.md")).readText()
        val blocks = Regex("```json\\s*([\\s\\S]*?)```").findAll(guide).map { it.groupValues[1] }.toList()
        assertEquals(2, blocks.size)
        blocks.forEach { assertTrue(BackupJson.decode(it).isPlan) }
    }
    @Test fun `known nutrition references decimal servings and weekdays map exactly`() {
        val result = PlanJson.decode(labelled).document
        val schedule = result.nutrition.schedules.single()
        assertEquals(17, schedule.daysOfWeek); assertEquals(810, schedule.timeMinute)
        assertEquals(result.nutrition.foods.single().id, schedule.items.single().foodId)
        assertEquals(300.0, schedule.items.nutritionTotals().calories, 0.0)
    }
    @Test fun `stable IDs make unchanged plans idempotent and revisions use duplicate policies`() {
        val first = PlanJson.decode(example()).document
        assertEquals(first, PlanJson.decode(example()).document)
        assertFalse(BackupPlanner.plan(first, first).hasChanges)
        val changed = PlanJson.decode(example().replace("\"target\": 8", "\"target\": 12")).document
        assertEquals(first.routines.single().id, changed.routines.single().id)
        assertFalse(BackupPlanner.plan(first, changed).hasChanges)
        assertTrue(BackupPlanner.plan(first, changed, ImportOptions(duplicates = DuplicatePolicy.USE_IMPORTED)).hasChanges)
        val independent = PlanJson.decode(example().replace("my-training-and-diet-plan", "another-plan")).document
        assertNotEquals(first.routines.single().id, independent.routines.single().id)
    }
    @Test fun `malformed invented history references and missing values are rejected`() {
        val content = example()
        listOf(content.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"),
            content.replace("\"sourceId\":", "\"workouts\":[],\"sourceId\":"),
            content.replace("\"sets\": 3", "\"sets\": 0"),
            content.replace("\"target\": 8", "\"target\": \"8\""),
            content.replace("\"MON\", \"TUE\"", "\"MON\", \"MON\""),
            content.replace("\"time\": null", "\"time\": \"25:00\""),
            content.replace("my-training-and-diet-plan", "bad/id"),
            content.replace("\"id\": \"tuck-hold\"", "\"id\": \"chest-press\""),
            labelled.replace("\"foodId\":\"f\"", "\"foodId\":\"unknown\""),
            labelled.replace("\"protein\":10", "\"protein\":null"),
            labelled.replace("\"servings\":1.5", "\"servings\":-1"),
            content.replace("\"format\":", "\"format\":\"fitt.plan\",\"format\":"),
        ).forEachIndexed { index, bad -> assertThrows("Invalid example $index", BackupException::class.java) { BackupJson.decode(bad) } }
    }
    @Test fun `text plans roundtrip in every current backup format`() {
        val document = PlanJson.decode(example()).document
        BackupFileFormat.entries.forEach { format ->
            val result = BackupFiles.decode(BackupFiles.encode(document, format))
            assertEquals(document, result.document); assertFalse(result.isPlan)
        }
    }
    @Test fun `fixed v3 nutrition archive upgrades with all values retained`() {
        val result = BackupFiles.decode(checkNotNull(javaClass.getResource("/backup-v3.zip")).readBytes())
        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertEquals(2, result.document.nutrition.foods.size)
        assertEquals(285.0, result.document.nutrition.meals.single().items.nutritionTotals().calories, 0.0)
        assertTrue(result.notices.any { it.contains("v3") })
    }
}
