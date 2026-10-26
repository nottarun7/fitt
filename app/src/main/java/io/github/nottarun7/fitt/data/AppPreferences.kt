package io.github.nottarun7.fitt.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Device appearance and consent are deliberately separate from portable health data. */
class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val mode = MutableStateFlow(runCatching { ThemeMode.valueOf(preferences.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM))
    val theme = mode.asStateFlow()
    fun theme(value: ThemeMode) {
        preferences.edit().putString("theme", value.name).apply()
        mode.value = value
    }
}
