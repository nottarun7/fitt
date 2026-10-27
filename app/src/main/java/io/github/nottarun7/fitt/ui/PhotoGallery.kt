package io.github.nottarun7.fitt.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nottarun7.fitt.progress.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun PhotoGallery(model: ProgressViewModel) {
    val state by model.photos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var compare by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var guide by rememberSaveable { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var pendingCopy by rememberSaveable { mutableStateOf<String?>(null) }
    var localMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { pendingCopy?.let(model::copy) ?: model.copies(true) }
        else localMessage = "Storage access was declined. Private photos still work; phone-gallery copies are optional."
        pendingCopy = null
    }
    fun needsPermission() = Build.VERSION.SDK_INT <= 28 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) model.add(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), model::captured)
    val row = state.photos.firstOrNull { it.id == selected }
    LaunchedEffect(state.photos) { compare = ArrayList(compare.filter { id -> state.photos.any { it.id == id } }) }
    BackHandler(selected != null || comparing) { selected = null; comparing = false }
    Column(Modifier.fillMaxSize()) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val message = localMessage ?: state.message
        if (message != null) Text(message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("photoMessage"), style = MaterialTheme.typography.bodySmall)
        if (comparing && compare.size == 2) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Photo comparison", style = MaterialTheme.typography.titleMedium)
                TextButton({ comparing = false }) { Text("Done") }
            }
            Row(Modifier.fillMaxWidth().weight(1f).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                compare.mapNotNull { id -> state.photos.firstOrNull { it.id == id } }.forEach { photo ->
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(photo.date, fontWeight = FontWeight.Bold)
                        PrivatePhoto(model.app.photos.file(photo.sha256), "Progress photo, ${photo.date}. ${photo.caption}", Modifier.fillMaxWidth().height(350.dp), large = true)
                        Text(photo.caption, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (row != null) {
            PhotoDetails(row, model.app.photos.file(row.sha256), state.busy, onBack = { selected = null },
                onSave = { date, caption -> model.edit(row.id, date, caption) }, onDelete = { model.delete(row.id); selected = null },
                onCopy = { if (needsPermission()) { pendingCopy = row.id; permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) } else model.copy(row.id) },
                onOpen = row.galleryUri?.let { uri -> {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        .onFailure { localMessage = "The phone-gallery copy is unavailable. Use Save phone-gallery copy to create it again." }
                } })
        } else {
            LazyColumn(Modifier.fillMaxSize().testTag("photoList"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    FittCard {
                        Text("Your private timeline", style = MaterialTheme.typography.titleLarge)
                        Text("Private on this device. Include photos in your ZIP backups.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ localMessage = null; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.busy, modifier = Modifier.weight(1f).testTag("addPhoto")) { Text("Choose photo") }
                            OutlinedButton({
                                localMessage = null
                                try { camera.launch(model.captureUri()) } catch (_: Exception) { model.clearCapture(); localMessage = "No camera app is available. Choose a photo from your phone instead." }
                            }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Take photo") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Also save new photos to phone gallery", fontWeight = FontWeight.SemiBold)
                                Text("Optional · Pictures/Fitt Progress", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(state.galleryCopies, { enabled -> if (enabled) consent = true else model.copies(false) }, enabled = !state.busy, modifier = Modifier.testTag("galleryCopies"))
                        }
                        Text("${state.photos.size}/200 private photos · shared copies are independent", style = MaterialTheme.typography.bodySmall)
                        TextButton({ guide = true }) { Text("Photo storage & backup guide") }
                        if (compare.isNotEmpty()) {
                            Button({ comparing = true }, enabled = compare.size == 2) { Text("Compare selected (${compare.size}/2)") }
                            TextButton({ compare = arrayListOf() }) { Text("Clear selection") }
                        }
                    }
                }
                if (state.photos.isEmpty()) item { EmptyMessage("Start your progress gallery", "Use similar lighting and framing when you want to compare changes. Dates and captions are editable; photos are never sent to Health Connect.") }
                items(state.photos.chunked(2), key = { it.first().id }) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { photo ->
                            Card(Modifier.weight(1f)) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PrivatePhoto(model.app.photos.file(photo.sha256), "Progress photo, ${photo.date}", Modifier.fillMaxWidth().height(190.dp).clickable { selected = photo.id })
                                    Text(photo.date, fontWeight = FontWeight.SemiBold)
                                    if (photo.caption.isNotBlank()) Text(photo.caption, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                                    TextButton({ selected = photo.id }) { Text("Open photo") }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(photo.id in compare, { checked -> compare = ArrayList(if (!checked) compare - photo.id else (compare + photo.id).takeLast(2)) })
                                        Text("Compare", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (guide || !state.guideSeen) PhotoGuide { guide = false; model.guideSeen() }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Create phone-gallery copies?") },
        text = { Text("Future photos you add will also be copied to Pictures/Fitt Progress. These copies are visible to gallery apps and may follow their cloud-backup settings. Existing photos are not copied automatically. Restored ZIP photos always stay private until you choose to copy them. Turning this off does not delete existing copies.") },
        confirmButton = { TextButton({ consent = false; if (needsPermission()) { pendingCopy = null; permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) } else model.copies(true) }) { Text("Enable copies") } },
        dismissButton = { TextButton({ consent = false }) { Text("Keep private only") } })
}

@Composable
private fun PhotoDetails(row: PhotoEntity, file: File, busy: Boolean, onBack: () -> Unit,
    onSave: (String, String) -> Unit, onDelete: () -> Unit, onCopy: () -> Unit, onOpen: (() -> Unit)?) {
    var date by rememberSaveable(row.id, row.date) { mutableStateOf(row.date) }
    var caption by rememberSaveable(row.id, row.caption) { mutableStateOf(row.caption) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val changed = date != row.date || caption != row.caption
    val validDate = runCatching { LocalDate.parse(date).toString() == date }.getOrDefault(false)
    fun back() { if (changed) discard = true else onBack() }
    BackHandler { if (!busy) back() }
    Column {
        PageHeader("Photo details", onBack = { if (!busy) back() }, action = { TextButton({ onSave(date, caption) }, enabled = !busy && changed && validDate && caption.length <= 16_384, modifier = Modifier.testTag("savePhoto")) { Text("Save") } })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PrivatePhoto(file, "Progress photo, ${row.date}", Modifier.fillMaxWidth().height(340.dp), large = true)
            OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth().testTag("photoDate"), label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, isError = !validDate, enabled = !busy)
            OutlinedTextField(caption, { caption = it }, Modifier.fillMaxWidth().testTag("photoCaption"), label = { Text("Caption / notes") }, minLines = 2, maxLines = 6, enabled = !busy, isError = caption.length > 16_384)
            Text("Private file · ${row.width} × ${row.height} · ${row.byteCount / 1024} KiB", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onCopy, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save phone-gallery copy") }
            if (onOpen != null) TextButton(onOpen, enabled = !busy) { Text("Open phone copy") }
            Text("Gallery copies are visible outside Fitt and may be backed up by your gallery app. Deleting this private entry leaves those copies and the original image untouched.", style = MaterialTheme.typography.bodySmall)
            TextButton({ delete = true }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete private photo") }
        }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete private photo?") }, text = { Text("Remove this entry and its private image when no other entry uses it. Phone-gallery copies and original images remain. A ZIP backup can restore the private photo.") },
        confirmButton = { TextButton({ delete = false; onDelete() }) { Text("Delete private photo") } }, dismissButton = { TextButton({ delete = false }) { Text("Cancel") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard photo detail changes?") }, confirmButton = { TextButton({ discard = false; onBack() }) { Text("Discard") } }, dismissButton = { TextButton({ discard = false }) { Text("Keep editing") } })
}

@Composable
private fun PrivatePhoto(file: File, description: String, modifier: Modifier, large: Boolean = false) {
    val decoded by produceState<Pair<Boolean, ImageBitmap?>>(false to null, file.path, file.lastModified(), large) {
        value = true to withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = if (large) 1 else 4 })?.asImageBitmap() }.getOrNull() }
    }
    val image = decoded.second
    if (image == null) Box(modifier, contentAlignment = Alignment.Center) { Text(if (!decoded.first) "Loading photo…" else "Photo unavailable. Restore its ZIP backup.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
    else Image(image, description, modifier, contentScale = ContentScale.Fit)
}

@Composable
fun PhotoGuide(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your photos, on your device") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("1. Add a photo\nChoose photo opens Android's photo picker. Fitt saves a separate private image; your original stays untouched. New imports are resized to at most 2048 px and re-encoded as JPEG without original EXIF/location metadata.")
            Text("2. Build your timeline\nOpen a photo to set its date and caption. Select two entries to compare. Use similar lighting and framing for useful comparisons.")
            Text("3. Optional phone album\nEnable gallery copies to create Pictures/Fitt Progress on the next photo you add. Copies appear in gallery apps and may follow their cloud-backup settings. Existing entries can be copied individually. These are independent copies: edits or deletion on either side do not sync.")
            Text("4. Back up photos\nChoose ZIP in Backups with Include private photos enabled. JSON/CSV exclude photos and preserve existing private photos on restore. Restored photos stay private; copy them to the phone gallery only if you choose.")
            Text("Private app storage is not a locked album: someone using your unlocked phone can open Fitt. Uninstalling or clearing Fitt removes private photos, so keep a ZIP backup somewhere private. Shared gallery copies remain. Fitt does not upload photos and never shares them with Health Connect.")
            Text("Limits: 200 entries, 20 MiB / 100 MP source image, 5 MiB per prepared JPEG, 256 MiB per ZIP. Backups are not password-protected.")
        }
    }, confirmButton = { TextButton(onDismiss, Modifier.testTag("photoGuideDone")) { Text("Got it") } })
}
