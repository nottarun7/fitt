package io.github.nottarun7.fitt.interchange

import org.junit.Assert.*
import org.junit.Test

class NutritionBackupTest {
    @Test fun `fixed legacy v2 ZIP JSON and CSV all migrate without losing an active draft`() {
        val bytes = checkNotNull(javaClass.getResource("/backup-v2.zip")).readBytes()
        val archive = BackupFiles.decode(bytes)
        assertEquals(BACKUP_SCHEMA_VERSION, archive.document.schemaVersion)
        assertEquals(BackupNutrition(), archive.document.nutrition)
        assertEquals("3.", archive.document.workouts.single { it.completedAtEpochMillis == null }.exercises.single().draft!!.weight)
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "backup.json" || entry.name == "backup.csv") {
                    val decoded = BackupFiles.decode(zip.readBytes())
                    assertEquals(archive.document, decoded.document); assertTrue(decoded.notices.isNotEmpty())
                }
            }
        }
    }
    private val food = BackupFood("f", "Paneer, homemade शक्ति", "100 g", 265.0, 18.0, 1.2, 20.8, 10)
    private val item = BackupPortion("mi", 4, food.id, food.name, food.servingLabel, 1.5, food.calories, food.protein, food.carbs, food.fat)
    private fun document() = BackupDocument(100, emptyList(), emptyList(), nutrition = BackupNutrition(listOf(food),
        listOf(BackupMeal("m", "2026-10-25", "=Lunch", "Notes\nwith, commas", 50, listOf(item))),
        listOf(BackupDietSchedule("d", "Weekday lunch", 31, 780, true, "'quote", 60, listOf(item.copy(id = "di")))), 1110))

    @Test fun `all formats preserve nutrition snapshots schedule settings and escaping`() {
        val data = document().canonical()
        BackupFileFormat.entries.forEach { format -> assertEquals(format.name, data, BackupFiles.decode(BackupFiles.encode(data, format)).document) }
        assertEquals(NutritionTotals(397.5, 27.0, 1.7999999999999998, 31.200000000000003), listOf(item).nutritionTotals())
    }
    @Test fun `current schema requires nutrition fields while v2 upgrades explicitly`() {
        val json = BackupJson.encode(document())
        assertThrows(BackupException::class.java) { BackupJson.decode(json.replace("\"reminderMinute\": 1110", "\"unknown\": 1110")) }
        val v2 = """{"format":"fitt.workouts","schemaVersion":2,"exportedAtEpochMillis":10,"routines":[],"workouts":[]}"""
        val decoded = BackupJson.decode(v2)
        assertEquals(BACKUP_SCHEMA_VERSION, decoded.document.schemaVersion); assertEquals(BackupNutrition(), decoded.document.nutrition)
        assertTrue(decoded.notices.isNotEmpty())
    }
    @Test fun `invalid nutrition never becomes a backup`() {
        val original = document()
        val broken = listOf(
            original.nutrition.copy(foods = listOf(food.copy(protein = Double.NaN))),
            original.nutrition.copy(foods = listOf(food.copy(calories = -1.0))),
            original.nutrition.copy(meals = listOf(original.nutrition.meals.single().copy(date = "2026-02-30"))),
            original.nutrition.copy(meals = listOf(original.nutrition.meals.single().copy(items = listOf(item.copy(quantity = 0.0))))),
            original.nutrition.copy(schedules = listOf(original.nutrition.schedules.single().copy(daysOfWeek = 0))),
            original.nutrition.copy(reminderMinute = 1440),
        )
        broken.forEach { assertThrows(BackupException::class.java) { BackupJson.encode(original.copy(nutrition = it)) } }
    }
    @Test fun `nutrition duplicate policies preserve or replace whole roots and remap copied references`() {
        val current = document()
        val incoming = current.copy(nutrition = current.nutrition.copy(foods = listOf(food.copy(calories = 200.0)),
            meals = listOf(current.nutrition.meals.single().copy(name = "Updated lunch"))))
        val kept = BackupPlanner.plan(current, incoming)
        assertEquals(current.nutrition, kept.desired.nutrition)
        assertEquals(2, kept.summary.changedDuplicates)
        val replaced = BackupPlanner.plan(current, incoming, ImportOptions(duplicates = DuplicatePolicy.USE_IMPORTED))
        assertEquals(incoming.nutrition.canonical(), replaced.desired.nutrition)
        val copied = BackupPlanner.plan(current, incoming, ImportOptions(duplicates = DuplicatePolicy.KEEP_BOTH))
        val newFood = copied.desired.nutrition.foods.single { it.id != "f" }
        val newMeal = copied.desired.nutrition.meals.single { it.id != "m" }
        assertEquals(newFood.id, newMeal.items.single().foodId)
        assertNotEquals(item.id, newMeal.items.single().id)
        assertTrue(copied.desired.validateBackup().isEmpty())
        val repeat = BackupPlanner.plan(replaced.desired, incoming)
        assertFalse(repeat.hasChanges)
    }
    @Test fun `old workout merges preserve nutrition while exact restore clears it`() {
        val current = document(); val empty = BackupDocument(100, emptyList(), emptyList())
        assertEquals(current.nutrition, BackupPlanner.plan(current, empty).desired.nutrition)
        val exact = BackupPlanner.plan(current, empty, ImportOptions(mode = ImportMode.RESTORE_EXACTLY))
        assertEquals(BackupNutrition(), exact.desired.nutrition)
        assertEquals(1, exact.summary.foods.removed); assertEquals(1, exact.summary.meals.removed); assertEquals(1, exact.summary.schedules.removed)
    }
    @Test fun `imported item collisions are remapped and reimport is idempotent`() {
        val current = document()
        val incoming = current.copy(nutrition = current.nutrition.copy(meals = listOf(current.nutrition.meals.single().copy(id = "other"))))
        val first = BackupPlanner.plan(current, incoming)
        assertEquals(2, first.desired.nutrition.meals.size)
        assertEquals(1, first.summary.remappedChildIds)
        assertFalse(BackupPlanner.plan(first.desired, incoming).hasChanges)
    }
}
