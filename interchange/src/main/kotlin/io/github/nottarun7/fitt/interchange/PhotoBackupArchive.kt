package io.github.nottarun7.fitt.interchange

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.*
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.*

@Serializable
private data class PhotoManifest(val format: String, val version: Int, val jsonSha256: String,
    val csvSha256: String, val photos: Map<String, String>)

/** Bounded streaming archive. Callers stage images privately and commit metadata only after validation. */
object PhotoBackupArchive {
    private val core = setOf("manifest.json", "backup.json", "backup.csv")
    private val photoName = Regex("photos/[0-9a-f]{64}\\.jpg")
    private fun name(hash: String) = "photos/$hash.jpg"

    fun write(document: BackupDocument, output: OutputStream, image: (BackupPhoto) -> InputStream) {
        val data = document.requireValid().canonical()
        val photos = data.photos.distinctBy { it.sha256 }
        val json = BackupJson.encode(data).toByteArray(Charsets.UTF_8)
        val csv = BackupCsv.encode(data).toByteArray(Charsets.UTF_8)
        val manifest = BackupJson.json.encodeToString(PhotoManifest("fitt.backup.archive", 2, sha256(json), sha256(csv),
            photos.associate { name(it.sha256) to it.sha256 })).toByteArray(Charsets.UTF_8)
        if (json.size.toLong() + csv.size + manifest.size + photos.sumOf { it.byteCount } > PhotoLimits.ARCHIVE_BYTES)
            throw BackupException("ZIP contents exceed 256 MiB. Remove unneeded progress photos before exporting.")
        ZipOutputStream(BoundedOutput(output, PhotoLimits.ARCHIVE_BYTES)).use { zip ->
            listOf("manifest.json" to manifest, "backup.json" to json, "backup.csv" to csv).forEach { (entry, bytes) ->
                zip.putNextEntry(ZipEntry(entry).apply { time = 0 }); zip.write(bytes); zip.closeEntry()
            }
            photos.forEach { photo ->
                zip.putNextEntry(ZipEntry(name(photo.sha256)).apply { time = 0 })
                val checked = image(photo).use { copyChecked(it, zip, photo.byteCount, PhotoLimits.IMAGE_BYTES.toLong(), jpeg = true) }
                if (checked.hash != photo.sha256) throw BackupException("A private photo failed checksum verification. Restore it from a good backup.")
                zip.closeEntry()
            }
        }
    }

    fun read(file: File, stageImage: (BackupPhoto) -> OutputStream): DecodedBackup {
        if (file.length() > PhotoLimits.ARCHIVE_BYTES) throw BackupException("Selected backup exceeds 256 MiB.")
        try {
            envelope(file)
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size > PhotoLimits.COUNT + 3 || entries.map { it.name }.distinct().size != entries.size ||
                    entries.any { it.isDirectory || (it.name !in core && !photoName.matches(it.name)) })
                    throw BackupException("ZIP has unexpected, duplicate, or unsafe entries.")
                val byName = entries.associateBy { it.name }
                if (!byName.keys.containsAll(core)) throw BackupException("ZIP is missing its manifest, JSON, or CSV.")
                fun bytes(name: String, limit: Int): ByteArray {
                    val entry = byName.getValue(name)
                    val out = ByteArrayOutputStream()
                    val checked = zip.getInputStream(entry).use { copyChecked(it, out, entry.size, limit.toLong()) }
                    if (checked.crc != entry.crc) throw BackupException("ZIP CRC verification failed.")
                    return out.toByteArray()
                }
                val manifestText = utf8(bytes("manifest.json", 65536))
                guardJson(manifestText)
                val element = BackupJson.json.parseToJsonElement(manifestText)
                val version = (element as? kotlinx.serialization.json.JsonObject)?.get("version")?.toString()
                if (version == "1") {
                    if (entries.size != 3 || file.length() > BackupLimits.FILE_BYTES) throw BackupException("Invalid legacy ZIP layout or size.")
                    return BackupFiles.decode(file.readBytes())
                }
                val manifest = BackupJson.json.decodeFromString<PhotoManifest>(manifestText)
                if (manifest.format != "fitt.backup.archive" || manifest.version != 2) throw BackupException("Unsupported ZIP manifest version.")
                val jsonBytes = bytes("backup.json", BackupLimits.DOCUMENT_BYTES)
                val csvBytes = bytes("backup.csv", BackupLimits.DOCUMENT_BYTES)
                if (sha256(jsonBytes) != manifest.jsonSha256 || sha256(csvBytes) != manifest.csvSha256) throw BackupException("ZIP document checksum verification failed.")
                val json = BackupJson.decode(utf8(jsonBytes)); val csv = BackupCsv.decode(utf8(csvBytes))
                if (json.isPlan || json.document != csv.document) throw BackupException("ZIP must contain matching complete backup documents.")
                val photos = json.document.photos.distinctBy { it.sha256 }
                val expected = photos.associate { name(it.sha256) to it.sha256 }
                if (manifest.photos != expected || byName.keys != core + expected.keys) throw BackupException("ZIP photo manifest or image entries do not match the backup.")
                val expanded = entries.sumOf { it.size.coerceAtLeast(0) }
                if (expanded > PhotoLimits.ARCHIVE_BYTES) throw BackupException("ZIP expands beyond 256 MiB.")
                photos.forEach { photo ->
                    val entry = byName.getValue(name(photo.sha256))
                    if (entry.size != photo.byteCount) throw BackupException("Photo size does not match its metadata.")
                    val checked = zip.getInputStream(entry).use { input -> stageImage(photo).use { output ->
                        copyChecked(input, output, photo.byteCount, PhotoLimits.IMAGE_BYTES.toLong(), jpeg = true)
                    } }
                    if (checked.hash != photo.sha256 || checked.crc != entry.crc) throw BackupException("Photo checksum or ZIP CRC verification failed.")
                }
                return DecodedBackup(json.document, json.notices + "ZIP documents and ${photos.size} image files verified. Restored photos stay private in Fitt.")
            }
        } catch (invalid: BackupException) { throw invalid }
        catch (_: Exception) { throw BackupException("ZIP is damaged, incomplete, encrypted, or has invalid metadata.") }
    }

    fun spool(input: InputStream, output: OutputStream) { copyChecked(input, output, null, PhotoLimits.ARCHIVE_BYTES) }

    private data class Checked(val hash: String, val crc: Long)
    private fun copyChecked(input: InputStream, output: OutputStream, expected: Long?, limit: Long, jpeg: Boolean = false): Checked {
        val digest = MessageDigest.getInstance("SHA-256"); val crc = CRC32(); val buffer = ByteArray(65536)
        var count = 0L; var first = -1; var second = -1; var penultimate = -1; var last = -1
        while (true) {
            val read = input.read(buffer); if (read < 0) break; if (read == 0) continue
            if (count + read > limit || (expected != null && count + read > expected)) throw BackupException("A ZIP entry or file exceeds its declared limit.")
            if (count == 0L) { first = buffer[0].toInt() and 255; if (read > 1) second = buffer[1].toInt() and 255 }
            else if (count == 1L && second < 0) second = buffer[0].toInt() and 255
            if (read > 1) penultimate = buffer[read - 2].toInt() and 255 else penultimate = last
            last = buffer[read - 1].toInt() and 255
            count += read; digest.update(buffer, 0, read); crc.update(buffer, 0, read); output.write(buffer, 0, read)
        }
        if (expected != null && count != expected) throw BackupException("ZIP entry is incomplete.")
        if (jpeg && (first != 255 || second != 216 || penultimate != 255 || last != 217)) throw BackupException("Progress photos must be complete JPEG images.")
        return Checked(digest.digest().joinToString("") { "%02x".format(it) }, crc.value)
    }

    private class BoundedOutput(private val output: OutputStream, private val limit: Long) : OutputStream() {
        private var count = 0L
        override fun write(value: Int) { if (++count > limit) throw BackupException("ZIP exceeds 256 MiB."); output.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            count += length; if (count > limit) throw BackupException("ZIP exceeds 256 MiB."); output.write(bytes, offset, length)
        }
        override fun flush() = output.flush()
        override fun close() = output.close()
    }
    private fun utf8(bytes: ByteArray) = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()

    /** Require a complete single-disk central directory, including matching flat local entry names. */
    private fun envelope(file: File) = RandomAccessFile(file, "r").use { input ->
        fun invalid(): Nothing = throw BackupException("ZIP directory is missing, incomplete, or unsafe.")
        fun u16(at: Long): Int { if (at < 0 || at + 2 > input.length()) invalid(); input.seek(at); return input.readUnsignedByte() or (input.readUnsignedByte() shl 8) }
        fun u32(at: Long): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)
        val end = (input.length() - 22 downTo (input.length() - 65557).coerceAtLeast(0)).firstOrNull {
            u32(it) == 0x06054b50L && it + 22 + u16(it + 20) == input.length()
        } ?: invalid()
        val count = u16(end + 10)
        if (u16(end + 4) != 0 || u16(end + 6) != 0 || u16(end + 8) != count || count !in 3..PhotoLimits.COUNT + 3) invalid()
        val start = u32(end + 16); val size = u32(end + 12)
        if (start + size != end) invalid()
        var at = start
        repeat(count) {
            if (u32(at) != 0x02014b50L || u16(at + 8) and 1 != 0 || u16(at + 34) != 0) invalid()
            val nameSize = u16(at + 28); val length = 46L + nameSize + u16(at + 30) + u16(at + 32)
            if (at + length > end) invalid()
            val nameBytes = ByteArray(nameSize); input.seek(at + 46); input.readFully(nameBytes)
            val local = u32(at + 42)
            if (local >= start || u32(local) != 0x04034b50L || u16(local + 6) and 1 != 0 || u16(local + 26) != nameSize) invalid()
            val localName = ByteArray(nameSize); input.seek(local + 30); input.readFully(localName)
            if (!nameBytes.contentEquals(localName)) invalid()
            at += length
        }
        if (at != end) invalid()
    }
}
