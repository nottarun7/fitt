package io.github.nottarun7.fitt.updates

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nottarun7.fitt.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val DAY = 86_400_000L
data class UpdateState(val automatic: Boolean = true, val checking: Boolean = false,
    val available: AvailableUpdate? = null, val showPrompt: Boolean = false, val message: String? = null)

class UpdateViewModel(context: Context, private val source: UpdateSource = GithubUpdateSource(),
    private val signer: () -> String = { installedSigner(context) }, private val installedCode: Long = BuildConfig.VERSION_CODE.toLong(),
    private val sdk: Int = Build.VERSION.SDK_INT, private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private val preferences = context.getSharedPreferences("app-updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UpdateState(automatic = preferences.getBoolean("automatic", true)))
    val state = _state.asStateFlow()
    fun automatic(value: Boolean) {
        preferences.edit().putBoolean("automatic", value).apply()
        _state.update { it.copy(automatic = value, showPrompt = if (value) it.showPrompt else false) }
    }
    fun check(manual: Boolean = false) {
        if (_state.value.checking) return
        val now = clock(); val last = preferences.getLong("attempt", 0)
        if (!manual && (!_state.value.automatic || (last > 0 && now >= last && now - last < DAY))) return
        preferences.edit().putLong("attempt", now).apply()
        _state.update { it.copy(checking = true, message = null) }
        viewModelScope.launch {
            try {
                val manifest = source.fetch()
                val update = UpdatePolicy.available(manifest, installedCode, sdk, signer())
                val message = when {
                    update != null -> "Version ${update.versionName} is available."
                    manifest.versionCode <= installedCode -> "You're up to date."
                    manifest.minSdk > sdk -> "The latest version requires a newer Android version."
                    else -> "This build's signing key does not match a published update. Do not uninstall to update; use a matching signed APK."
                }
                val prompt = update != null && (manual || (preferences.getLong("skipped", 0) != update.versionCode && now >= preferences.getLong("later", 0)))
                _state.update { it.copy(available = update, showPrompt = prompt, message = if (manual || update != null) message else null) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (manual) _state.update { it.copy(message = "Could not check for updates. Your local data is available offline. Try again later.") } }
            finally { _state.update { it.copy(checking = false) } }
        }
    }
    fun later() { preferences.edit().putLong("later", clock() + DAY).apply(); _state.update { it.copy(showPrompt = false) } }
    fun skip() { _state.value.available?.let { preferences.edit().putLong("skipped", it.versionCode).apply() }; _state.update { it.copy(showPrompt = false) } }
    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory { initializer { UpdateViewModel(context.applicationContext) } }
    }
}
