package io.github.nottarun7.fitt.interchange

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32

enum class BackupFileFormat(val extension: String, val mime: String) {
    JSON("json", "application/json"), CSV("csv", "text/csv"), ZIP("zip", "application/zip"),
}

@Serializable
private data class ArchiveManifest(val format: String, val version: Int, val jsonSha256: String, val csvSha256: String)

object BackupFiles {
    fun encode(document: BackupDocument, format: BackupFileFormat): ByteArray {
        val result = when (format) {
            BackupFileFormat.JSON -> BackupJson.encode(document).toByteArray(Charsets.UTF_8)
            BackupFileFormat.CSV -> BackupCsv.encode(document).toByteArray(Charsets.UTF_8)
            BackupFileFormat.ZIP -> {
                if (document.photos.isNotEmpty()) throw BackupException("Use the photo ZIP archive writer to include image files.")
                val json = BackupJson.encode(document).toByteArray(Charsets.UTF_8)
                val csv = BackupCsv.encode(document).toByteArray(Charsets.UTF_8)
                val manifest = BackupJson.json.encodeToString(ArchiveManifest("fitt.backup.archive", 1, sha256(json), sha256(csv))).toByteArray(Charsets.UTF_8)
                ByteArrayOutputStream().also { bytes -> ZipOutputStream(bytes).use { zip ->
                    listOf("manifest.json" to manifest, "backup.json" to json, "backup.csv" to csv).forEach { (name, value) ->
                        zip.putNextEntry(ZipEntry(name).apply { time = 0 }); zip.write(value); zip.closeEntry()
                    }
                } }.toByteArray()
            }
        }
        if (result.size > BackupLimits.FILE_BYTES) throw BackupException("Backup exceeds the 12 MiB file limit.")
        return result
    }

    fun decode(bytes: ByteArray): DecodedBackup {
        if (bytes.size > BackupLimits.FILE_BYTES) throw BackupException("Backup exceeds the 12 MiB file limit.")
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) return decodeZip(bytes)
        val text = utf8(bytes).removePrefix("\uFEFF")
        return if (text.trimStart().startsWith("{")) BackupJson.decode(text) else BackupCsv.decode(text)
    }

    private fun decodeZip(bytes: ByteArray): DecodedBackup {
        val directory = zipDirectory(bytes)
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    // No paths are ever extracted; only these exact flat names are accepted.
                    if (entry.isDirectory || entry.name !in setOf("manifest.json", "backup.json", "backup.csv") || entry.name in entries) {
                        throw BackupException("ZIP contains unexpected, duplicate, or unsafe entries.")
                    }
                    val limit = if (entry.name == "manifest.json") 4096 else BackupLimits.DOCUMENT_BYTES
                    val data = readBounded(zip, limit)
                    total += data.size
                    if (total > BackupLimits.ARCHIVE_BYTES) throw BackupException("ZIP expands beyond the allowed size.")
                    entries[entry.name] = data
                    zip.closeEntry()
                }
            }
        } catch (error: BackupException) { throw error }
        catch (_: Exception) { throw BackupException("ZIP is damaged, encrypted, or incomplete.") }
        if (entries.keys != setOf("manifest.json", "backup.json", "backup.csv")) throw BackupException("ZIP is missing its manifest, JSON, or CSV data.")
        entries.forEach { (name, data) ->
            val (size, crc) = directory.getValue(name)
            if (size != data.size.toLong() || crc != CRC32().apply { update(data) }.value) throw BackupException("ZIP directory or CRC verification failed.")
        }
        val manifestText = utf8(entries.getValue("manifest.json"))
        guardJson(manifestText)
        val manifest = try { BackupJson.json.decodeFromString<ArchiveManifest>(manifestText) }
        catch (_: IllegalArgumentException) { throw BackupException("Invalid ZIP manifest.") }
        if (manifest.format != "fitt.backup.archive" || manifest.version != 1) throw BackupException("Unsupported ZIP manifest version.")
        if (manifest.jsonSha256 != sha256(entries.getValue("backup.json")) || manifest.csvSha256 != sha256(entries.getValue("backup.csv"))) {
            throw BackupException("ZIP checksum verification failed.")
        }
        val json = BackupJson.decode(utf8(entries.getValue("backup.json")))
        if (json.isPlan) throw BackupException("Plan imports use a plain fitt.plan JSON file, not a backup archive.")
        if (json.document.photos.isNotEmpty()) throw BackupException("Photo image files are missing. Use a complete photo ZIP backup.")
        val csv = BackupCsv.decode(utf8(entries.getValue("backup.csv")))
        if (json.document != csv.document) throw BackupException("ZIP JSON and CSV contain different data.")
        return DecodedBackup(json.document, json.notices + "ZIP checksums and matching JSON/CSV data verified.")
    }

    fun readBounded(input: InputStream, limit: Int = BackupLimits.FILE_BYTES): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (result.size().toLong() + read > limit) throw BackupException("File or ZIP entry exceeds the allowed size.")
            result.write(buffer, 0, read)
        }
        return result.toByteArray()
    }

    private fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) { throw BackupException("The file must contain valid UTF-8 text.") }

    private fun zipDirectory(bytes: ByteArray): Map<String, Pair<Long, Long>> {
        fun invalid(): Nothing = throw BackupException("ZIP directory is missing, unsafe, or incomplete.")
        fun u16(at: Int): Int {
            if (at < 0 || at + 2 > bytes.size) invalid()
            return (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
        }
        fun u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)
        val end = (bytes.size - 22 downTo (bytes.size - 65_557).coerceAtLeast(0)).firstOrNull {
            u32(it) == 0x06054b50L && it + 22 + u16(it + 20) == bytes.size
        } ?: invalid()
        if (u16(end + 4) != 0 || u16(end + 6) != 0 || u16(end + 8) != 3 || u16(end + 10) != 3) invalid()
        val start = u32(end + 16)
        val size = u32(end + 12)
        if (start + size != end.toLong() || start > bytes.size) invalid()
        var cursor = start.toInt()
        val result = linkedMapOf<String, Pair<Long, Long>>()
        repeat(3) {
            if (u32(cursor) != 0x02014b50L || (u16(cursor + 8) and 1) != 0 || u16(cursor + 34) != 0) invalid()
            val nameLength = u16(cursor + 28)
            val length = 46 + nameLength + u16(cursor + 30) + u16(cursor + 32)
            if (cursor.toLong() + length > end) invalid()
            val name = utf8(bytes.copyOfRange(cursor + 46, cursor + 46 + nameLength))
            if (name !in setOf("manifest.json", "backup.json", "backup.csv") || name in result) invalid()
            val local = u32(cursor + 42)
            if (local >= start || u32(local.toInt()) != 0x04034b50L) invalid()
            result[name] = u32(cursor + 24) to u32(cursor + 16)
            cursor += length
        }
        if (cursor != end) invalid()
        return result
    }
}
