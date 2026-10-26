package io.github.nottarun7.fitt.progress

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import io.github.nottarun7.fitt.data.WorkoutDatabase
import io.github.nottarun7.fitt.data.BackupStore
import io.github.nottarun7.fitt.interchange.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PhotoAdded(val photo: PhotoEntity, val duplicate: Boolean)

class PhotoLibrary(private val context: Context, private val database: WorkoutDatabase,
    private val root: File = File(context.filesDir, "progress_photos")) {
    private val mutex = Mutex()
    val entries = database.photos().observe()
    suspend fun <T> locked(block: suspend () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
    fun file(hash: String): File {
        require(hash.matches(Regex("[0-9a-f]{64}")))
        return File(root, "$hash.jpg")
    }
    fun valid(photo: BackupPhoto): Boolean = runCatching {
        val file = file(photo.sha256)
        file.isFile && file.length() == photo.byteCount && digest(file) == photo.sha256 && dimensions(file) == (photo.width to photo.height)
    }.getOrDefault(false)
    fun validateStaged(photo: BackupPhoto, source: File): Boolean = source.length() == photo.byteCount &&
        digest(source) == photo.sha256 && dimensions(source) == (photo.width to photo.height) && isDecodable(source)

    suspend fun add(uri: Uri): PhotoAdded = locked {
        val source = File.createTempFile("photo-input-", ".tmp", context.cacheDir)
        val prepared = File.createTempFile("photo-ready-", ".jpg", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { copy(input, it, PhotoLimits.INPUT_BYTES.toLong()) } }
                ?: throw IllegalArgumentException("Could not open this photo. Choose an accessible image.")
            val sourceHash = digest(source)
            database.photos().all().firstOrNull { it.sha256 == sourceHash && valid(it.portable()) }?.let { return@locked PhotoAdded(it, true) }
            normalize(source, prepared)
            val hash = digest(prepared)
            database.photos().all().firstOrNull { it.sha256 == hash && valid(it.portable()) }?.let { return@locked PhotoAdded(it, true) }
            require(database.photos().all().size < PhotoLimits.COUNT) { "The gallery supports 200 photos. Remove unneeded entries before adding more." }
            val (width, height) = dimensions(prepared)
            val photo = BackupPhoto(UUID.randomUUID().toString(), LocalDate.now().toString(), "", System.currentTimeMillis(), hash, prepared.length(), width, height)
            check(photo.validatePhoto().isEmpty()) { "This photo could not be prepared within the supported limits." }
            install(prepared, photo)
            val row = photo.entity()
            database.photos().save(row)
            PhotoAdded(row, false)
        } finally { source.delete(); prepared.delete(); cleanup() }
    }

    suspend fun edit(id: String, date: String, caption: String) = locked {
        val old = database.photos().photo(id) ?: throw IllegalArgumentException("This photo was removed.")
        val updated = old.copy(date = date, caption = caption)
        val issues = updated.portable().validatePhoto()
        require(issues.isEmpty()) { issues.joinToString("\n") { it.message } }
        database.photos().save(updated)
    }
    suspend fun delete(id: String) = locked { database.photos().delete(listOf(id)); cleanup() }
    suspend fun collectUnused() = locked { cleanup() }

    /** New blobs become durable before the Room transaction; old referenced blobs survive failures/crashes. */
    suspend fun restore(store: BackupStore, plan: BackupPlan, staged: Map<String, File>): ImportSummary = locked {
        try {
            val selectedHashes = plan.desired.photos.map { it.sha256 }.toSet()
            staged.filterKeys { it in selectedHashes }.forEach { (hash, source) ->
                val photo = plan.desired.photos.first { it.sha256 == hash }
                require(source.length() == photo.byteCount && digest(source) == hash && dimensions(source) == (photo.width to photo.height)) { "A staged photo is invalid." }
                require(isDecodable(source)) { "A staged photo cannot be decoded." }
                install(source, photo)
            }
            store.apply(plan)
        } finally { withContext(NonCancellable) { cleanup() } }
    }
    suspend fun missing(photos: List<BackupPhoto>): List<BackupPhoto> = locked { photos.distinctBy { it.sha256 }.filter { !valid(it) } }
    suspend fun repairCount(photos: List<BackupPhoto>): Int = missing(photos).size

    suspend fun galleryCopy(id: String): Uri = locked {
        val row = database.photos().photo(id) ?: throw IllegalArgumentException("This photo was removed.")
        require(valid(row.portable())) { "The private photo is missing or damaged. Restore it from a ZIP backup." }
        row.galleryUri?.let { saved ->
            if (runCatching { context.contentResolver.openFileDescriptor(Uri.parse(saved), "r")?.use { true } == true }.getOrDefault(false)) return@locked Uri.parse(saved)
        }
        val uri = if (Build.VERSION.SDK_INT >= 29) copyScoped(row) else copyLegacy(row)
        database.photos().save(row.copy(galleryUri = uri.toString()))
        uri
    }
    private fun copyScoped(row: PhotoEntity): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "fitt_progress_${row.sha256}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Fitt Progress/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.DATE_TAKEN, LocalDate.parse(row.date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) { "Could not create a gallery copy." }
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { output -> file(row.sha256).inputStream().use { copy(it, output, PhotoLimits.IMAGE_BYTES.toLong()) } }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); check(resolver.update(uri, values, null, null) == 1)
            return uri
        } catch (error: Exception) { runCatching { resolver.delete(uri, null, null) }; throw error }
    }
    @Suppress("DEPRECATION")
    private suspend fun copyLegacy(row: PhotoEntity): Uri {
        require(context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) { "Allow storage access to create the optional phone-gallery copy. Your private photo is saved." }
        val album = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Fitt Progress")
        check(album.isDirectory || album.mkdirs())
        val target = File(album, "fitt_progress_${row.sha256}.jpg")
        if (!target.exists()) file(row.sha256).inputStream().use { input -> target.outputStream().use { copy(input, it, PhotoLimits.IMAGE_BYTES.toLong()) } }
        return withTimeout(15_000) { suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("image/jpeg")) { _, uri ->
                if (continuation.isActive) {
                    if (uri != null) continuation.resume(uri) else continuation.resumeWithException(IllegalStateException("Copy saved, but the phone gallery could not index it. Try opening the album in your gallery app."))
                }
            }
        } }
    }
    private fun install(source: File, photo: BackupPhoto) {
        check(root.isDirectory || root.mkdirs())
        if (valid(photo)) return
        val temporary = File.createTempFile("photo-", ".part", root)
        try {
            FileOutputStream(temporary).use { output -> source.inputStream().use { copy(it, output, PhotoLimits.IMAGE_BYTES.toLong()) }; output.fd.sync() }
            check(temporary.renameTo(file(photo.sha256))) { "Could not store the private photo." }
        } finally { temporary.delete() }
    }
    private suspend fun cleanup() {
        val used = database.photos().all().map { it.sha256 + ".jpg" }.toSet()
        root.listFiles()?.filter { it.isFile && (it.name.matches(Regex("[0-9a-f]{64}\\.jpg")) || it.name.endsWith(".part")) && it.name !in used }?.forEach { it.delete() }
    }
    private fun normalize(source: File, target: File) {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            require(info.size.width.toLong() * info.size.height <= 100_000_000L) { "This image is too large to import safely." }
            val scale = (PhotoLimits.MAX_EDGE.toDouble() / maxOf(info.size.width, info.size.height)).coerceAtMost(1.0)
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000L) { "Choose a supported photo no larger than 100 megapixels." }
            var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > PhotoLimits.MAX_EDGE) sample *= 2
            val raw = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw IllegalArgumentException("Unsupported image.")
            val orientation = runCatching { ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(1)
            val matrix = Matrix().apply { when (orientation) {
                2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
            } }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { if (it !== raw) raw.recycle() }
        }
        try {
            val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            try {
                Canvas(flattened).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
                target.outputStream().use { check(flattened.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
                require(target.length() in 1..PhotoLimits.IMAGE_BYTES.toLong()) { "Prepared image exceeds 5 MiB." }
            } finally { flattened.recycle() }
        } finally { bitmap.recycle() }
    }
    companion object {
        fun dimensions(file: File): Pair<Int, Int> {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, options)
            return options.outWidth to options.outHeight
        }
        private fun isDecodable(file: File): Boolean = BitmapFactory.decodeFile(file.path)?.let { it.recycle(); true } ?: false
        fun digest(file: File): String = file.inputStream().use { input ->
            val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; if (n > 0) hash.update(buffer, 0, n) }
            hash.digest().joinToString("") { "%02x".format(it) }
        }
        private fun copy(input: InputStream, output: OutputStream, limit: Long) {
            val buffer = ByteArray(65536); var total = 0L
            while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= limit) { "Selected image exceeds the supported size limit." }; output.write(buffer, 0, n) }
        }
    }
}
