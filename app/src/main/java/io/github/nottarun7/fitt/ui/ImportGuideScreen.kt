package io.github.nottarun7.fitt.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var guide by remember { mutableStateOf<String?>(null) }
    var example by remember { mutableStateOf<String?>(null) }
    var schema by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val texts = withContext(Dispatchers.IO) { listOf("import-guide.md", "fitt-plan-example.json", "fitt-plan-v1.schema.json").map { name ->
                context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } }
            guide = texts[0]; example = texts[1]; schema = texts[2]
        } catch (_: Exception) { message = "Could not open the bundled guide. Reopen this screen to retry." }
    }
    fun save(uri: android.net.Uri?, text: String?) {
        if (uri == null || text == null || busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() }
                }
                message = "Saved successfully."
            } catch (_: Exception) { message = "Could not save the file. Choose an accessible destination and retry." }
            finally { busy = false }
        }
    }
    val saveGuide = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { save(it, guide) }
    val saveExample = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { save(it, example) }
    val saveSchema = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/schema+json")) { save(it, schema) }
    fun copy(text: String?, label: String) {
        if (text != null) { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text)); message = "Copied $label." }
    }
    BackHandler(busy) { }
    Column {
        PageHeader("Import guide", "PDF / DOC → external LLM → Fitt JSON", onBack = { if (!busy) onBack() })
        LazyColumn(Modifier.weight(1f).testTag("importGuideList"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                FittCard {
                    Text("Bring a workout or diet plan", style = MaterialTheme.typography.titleLarge)
                    Text("Give your document and this prompt to an external LLM. Review its JSON against your source, save it as my-plan.json, then open Train → Backups → Choose backup file.")
                    Text("The standard fitt.plan v1 format creates templates, not completed activity. Imports merge after validation and duplicate review. Fitt never sends your document to an LLM.")
                    Text("Unknown nutrition stays as text instructions. Unknown meal times stay unset. Add verified foods before recording those meals in calorie totals.")
                    Button({ copy(guide, "LLM conversion guide") }, Modifier.fillMaxWidth().testTag("copyImportPrompt"), enabled = guide != null && !busy) { Text("Copy LLM prompt & guide") }
                    OutlinedButton({ copy(example, "example JSON") }, enabled = example != null && !busy) { Text("Copy example JSON") }
                    OutlinedButton({ saveExample.launch("fitt-plan-example.json") }, enabled = example != null && !busy, modifier = Modifier.testTag("savePlanExample")) { Text("Save example JSON") }
                    OutlinedButton({ saveGuide.launch("fitt-import-guide.md") }, enabled = guide != null && !busy) { Text("Save full guide") }
                    OutlinedButton({ saveSchema.launch("fitt-plan-v1.schema.json") }, enabled = schema != null && !busy) { Text("Save JSON Schema") }
                }
            }
            if (message != null) item { Text(message!!, Modifier.testTag("importGuideMessage")) }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (guide != null) item { SelectionContainer { Text(guide!!, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}
