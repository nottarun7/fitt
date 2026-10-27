package io.github.nottarun7.fitt

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nottarun7.fitt.data.*
import io.github.nottarun7.fitt.progress.PhotoLibrary
import io.github.nottarun7.fitt.progress.ProgressAnalytics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Open the already-released schema, rather than seeding through the new build's write APIs. */
@RunWith(AndroidJUnit4::class)
class ReleasedDataContinuityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        WorkoutDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())

    private val tables = listOf("routines", "routine_exercises", "sessions", "session_exercises",
        "logged_sets", "set_drafts", "foods", "meals", "meal_items", "diet_schedules",
        "schedule_items", "nutrition_settings", "progress_photos")

    private fun snapshot(database: SupportSQLiteDatabase): Map<String, List<List<Any?>>> =
        tables.associateWith { table ->
            val key = if (table == "set_drafts") "exerciseId" else "id"
            database.query("SELECT * FROM $table ORDER BY $key").use { rows ->
                buildList {
                    while (rows.moveToNext()) add((0 until rows.columnCount).map { column ->
                        when (rows.getType(column)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> rows.getLong(column)
                            Cursor.FIELD_TYPE_FLOAT -> rows.getDouble(column)
                            Cursor.FIELD_TYPE_BLOB -> rows.getBlob(column).toList()
                            else -> rows.getString(column)
                        }
                    })
                }
            }
        }

    @Test fun releasedV3DataAndPhotoBytesRemainUnchangedAfterOpeningAndReadingTheUpdatedApp() = runBlocking {
        val name = "released-continuity-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "released-photos-${UUID.randomUUID()}").apply { check(mkdirs()) }
        var opened: WorkoutDatabase? = null
        try {
            val originalImage = File(root, "original.jpg")
            val bitmap = Bitmap.createBitmap(16, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            try { originalImage.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
            finally { bitmap.recycle() }
            val bytes = originalImage.readBytes()
            val hash = PhotoLibrary.digest(originalImage)
            val privateImage = File(root, "$hash.jpg")
            check(originalImage.renameTo(privateImage))
            val galleryUri = "content://test.gallery.example/progress/keep-existing-reference"
            val baseline = migration.createDatabase(name, 3)
            val expected = try {
                baseline.execSQL("INSERT INTO routines VALUES ('routine', 'Original routine', 100)")
                baseline.execSQL("INSERT INTO routine_exercises VALUES ('rx', 'routine', 'machine-chest-press', 'Original press', 'REPS', 2, 3, 8, 1, 0)")
                baseline.execSQL("INSERT INTO sessions VALUES ('complete', 'Original completed session', 'routine', 1000, 2000, NULL)")
                baseline.execSQL("INSERT INTO sessions VALUES ('active', 'Original unfinished session', 'routine', 3000, NULL, 1)")
                baseline.execSQL("INSERT INTO session_exercises VALUES ('wx-complete', 'complete', 'machine-chest-press', 'Completed snapshot', 'REPS', 0, 3, 8, 1, 0)")
                baseline.execSQL("INSERT INTO session_exercises VALUES ('wx-active', 'active', NULL, 'Custom timed snapshot', 'SECONDS', 4, 2, 20, 0, 1)")
                baseline.execSQL("INSERT INTO logged_sets VALUES ('finished-set', 'wx-complete', 2, 8, 0.0, 'LB', 0)")
                baseline.execSQL("INSERT INTO logged_sets VALUES ('active-set', 'wx-active', 1, 20, NULL, 'KG', 1)")
                baseline.execSQL("INSERT INTO set_drafts VALUES ('wx-active', 'not ready', '42.', 'LB', 1)")
                baseline.execSQL("INSERT INTO foods VALUES ('food', 'Original custom food', '1 bowl', 400.0, 20.0, 50.0, 10.0, 100)")
                baseline.execSQL("INSERT INTO meals VALUES ('meal', '2026-10-25', 'Original logged meal', 'Keep meal note', 200)")
                baseline.execSQL("INSERT INTO meal_items VALUES ('meal', 'meal-item', 2, 'food', 'Stored meal snapshot', '1 bowl', 1.5, 400.0, 20.0, 50.0, 10.0)")
                baseline.execSQL("INSERT INTO diet_schedules VALUES ('schedule', 'Original diet schedule', 31, 780, 1, 'Keep preparation note', 300)")
                baseline.execSQL("INSERT INTO diet_schedules VALUES ('text-plan', 'Original text plan', 127, -1, 0, 'Nutrition values are unspecified', 400)")
                baseline.execSQL("INSERT INTO schedule_items VALUES ('schedule', 'schedule-item', 1, 'food', 'Stored planned snapshot', '1 bowl', 0.75, 400.0, 20.0, 50.0, 10.0)")
                baseline.execSQL("INSERT INTO nutrition_settings VALUES (1, 1110)")
                baseline.execSQL("INSERT INTO progress_photos VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>("photo", "2026-10-20", "Original caption", 500L, hash, bytes.size.toLong(), 16, 20, galleryUri))
                snapshot(baseline)
            } finally { baseline.close() }

            opened = WorkoutDatabase.open(context, name)
            val database = opened
            val photos = PhotoLibrary(context, database, root)
            val document = BackupStore(database, photos::valid) { 9000 }.export()
            val workout = WorkoutRepository(database)
            val active = workout.active.first()!!
            val nutrition = NutritionRepository(database).overview.first()
            val photoRows = photos.entries.first()
            database.workouts().observeCompletedSessions().first()
            ProgressAnalytics.report(document.workouts, document.nutrition.meals,
                LocalDate.parse("2026-10-26"), 30, ZoneId.of("Asia/Kolkata"))

            assertEquals("active", active.session.id)
            assertEquals("not ready", active.orderedExercises.single().draft.value)
            assertEquals("42.", active.orderedExercises.single().draft.weight)
            assertEquals(1, document.routines.size)
            assertEquals(2, document.workouts.size)
            assertEquals(1110, nutrition.reminderMinute)
            assertEquals(2, nutrition.schedules.size)
            assertEquals(galleryUri, photoRows.single().galleryUri)
            assertEquals(expected, snapshot(database.openHelper.readableDatabase))
            assertArrayEquals(bytes, privateImage.readBytes())
            assertTrue(photos.valid(document.photos.single()))
            assertEquals(3, database.openHelper.readableDatabase.version)

            database.close()
            opened = WorkoutDatabase.open(context, name)
            assertEquals(expected, snapshot(opened.openHelper.readableDatabase))
            assertArrayEquals(bytes, privateImage.readBytes())
        } finally {
            opened?.close()
            context.deleteDatabase(name)
            root.deleteRecursively()
        }
    }
}
