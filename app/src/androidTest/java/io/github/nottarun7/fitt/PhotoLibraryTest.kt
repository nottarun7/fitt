package io.github.nottarun7.fitt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nottarun7.fitt.data.*
import io.github.nottarun7.fitt.interchange.*
import io.github.nottarun7.fitt.progress.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhotoLibraryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "photos-${UUID.randomUUID()}.db"
    private val root = File(context.cacheDir, "photo-test-${UUID.randomUUID()}")
    private lateinit var db: WorkoutDatabase
    private lateinit var photos: PhotoLibrary
    private lateinit var store: BackupStore
    @get:Rule val migration = MigrationTestHelper(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(), WorkoutDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())
    @Before fun before() { root.mkdirs(); open() }
    private fun open() { db = WorkoutDatabase.open(context, name); photos = PhotoLibrary(context, db, File(root, "private")); store = BackupStore(db, photos::valid) { 100 } }
    @After fun after() { db.close(); context.deleteDatabase(name); root.deleteRecursively() }
    private fun image(color: Int = Color.BLUE, width: Int = 80, height: Int = 120): File = File(root, "source-${UUID.randomUUID()}.jpg").also { file ->
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
    }
    private suspend fun add(color: Int = Color.BLUE) = photos.add(Uri.fromFile(image(color))).photo
    private fun archive(document: BackupDocument): File = File(root, "backup-${UUID.randomUUID()}.zip").also { file ->
        file.outputStream().use { PhotoBackupArchive.write(document, it) { photo -> photos.file(photo.sha256).inputStream() } }
    }
    private fun staged(file: File): Pair<BackupDocument, Map<String, File>> {
        val files = linkedMapOf<String, File>()
        val decoded = PhotoBackupArchive.read(file) { photo -> File(root, "staged-${photo.sha256}.jpg").also { files[photo.sha256] = it }.outputStream() }
        return decoded.document to files
    }
    @Test fun importedImageIsOrientedResizedAndStrippedOfOriginalExifAndSurvivesReopen() = runBlocking {
        val source = image(width = 3000, height = 1000)
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, "6")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "12/1,30/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            saveAttributes()
        }
        val photo = photos.add(Uri.fromFile(source)).photo
        assertEquals(682, photo.width); assertEquals(2048, photo.height)
        val exif = ExifInterface(photos.file(photo.sha256).path)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertTrue(exif.getAttribute(ExifInterface.TAG_ORIENTATION) in listOf(null, "0", "1"))
        photos.edit(photo.id, "2026-10-25", "Day one 🏋️")
        val expected = store.export(); db.close(); open()
        assertEquals(expected, store.export()); assertTrue(photos.valid(store.export().photos.single()))
        assertTrue(source.exists())
    }
    @Test fun duplicateImportDoesNotAddRowsAndPrivateDeletionDoesNotTouchOriginal() = runBlocking {
        val source = image(); val one = photos.add(Uri.fromFile(source)); val two = photos.add(Uri.fromFile(source))
        assertTrue(two.duplicate); assertEquals(one.photo.id, two.photo.id); assertEquals(1, store.export().photos.size)
        photos.delete(one.photo.id); assertFalse(photos.file(one.photo.sha256).exists()); assertTrue(source.exists())
    }
    @Test fun fullPhotoZipRestoresVerifiedBlobsAndMetadataAfterDeletionAndReopen() = runBlocking {
        val row = add(); photos.edit(row.id, "2026-10-25", "Before")
        val expected = store.export(); val zip = archive(expected)
        photos.delete(row.id)
        val (document, staged) = staged(zip)
        photos.restore(store, store.preview(document, ImportOptions(mode = ImportMode.RESTORE_EXACTLY)), staged)
        db.close(); open()
        assertEquals(expected, store.export()); assertTrue(photos.valid(expected.photos.single()))
        assertNull(db.photos().all().single().galleryUri)
    }
    @Test fun identicalMetadataCanRepairMissingBlobAndBadDimensionsAreRejected() = runBlocking {
        val row = add(); val expected = store.export(); val zip = archive(expected)
        photos.file(row.sha256).delete()
        val (document, staged) = staged(zip); val plan = store.preview(document)
        assertFalse(plan.hasChanges); assertEquals(1, photos.missing(plan.desired.photos).size)
        photos.restore(store, plan, staged); assertTrue(photos.valid(row.portable()))
        assertFalse(photos.validateStaged(row.portable().copy(width = 999), staged.getValue(row.sha256)))
        assertEquals(expected, store.export())
    }
    @Test fun dataOnlyExactRestorePreservesPhotosWhileFullEmptyRestoreRemovesPrivateFiles() = runBlocking {
        val row = add(); val empty = BackupDocument(100, emptyList(), emptyList())
        photos.restore(store, store.preview(empty.dataOnly(), ImportOptions(mode = ImportMode.RESTORE_EXACTLY)), emptyMap())
        assertTrue(photos.file(row.sha256).exists()); assertEquals(1, store.export().photos.size)
        photos.restore(store, store.preview(empty, ImportOptions(mode = ImportMode.RESTORE_EXACTLY)), emptyMap())
        assertTrue(store.export().photos.isEmpty()); assertFalse(photos.file(row.sha256).exists())
    }
    @Test fun keepBothPhotoEntriesShareBlobUntilTheirLastReferenceIsDeleted() = runBlocking {
        val row = add(); val incoming = store.export().copy(photos = listOf(row.portable().copy(caption = "Imported revision")))
        photos.restore(store, store.preview(incoming, ImportOptions(duplicates = DuplicatePolicy.KEEP_BOTH)), emptyMap())
        val entries = db.photos().all(); assertEquals(2, entries.size)
        photos.delete(row.id); assertTrue(photos.file(row.sha256).exists())
        photos.delete(entries.single { it.id != row.id }.id); assertFalse(photos.file(row.sha256).exists())
    }
    @Test fun failedDatabaseRestoreRollsBackMetadataRetainsOldBlobsAndCollectsNewOrphans() = runBlocking {
        val old = add(); val before = store.export(); val next = add(Color.RED); val nextBytes = photos.file(next.sha256).readBytes()
        photos.delete(next.id)
        val staged = File(root, "new.jpg").apply { writeBytes(nextBytes) }
        val incoming = before.copy(photos = listOf(next.portable()))
        val plan = store.preview(incoming, ImportOptions(mode = ImportMode.RESTORE_EXACTLY))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_photo BEFORE INSERT ON progress_photos BEGIN SELECT RAISE(ABORT, 'fail_photo'); END")
        var failed = false
        try { photos.restore(store, plan, mapOf(next.sha256 to staged)) } catch (_: Exception) { failed = true }
        assertTrue(failed); assertEquals(before, store.export()); assertTrue(photos.file(old.sha256).exists()); assertFalse(photos.file(next.sha256).exists())
    }
    @Test fun missingPhotoCannotBeInsertedAndStalePreviewCannotOverwriteEdits() = runBlocking {
        val row = add(); val before = store.export(); val plan = store.preview(before.copy(photos = emptyList()), ImportOptions(mode = ImportMode.RESTORE_EXACTLY))
        photos.edit(row.id, row.date, "Edited after preview")
        var stale = false; try { photos.restore(store, plan, emptyMap()) } catch (_: StaleBackupPreviewException) { stale = true }; assertTrue(stale)
        val incoming = before.copy(photos = listOf(row.portable().copy(id = "new", sha256 = "a".repeat(64))))
        var failed = false; try { photos.restore(store, store.preview(incoming), emptyMap()) } catch (_: BackupException) { failed = true }; assertTrue(failed)
        assertEquals("Edited after preview", store.export().photos.single().caption)
    }
    @Test fun optionalMediaStoreCopyLivesInNamedAlbumAndIsIndependentOfPrivateDeletion() = runBlocking {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 29)
        val row = add(); val uri = photos.galleryCopy(row.id)
        try {
            assertEquals(uri, photos.galleryCopy(row.id))
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.RELATIVE_PATH), null, null, null)!!.use { it.moveToFirst(); assertEquals("Pictures/Fitt Progress/", it.getString(0)) }
            assertFalse(BackupJson.encode(store.export()).contains("content://"))
            photos.delete(row.id)
            assertNotNull(context.contentResolver.openInputStream(uri)?.use { it.read() })
        } finally { context.contentResolver.delete(uri, null, null) }
    }
    @Test fun explicitMigrationFromTwoPreservesNutritionAndWorkoutRecords() = runBlocking {
        db.close(); context.deleteDatabase(name)
        migration.createDatabase(name, 2).apply {
            execSQL("INSERT INTO routines VALUES ('r', 'Keep routine', 1)")
            execSQL("INSERT INTO foods VALUES ('f', 'Keep food', '1 bowl', 400, 20, 50, 10, 1)")
            close()
        }
        migration.runMigrationsAndValidate(name, 3, true, WorkoutDatabase.MIGRATION_2_3).close()
        open(); val data = store.export()
        assertEquals("Keep routine", data.routines.single().name); assertEquals("Keep food", data.nutrition.foods.single().name); assertTrue(data.photos.isEmpty())
    }
}
