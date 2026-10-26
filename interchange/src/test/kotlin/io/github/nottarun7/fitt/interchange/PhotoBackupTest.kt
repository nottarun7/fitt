package io.github.nottarun7.fitt.interchange

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.*

class PhotoBackupTest {
    @get:Rule val temporary = TemporaryFolder()
    // The portable archive checks JPEG framing; Android additionally decodes the complete image.
    private val bytes = byteArrayOf(255.toByte(), 216.toByte(), 1, 2, 3, 255.toByte(), 217.toByte())
    private val photo = BackupPhoto("p", "2026-10-26", "=private\nशक्ति 🏋️", 1, sha256(bytes), bytes.size.toLong(), 10, 20)
    private fun document() = backupFixture().copy(photos = listOf(photo))
    private fun archive(data: BackupDocument = document()): java.io.File = temporary.newFile().also { file ->
        file.outputStream().use { PhotoBackupArchive.write(data, it) { bytes.inputStream() } }
    }
    private fun reject(work: () -> Unit) { try { work(); fail("Expected rejection") } catch (_: BackupException) {} }
    private fun entries(file: java.io.File): LinkedHashMap<String, ByteArray> = ZipFile(file).use { zip ->
        zip.entries().asSequence().associateTo(linkedMapOf()) { it.name to zip.getInputStream(it).use { stream -> stream.readBytes() } }
    }
    private fun zip(entries: Map<String, ByteArray>): java.io.File = temporary.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { output -> entries.forEach { (name, data) -> output.putNextEntry(ZipEntry(name)); output.write(data); output.closeEntry() } }
    }
    @Test fun `streamed ZIP roundtrips data and image without base64 and deduplicates blobs`() {
        val expected = document().copy(photos = listOf(photo, photo.copy(id = "copy", date = "2026-10-25")))
        val file = archive(expected); val images = mutableListOf<ByteArrayOutputStream>()
        val decoded = PhotoBackupArchive.read(file) { ByteArrayOutputStream().also(images::add) }
        assertEquals(expected.canonical(), decoded.document); assertEquals(1, images.size)
        assertArrayEquals(bytes, images.single().toByteArray()); assertEquals(4, entries(file).size)
        assertFalse(String(entries(file).getValue("backup.json")).contains("base64"))
    }
    @Test fun `JSON CSV metadata roundtrip and formula escaping`() {
        assertEquals(document().canonical(), BackupJson.decode(BackupJson.encode(document())).document)
        assertEquals(document().canonical(), BackupCsv.decode(BackupCsv.encode(document())).document)
        assertTrue(BackupCsv.encode(document()).contains("'=private"))
        reject { BackupFiles.encode(document(), BackupFileFormat.ZIP) }
    }
    @Test fun `ZIP detects modified image even when its CRC was recalculated`() {
        val items = entries(archive()); items["photos/${photo.sha256}.jpg"] = bytes.copyOf().apply { this[2] = 9 }
        reject { PhotoBackupArchive.read(zip(items)) { ByteArrayOutputStream() } }
    }
    @Test fun `ZIP rejects missing extra unsafe and mismatched assets`() {
        val original = entries(archive())
        reject { PhotoBackupArchive.read(zip(original.filterKeys { !it.startsWith("photos/") })) { ByteArrayOutputStream() } }
        reject { PhotoBackupArchive.read(zip(original + ("../secret.jpg" to bytes))) { ByteArrayOutputStream() } }
        reject { PhotoBackupArchive.read(zip(original + ("photos/${"a".repeat(64)}.jpg" to bytes))) { ByteArrayOutputStream() } }
        reject { PhotoBackupArchive.read(zip(original + ("backup.json" to BackupJson.encode(document().copy(photos = listOf(photo.copy(caption = "changed")))) .toByteArray()))) { ByteArrayOutputStream() } }
        val changedCsv = BackupCsv.encode(document().copy(photos = listOf(photo.copy(caption = "different CSV")))).toByteArray()
        val changedManifest = String(original.getValue("manifest.json")).replace(sha256(original.getValue("backup.csv")), sha256(changedCsv)).toByteArray()
        reject { PhotoBackupArchive.read(zip(original + mapOf("backup.csv" to changedCsv, "manifest.json" to changedManifest))) { ByteArrayOutputStream() } }
    }
    @Test fun `ZIP rejects truncated directory local central name mismatch and duplicate entries`() {
        val file = archive(); val data = file.readBytes()
        file.writeBytes(data.copyOf(data.size - 22)); reject { PhotoBackupArchive.read(file) { ByteArrayOutputStream() } }
        file.writeBytes(data.copyOf().apply { this[30] = 'x'.code.toByte() }); reject { PhotoBackupArchive.read(file) { ByteArrayOutputStream() } }
        // Change both occurrences of backup.csv to backup.json with an equal-length name.
        val replacement = "backup.json".toByteArray()
        // ZIP paths differ in length, so construct a same-length duplicate of manifest via raw name replacement instead.
        val original = zip(linkedMapOf("backup.json" to byteArrayOf(1), "backup.csvx" to byteArrayOf(2), "manifest.json" to byteArrayOf(3))).readBytes()
        val from = "backup.csvx".toByteArray()
        for (i in 0..original.size - from.size) if (original.copyOfRange(i, i + from.size).contentEquals(from)) replacement.copyInto(original, i)
        file.writeBytes(original); reject { PhotoBackupArchive.read(file) { ByteArrayOutputStream() } }
    }
    @Test fun `writer refuses corrupt non JPEG or missing length content`() {
        reject { archive(document().copy(photos = listOf(photo.copy(sha256 = "0".repeat(64))))) }
        reject { archive(document().copy(photos = listOf(photo.copy(byteCount = 100)))) }
        val bad = "plain text".toByteArray()
        reject { PhotoBackupArchive.write(document().copy(photos = listOf(photo.copy(sha256 = sha256(bad), byteCount = bad.size.toLong()))), ByteArrayOutputStream()) { bad.inputStream() } }
    }
    @Test fun `v1 manifest archives and fixed v2 v3 archives still read`() {
        val file = temporary.newFile(); file.writeBytes(BackupFiles.encode(backupFixture(), BackupFileFormat.ZIP))
        assertEquals(backupFixture().canonical(), PhotoBackupArchive.read(file) { error("No photo expected") }.document)
        for (name in listOf("backup-v2.zip", "backup-v3.zip")) {
            file.writeBytes(checkNotNull(javaClass.getResource("/$name")).readBytes())
            val decoded = PhotoBackupArchive.read(file) { error("No photo expected") }
            assertFalse(decoded.document.photoScope); assertTrue(decoded.document.photos.isEmpty())
        }
    }
    @Test fun `data only exact restore preserves photos full exact replaces them`() {
        val before = document()
        val empty = BackupDocument(1, emptyList(), emptyList())
        val options = ImportOptions(mode = ImportMode.RESTORE_EXACTLY)
        assertEquals(before.photos, BackupPlanner.plan(before, empty.dataOnly(), options).desired.photos)
        val full = BackupPlanner.plan(before, empty, options)
        assertTrue(full.desired.photos.isEmpty()); assertEquals(1, full.summary.photos.removed)
    }
    @Test fun `photo ID duplicate policies treat date and caption as a whole record`() {
        val before = document(); val changed = before.copy(photos = listOf(photo.copy(caption = "after", date = "2026-10-25")))
        assertEquals(before.photos, BackupPlanner.plan(before, changed).desired.photos)
        assertEquals(changed.photos, BackupPlanner.plan(before, changed, ImportOptions(duplicates = DuplicatePolicy.USE_IMPORTED)).desired.photos)
        val both = BackupPlanner.plan(before, changed, ImportOptions(duplicates = DuplicatePolicy.KEEP_BOTH))
        assertEquals(2, both.desired.photos.size); assertEquals(1, both.summary.photos.added)
        assertEquals(1, both.desired.photos.map { it.sha256 }.distinct().size)
    }
    @Test fun `v4 JSON upgrades as data only and v5 requires explicit photo scope`() {
        val data = document().dataOnly(); val json = BackupJson.encode(data)
        reject { BackupJson.decode(json.replace("\"photoScope\": false", "\"unexpected\":false")) }
        val legacy = BackupJson.json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
        val fields = legacy.toMutableMap().apply { remove("photos"); remove("photoScope"); put("schemaVersion", kotlinx.serialization.json.JsonPrimitive(4)) }
        val decoded = BackupJson.decode(kotlinx.serialization.json.JsonObject(fields).toString())
        assertFalse(decoded.document.photoScope); assertEquals(data.routines, decoded.document.routines)
        val csv = BackupCsv.encode(BackupDocument(1, emptyList(), emptyList())).lineSequence().filter { it.isNotBlank() }.mapIndexed { index, line ->
            val cells = line.split(',').take(37).toMutableList()
            if (index == 1) cells[21] = "4"
            cells.joinToString(",")
        }.joinToString("\r\n")
        val oldCsv = BackupCsv.decode(csv).document
        assertFalse(oldCsv.photoScope); assertTrue(oldCsv.photos.isEmpty())
    }
    @Test fun `photo metadata rejects invalid dimensions hashes dates and conflicting descriptors`() {
        listOf(photo.copy(date = "2026-02-30"), photo.copy(width = 2049), photo.copy(sha256 = "../file"), photo.copy(byteCount = 0), photo.copy(byteCount = PhotoLimits.IMAGE_BYTES + 1L), photo.copy(caption = "\uD800")).forEach {
            reject { BackupJson.encode(document().copy(photos = listOf(it))) }
        }
        reject { BackupJson.encode(document().copy(photoScope = false)) }
        reject { BackupJson.encode(document().copy(photos = listOf(photo, photo.copy(id = "other", width = 100)))) }
        reject { BackupJson.encode(document().copy(photos = (0..200).map { photo.copy(id = "$it") })) }
    }
}
