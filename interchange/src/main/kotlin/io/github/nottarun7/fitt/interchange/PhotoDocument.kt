package io.github.nottarun7.fitt.interchange

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class BackupPhoto(val id: String, val date: String, val caption: String, val addedAt: Long,
    val sha256: String, val byteCount: Long, val width: Int, val height: Int)

object PhotoLimits {
    const val COUNT = 200
    const val IMAGE_BYTES = 5 * 1024 * 1024
    const val INPUT_BYTES = 20 * 1024 * 1024
    const val MAX_EDGE = 2048
    const val ARCHIVE_BYTES = 256L * 1024 * 1024
}

fun BackupPhoto.validatePhoto(): List<ValidationIssue> = buildList {
    fun check(ok: Boolean, field: String, message: String) { if (!ok) add(ValidationIssue("photos[$id].$field", message)) }
    check(id.isNotBlank() && id.length <= 200 && id.none { it.isISOControl() || it.isSurrogate() }, "id", "Invalid photo ID.")
    check(runCatching { LocalDate.parse(date).toString() == date }.getOrDefault(false), "date", "Use an ISO date (YYYY-MM-DD).")
    check(caption.length <= BackupLimits.TEXT_LENGTH && '\u0000' !in caption && validPhotoText(caption), "caption", "Invalid or oversized caption.")
    check(addedAt >= 0, "addedAt", "Must not be negative.")
    check(sha256.matches(Regex("[0-9a-f]{64}")), "sha256", "Expected a lowercase SHA-256 hash.")
    check(byteCount in 1..PhotoLimits.IMAGE_BYTES.toLong(), "byteCount", "Photo exceeds the 5 MiB limit.")
    check(width in 1..PhotoLimits.MAX_EDGE && height in 1..PhotoLimits.MAX_EDGE, "dimensions", "Dimensions must be positive and no more than 2048 px per edge.")
}

private fun validPhotoText(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val c = text[i++]
        if (c.isLowSurrogate()) return false
        if (c.isHighSurrogate() && (i >= text.length || !text[i++].isLowSurrogate())) return false
    }
    return true
}

fun BackupDocument.dataOnly(): BackupDocument = copy(photos = emptyList(), photoScope = false)
