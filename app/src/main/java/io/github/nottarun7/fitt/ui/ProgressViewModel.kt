package io.github.nottarun7.fitt.ui

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nottarun7.fitt.FittApplication
import io.github.nottarun7.fitt.progress.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ProgressState(val report: ProgressReport? = null, val days: Int = 30, val error: String? = null)
data class PhotoState(val photos: List<PhotoEntity> = emptyList(), val busy: Boolean = false,
    val message: String? = null, val guideSeen: Boolean = false, val galleryCopies: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(val app: FittApplication, private val saved: SavedStateHandle) : ViewModel() {
    private val days = MutableStateFlow(saved.get<Int>("days")?.takeIf { it in setOf(7, 30, 90) } ?: 30)
    private val refresh = MutableStateFlow(0)
    private val preferences = app.getSharedPreferences("progress-device", 0)
    private val _photos = MutableStateFlow(PhotoState(guideSeen = preferences.getBoolean("guide", false), galleryCopies = preferences.getBoolean("copies", false)))
    val photos = _photos.asStateFlow()
    val state = combine(app.database.workouts().observeCompletedSessions(), app.database.nutrition().observeMeals(), days, refresh) { _, _, count, _ -> count }
        .mapLatest { count ->
            val document = app.backups.export()
            ProgressState(ProgressAnalytics.report(document.workouts, document.nutrition.meals, LocalDate.now(), count, ZoneId.systemDefault()), count)
        }.flowOn(Dispatchers.Default).catch { emit(ProgressState(days = days.value, error = "Could not load progress. Reopen this screen to try again.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressState(days = days.value))

    init { viewModelScope.launch { app.photos.entries.collect { rows -> _photos.update { it.copy(photos = rows) } } } }
    fun range(value: Int) { require(value in setOf(7, 30, 90)); saved["days"] = value; days.value = value }
    fun refresh() { refresh.value++ }
    fun guideSeen() { preferences.edit().putBoolean("guide", true).apply(); _photos.update { it.copy(guideSeen = true) } }
    fun copies(enabled: Boolean) { preferences.edit().putBoolean("copies", enabled).apply(); _photos.update { it.copy(galleryCopies = enabled) } }
    fun clearPhotoMessage() { _photos.update { it.copy(message = null) } }
    private fun work(action: suspend () -> String) {
        if (_photos.value.busy) return
        _photos.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { val message = withContext(Dispatchers.IO) { action() }; _photos.update { it.copy(message = message) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { _photos.update { it.copy(message = error.message?.take(500) ?: "Could not complete this photo operation. Please try again.") } }
            finally { _photos.update { it.copy(busy = false) } }
        }
    }
    fun add(uri: Uri, captured: Boolean = false) = work {
        try {
            val added = app.photos.add(uri)
            var message = if (added.duplicate) "This photo is already in your private gallery." else "Photo saved privately. Open it to edit its date and caption."
            if (_photos.value.galleryCopies) {
                message += try { app.photos.galleryCopy(added.photo.id); " Phone-gallery copy saved in Pictures/Fitt Progress." }
                catch (_: Exception) { " Private photo is saved, but the gallery copy failed. Open the photo to retry." }
            }
            message
        } finally { if (captured) clearCapture() }
    }
    fun edit(id: String, date: String, caption: String) = work { app.photos.edit(id, date, caption); "Photo details saved." }
    fun delete(id: String) = work { app.photos.delete(id); "Private photo removed. Original images and phone-gallery copies are untouched." }
    fun copy(id: String) = work { app.photos.galleryCopy(id); "Copy saved in Pictures/Fitt Progress. Open your phone gallery to view the album." }
    fun captureUri(): Uri {
        clearCapture()
        val directory = File(app.cacheDir, "photo_capture").apply { check(isDirectory || mkdirs()) }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        saved["capture"] = file.name
        return FileProvider.getUriForFile(app, "${app.packageName}.capture", file)
    }
    fun captured(success: Boolean) {
        val file = captureFile()
        if (success && file != null && file.isFile) add(FileProvider.getUriForFile(app, "${app.packageName}.capture", file), true)
        else clearCapture()
    }
    fun clearCapture() { captureFile()?.delete(); saved.remove<String>("capture") }
    private fun captureFile(): File? = saved.get<String>("capture")?.takeIf { it.matches(Regex("[0-9a-f-]{36}\\.jpg")) }?.let { File(app.cacheDir, "photo_capture/$it") }
    companion object {
        fun factory(app: FittApplication): ViewModelProvider.Factory = viewModelFactory { initializer { ProgressViewModel(app, createSavedStateHandle()) } }
    }
}
