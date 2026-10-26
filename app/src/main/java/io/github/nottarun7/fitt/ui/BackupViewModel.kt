package io.github.nottarun7.fitt.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nottarun7.fitt.data.BackupStore
import io.github.nottarun7.fitt.data.StaleBackupPreviewException
import io.github.nottarun7.fitt.interchange.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.nottarun7.fitt.progress.PhotoLibrary
import java.io.File
import java.util.UUID

data class BackupUiState(
    val busy: Boolean = false,
    val operation: String = "",
    val sourceName: String? = null,
    val decoded: DecodedBackup? = null,
    val plan: BackupPlan? = null,
    val options: ImportOptions = ImportOptions(),
    val issues: List<ValidationIssue> = emptyList(),
    val message: String? = null,
    val result: ImportSummary? = null,
    val photoRepairs: Int = 0,
    val includePhotos: Boolean = true,
    val photoCount: Int = 0,
)

class BackupViewModel(private val store: BackupStore, private val resolver: ContentResolver,
    private val photos: PhotoLibrary, private val cache: File) : ViewModel() {
    private data class ReadPreview(val name: String, val decoded: DecodedBackup, val plan: BackupPlan, val repairs: Int)
    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()
    private var stage: File? = null
    private var stagedImages = emptyMap<String, File>()
    init { viewModelScope.launch { photos.entries.collect { rows -> _state.update { it.copy(photoCount = rows.size) } } } }
    fun includePhotos(value: Boolean) { if (!_state.value.busy) _state.update { it.copy(includePhotos = value) } }
    private fun clearStage() { stage?.deleteRecursively(); stage = null; stagedImages = emptyMap() }
    override fun onCleared() { clearStage() }
    private suspend fun repairs(decoded: DecodedBackup, preview: BackupPlan): Int {
        val incoming = decoded.document.photos.map { it.sha256 }.toSet()
        val selected = preview.desired.photos.filter { it.sha256 in incoming }
        val missing = photos.missing(selected)
        if (missing.any { it.sha256 !in stagedImages }) throw BackupException("Photo image files are missing. Choose the original ZIP with photos, not an extracted JSON/CSV file.")
        return missing.size
    }

    private fun task(operation: String, reset: Boolean = false, work: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { (if (reset) BackupUiState(photoCount = it.photoCount, includePhotos = it.includePhotos) else it).copy(busy = true, operation = operation, issues = emptyList(), message = null, result = null) }
        viewModelScope.launch {
            try { work() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (invalid: BackupException) { _state.update { it.copy(issues = invalid.issues) } }
            catch (_: SecurityException) { _state.update { it.copy(issues = listOf(ValidationIssue("file", "Access was lost. Select the file again."))) } }
            catch (_: Exception) { _state.update { it.copy(issues = listOf(ValidationIssue("file", "Could not complete this operation. Try again with an accessible Fitt file."))) } }
            finally { _state.update { it.copy(busy = false, operation = "") } }
        }
    }

    fun read(uri: Uri) = task("Reading and validating file…", reset = true) {
        val (name, decoded, preview, repairCount) = withContext(Dispatchers.IO) {
            clearStage()
            val name = runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.filterNot { it.isISOControl() }?.take(200) ?: "Selected backup"
            val directory = File(cache, "fitt-import-${UUID.randomUUID()}").apply { check(mkdirs()) }
            try {
                val source = File(directory, "source")
                resolver.openInputStream(uri)?.use { input -> source.outputStream().use { PhotoBackupArchive.spool(input, it) } }
                    ?: throw BackupException("Could not open the selected file.")
                val isZip = source.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
                val images = linkedMapOf<String, File>()
                val decoded = if (isZip) PhotoBackupArchive.read(source) { photo ->
                    File(directory, "${photo.sha256}.jpg").also { images[photo.sha256] = it }.outputStream()
                } else {
                    if (source.length() > BackupLimits.FILE_BYTES) throw BackupException("JSON/CSV files are limited to 12 MiB.")
                    BackupFiles.decode(source.readBytes())
                }
                decoded.document.photos.distinctBy { it.sha256 }.forEach { photo -> images[photo.sha256]?.let {
                    if (!photos.validateStaged(photo, it)) throw BackupException("A photo cannot be decoded or does not match its dimensions.")
                } }
                source.delete(); stage = directory; stagedImages = images
                val preview = store.preview(decoded.document, planOnly = decoded.isPlan)
                val repairs = repairs(decoded, preview)
                ReadPreview(name, decoded, preview, repairs)
            } catch (error: Exception) { directory.deleteRecursively(); stage = null; stagedImages = emptyMap(); throw error }
        }
        _state.update { it.copy(sourceName = name, decoded = decoded, plan = preview, photoRepairs = repairCount) }
    }

    fun export(uri: Uri, format: BackupFileFormat, includePhotos: Boolean = _state.value.includePhotos) = task("Preparing and saving ${format.name}…") {
        withContext(Dispatchers.IO) {
            if (format == BackupFileFormat.ZIP) {
                val temporary = File.createTempFile("fitt-backup-", ".zip", cache)
                try {
                    photos.locked {
                        val snapshot = store.export().let { if (includePhotos) it else it.dataOnly() }
                        temporary.outputStream().use { output -> PhotoBackupArchive.write(snapshot, output) {
                            val file = photos.file(it.sha256)
                            if (!file.isFile) throw BackupException("A private photo is missing. Restore it from a good ZIP, or uncheck Include private photos to export data only.")
                            file.inputStream()
                        } }
                    }
                    resolver.openOutputStream(uri, "wt")?.use { output -> temporary.inputStream().use { PhotoBackupArchive.spool(it, output) }; output.flush() }
                        ?: throw BackupException("Could not open the destination for writing.")
                } finally { temporary.delete() }
            } else {
                val bytes = BackupFiles.encode(store.export().dataOnly(), format)
                resolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: throw BackupException("Could not open the destination for writing.")
            }
        }
        _state.update { it.copy(message = if (format == BackupFileFormat.ZIP && includePhotos) "ZIP export saved with progress photos."
            else "${format.name} data export saved. Progress photos are excluded and will be preserved during restore.") }
    }

    fun options(options: ImportOptions) {
        val decoded = _state.value.decoded ?: return
        val safeOptions = if (decoded.isPlan) options.copy(mode = ImportMode.MERGE) else options
        task("Updating import preview…") {
            _state.update { it.copy(options = safeOptions, plan = null) }
            val preview = withContext(Dispatchers.IO) { store.preview(decoded.document, safeOptions, planOnly = decoded.isPlan) }
            val repairCount = withContext(Dispatchers.IO) { repairs(decoded, preview) }
            _state.update { it.copy(plan = preview, photoRepairs = repairCount) }
        }
    }

    fun applyImport() {
        val plan = _state.value.plan ?: return
        val decoded = _state.value.decoded ?: return
        task("Importing records…") {
            try {
                val summary = withContext(Dispatchers.IO) { photos.restore(store, plan, stagedImages) }
                clearStage()
                _state.update { it.copy(decoded = null, plan = null, result = summary, photoRepairs = 0, message = "Import completed. Restored photos are private; gallery copies are never created automatically.") }
            } catch (_: StaleBackupPreviewException) {
                val preview = withContext(Dispatchers.IO) { store.preview(decoded.document, plan.options, planOnly = decoded.isPlan) }
                val repairCount = withContext(Dispatchers.IO) { repairs(decoded, preview) }
                _state.update { it.copy(plan = preview, photoRepairs = repairCount, message = "Your data changed while this preview was open. Review the updated counts and confirm again.") }
            }
        }
    }

    companion object {
        fun factory(store: BackupStore, resolver: ContentResolver, photos: PhotoLibrary, cache: File): ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(store, resolver, photos, cache) }
        }
    }
}
